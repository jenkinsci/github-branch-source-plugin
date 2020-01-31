package org.jenkinsci.plugins.github_branch_source;

import com.github.tomakehurst.wiremock.client.ScenarioMappingBuilder;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.common.SingleRootFileSource;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.Response;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import hudson.util.LogTaskListener;
import hudson.util.RingBufferLogHandler;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.Before;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.github.GitHub;

import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.*;

public class ApiRateLimitCheckerTest {
    /**
     * All tests in this class only use Jenkins for the extensions
     */
    @ClassRule
    public static JenkinsRule r = new JenkinsRule();

    private static WireMockRuleFactory factory = new WireMockRuleFactory();

    private RingBufferLogHandler handler;
    private LogTaskListener listener;

    private final Random entropy = new Random(1000);

    private final Date soon = Date.from(LocalDateTime.now().plusMinutes(60).atZone(ZoneId.systemDefault()).toInstant());

    private GitHub github;

    private Stream<String> getOutputLines() {
        return handler.getView().stream().map(LogRecord::getMessage);
    }

    private long countOfOutputLines(Predicate<String> predicate) {
        return getOutputLines().filter(predicate).count();
    }

    private long countOfOutputLinesContaining(String substring) {
        return countOfOutputLines(m -> m.contains(substring));
    }

    private class RateLimit {
        final int remaining;
        final int limit;
        final Date reset;

        RateLimit(int limit, int remaining, Date reset) {
            this.limit = limit;
            this.remaining = remaining;
            this.reset = reset;
        }
    }

    @Rule
    public WireMockRule githubRaw = factory.getRule(WireMockConfiguration.options()
            .dynamicPort()
            .usingFilesUnderClasspath("raw")
    );
    @Rule
    public WireMockRule githubApi = factory.getRule(WireMockConfiguration.options()
            .dynamicPort()
            .usingFilesUnderClasspath("api")
            .extensions(
                    new ResponseTransformer() {
                        @Override
                        public Response transform(Request request, Response response, FileSource files,
                                                  Parameters parameters) {
                            if ("application/json"
                                    .equals(response.getHeaders().getContentTypeHeader().mimeTypePart())) {
                                return Response.Builder.like(response)
                                        .but()
                                        .body(response.getBodyAsString()
                                                .replace("https://api.github.com/",
                                                        "http://localhost:" + githubApi.port() + "/")
                                                .replace("https://raw.githubusercontent.com/",
                                                        "http://localhost:" + githubRaw.port() + "/")
                                        )
                                        .build();
                            }
                            return response;
                        }

                        @Override
                        public String getName() {
                            return "url-rewrite";
                        }

                    })
    );

    @Before
    public void setUp() throws Exception {
        new File("src/test/resources/api/mappings").mkdirs();
        new File("src/test/resources/api/__files").mkdirs();
        new File("src/test/resources/raw/mappings").mkdirs();
        new File("src/test/resources/raw/__files").mkdirs();
        githubApi.enableRecordMappings(new SingleRootFileSource("src/test/resources/api/mappings"),
                new SingleRootFileSource("src/test/resources/api/__files"));
        githubRaw.enableRecordMappings(new SingleRootFileSource("src/test/resources/raw/mappings"),
                new SingleRootFileSource("src/test/resources/raw/__files"));


        githubApi.stubFor(
                get(urlMatching(".*")).atPriority(10).willReturn(aResponse().proxiedFrom("https://api.github.com/")));
        githubRaw.stubFor(get(urlMatching(".*")).atPriority(10)
                .willReturn(aResponse().proxiedFrom("https://raw.githubusercontent.com/")));

        github = Connector.connect("http://localhost:" + githubApi.port(), null);

        resetAllScenarios();

        handler = new RingBufferLogHandler(1000);
        final Logger logger = Logger.getLogger(getClass().getName());
        logger.addHandler(handler);
        listener = new LogTaskListener(logger, Level.INFO);

        // Set the random to a known state for testing
        ApiRateLimitChecker.setEntropy(entropy);

        // Default the expiration window to a small but measurable time for testing
        ApiRateLimitChecker.setExpirationWaitMillis(20);

        // Default the notification interval to a small but measurable time for testing
        ApiRateLimitChecker. setNotificationWaitMillis(60);
    }

    private void setupStubs(List<RateLimit> scenarios) {
        String scenarioName = UUID.randomUUID().toString();
        for (int i = 0; i < scenarios.size(); i++) {
            String state = (i == 0) ? Scenario.STARTED : Integer.toString(i);
            String nextState = Integer.toString(i + 1);
            RateLimit scenarioResponse = scenarios.get(i);

            String limit = Integer.toString(scenarioResponse.limit);
            String remaining = Integer.toString(scenarioResponse.remaining);
            String reset = Long.toString(scenarioResponse.reset.toInstant().getEpochSecond());
            String body = "{" +
                String.format(" \"rate\": { \"limit\": %s, \"remaining\": %s, \"reset\": %s },", limit, remaining, reset) +
                " \"resources\": {" +
                String.format(" \"core\": { \"limit\": %s, \"remaining\": %s, \"reset\": %s },", limit, remaining, reset) +
                String.format(" \"search\": { \"limit\": %s, \"remaining\": %s, \"reset\": %s },", limit, remaining, reset) +
                String.format(" \"graphql\": { \"limit\": %s, \"remaining\": %s, \"reset\": %s },", limit, remaining, reset) +
                String.format(" \"integration_manifest\": { \"limit\": %s, \"remaining\": %s, \"reset\": %s }", limit, remaining, reset) +
                " } }";
            ScenarioMappingBuilder scenario = get(urlEqualTo("/rate_limit"))
                    .inScenario(scenarioName)
                    .whenScenarioStateIs(state)
                    .willReturn(aResponse()
                            .withHeader("Content-Type", "application/json; charset=utf-8")
                            .withHeader("X-RateLimit-Limit", limit)
                            .withHeader("X-RateLimit-Remaining", remaining)
                            .withHeader("X-RateLimit-Reset", reset)
                            .withBody(body)
                    );
            if (i != scenarios.size() - 1) {
                scenario = scenario.willSetStateTo(nextState);
            }
            githubApi.stubFor(scenario);

        }
    }

    /**
     * Verify that the throttle does not happen in OnOver throttle
     * when none of the quota has been used
     *
     * @author Julian V. Modesto
     */
    @Test
    public void ThrottleOnOverTestWithQuota() throws Exception {
        // set up scenarios
        List<RateLimit> scenarios = new ArrayList<>();
        int limit = 5000;
        scenarios.add(new RateLimit(limit, limit, soon));
        setupStubs(scenarios);

        // Given a full rate limit quota, then we expect no throttling
        for (int i = 0; i < 100; i++) {
            ApiRateLimitChecker.ThrottleOnOver.checkApiRateLimit(listener, github);
        }

        assertEquals(0, countOfOutputLinesContaining("Sleeping"));
    }

    /**
     * Verify when the throttle is not happening in "OnNormalize" throttle
     * when none of the quota has been used
     *
     * @author Julian V. Modesto
     */
    @Test
    public void ThrottleOnNormalizeTestWithQuota() throws Exception {
        // set up scenarios
        List<RateLimit> scenarios = new ArrayList<>();
        int limit = 5000;
        scenarios.add(new RateLimit(limit, limit, soon));
        setupStubs(scenarios);

        // Given a full rate limit quota, then we expect no throttling
        for (int i = 0; i < 100; i++) {
            ApiRateLimitChecker.ThrottleForNormalize.checkApiRateLimit(listener, github);
        }

        assertEquals(0, countOfOutputLinesContaining("Sleeping"));
    }

    /**
     * Verify exactly when the throttle is occurring in "OnOver"
     *
     * @author Julian V. Modesto
     */
    @Test
    public void ThrottleOnOverTest() throws Exception {
        // set up scenarios
        List<RateLimit> scenarios = new ArrayList<>();
        // set remaining quota to over buffer to trigger throttle
        int limit = 5000;
        int buffer = ApiRateLimitChecker.calculateBuffer(limit);
        int expectedNumThrottles = 10;

        //This is going to not throttle for 10 values and then throttle the next 20
        for (int i = -10; i <= expectedNumThrottles; i++) {
            scenarios.add(new RateLimit(limit, buffer - i, soon));
        }

        // finally, stop throttling by restoring quota
        scenarios.add(new RateLimit(limit, limit, soon));
        setupStubs(scenarios);

        // check rate limit to hit the first 11 scenarios because the throttle (add more here)
        // does not happen until under buffer
        for (int i = 0; i < 11; i++) {
            ApiRateLimitChecker.ThrottleOnOver.checkApiRateLimit(listener, github);
        }

        //should be no output
        assertEquals(0, countOfOutputLines(m -> m.matches("[sS]leeping")));

        // check rate limit to hit the next 10 scenarios
        ApiRateLimitChecker.ThrottleOnOver.checkApiRateLimit(listener, github);

        //output for all the throttled scenarios. Sleeps normally on the first and then the `notify` hits the next 9
        assertEquals(1, countOfOutputLinesContaining("Sleeping for"));
        assertEquals(expectedNumThrottles-1, countOfOutputLinesContaining("Still sleeping"));
        assertEquals(1, countOfOutputLinesContaining("refreshed"));

        //Make sure no new output
        ApiRateLimitChecker.ThrottleOnOver.checkApiRateLimit(listener, github);
        assertEquals(1, countOfOutputLinesContaining("Sleeping for"));
        assertEquals(expectedNumThrottles-1, countOfOutputLinesContaining("Still sleeping"));
        assertEquals(1, countOfOutputLinesContaining("refreshed"));
    }

    /**
     * Verify the bounds of the throttle for "Normalize"
     *
     * @author Julian V. Modesto
     */
    @Test
    public void ThrottleForNormalizeTestWithinIdeal() throws Exception {
        List<RateLimit> scenarios = new ArrayList<>();
        int limit = 5000;
        int buffer = ApiRateLimitChecker.calculateBuffer(limit);

        // Approximate the ideal here
        int approximateIdeal = 4000;

        // Check that if we're above within our ideal, then we don't throttle
        scenarios.add(new RateLimit(limit, approximateIdeal + buffer - 100, soon));

        // Check that we are under our ideal so we should throttle
        scenarios.add(new RateLimit(limit, approximateIdeal - 100, soon));

        // Check that we are under our ideal so we should throttle again
        scenarios.add(new RateLimit(limit, approximateIdeal - 100, soon));

        // Check that we are further under our ideal so we should throttle again
        scenarios.add(new RateLimit(limit, approximateIdeal - 101, soon));

        // Check that we can back to our original throttle
        scenarios.add(new RateLimit(limit, approximateIdeal - 100, soon));

        // "Less" under the ideal but should recheck and throttle again
        scenarios.add(new RateLimit(limit, approximateIdeal - 99, soon));

        // Check that we are under our ideal so we should throttle
        scenarios.add(new RateLimit(limit, approximateIdeal - 99, soon));


        // Reset back to a full limit
        scenarios.add(new RateLimit(limit, limit, soon));
        setupStubs(scenarios);

        // First check will say under budget (add counts)
        ApiRateLimitChecker.ThrottleForNormalize.checkApiRateLimit(listener, github);
        assertEquals(1, countOfOutputLinesContaining("under budget"));
        assertFalse(handler.getView().stream().anyMatch(m -> m.getMessage().contains("Sleeping")));

        // Second check will go over budget
        ApiRateLimitChecker.ThrottleForNormalize.checkApiRateLimit(listener, github);
        assertEquals(2, countOfOutputLinesContaining("rechecking"));
        assertEquals(3, countOfOutputLinesContaining("Still sleeping"));
        assertEquals(2, countOfOutputLinesContaining("Sleeping for"));
        assertEquals(1, countOfOutputLinesContaining("under budget"));

        // The last scenario will trigger back to under budget with a full limit but no new messages
        assertEquals(8, handler.getView().stream().count());
    }

    /**
     * Verify OnNormal throttling when past the buffer
     *
     * @author  Julian V. Modesto
     */
    @Test
    public void NormalizeThrottleWithBurnedBuffer() throws Exception {
        long now = System.currentTimeMillis();

        // Set up scenarios
        List<RateLimit> scenarios = new ArrayList<>();
        int limit = 5000;
        // Trigger a throttle but the reset time is past
        scenarios.add(new RateLimit(limit, 0, new Date(now)));
        // Trigger a throttle but the reset time is past
        scenarios.add(new RateLimit(limit, 0, new Date(now)));
        // We never want to go under the buffer regardless of time past
        scenarios.add(new RateLimit(limit, 0, new Date(now - TimeUnit.SECONDS.toMillis(30))));
        // Trigger a throttle but we have burned our buffer
        scenarios.add(new RateLimit(limit, 0, soon));
        // Refresh rate limit
        scenarios.add(new RateLimit(limit, limit, soon));
        setupStubs(scenarios);

        // Run check against API limit
        ApiRateLimitChecker.ThrottleForNormalize.checkApiRateLimit(listener, github);

        // Expect a triggered throttle for normalize
        assertEquals(1, countOfOutputLinesContaining(
            "Current quota has 0 remaining (250 over budget). Next quota of 5000 due now. Sleeping for 7 ms."));
        assertEquals(1, countOfOutputLinesContaining(
            "Current quota has 0 remaining (250 over budget). Next quota of 5000 due now. Sleeping for 15 ms."));
        assertEquals(1, countOfOutputLinesContaining(
            "Current quota has 0 remaining (250 over budget). Next quota of 5000 due now. Sleeping for 16 ms."));
        assertEquals(4, countOfOutputLinesContaining("Sleeping"));
        // Expect that we stopped waiting on a refresh
        assertEquals(1, countOfOutputLinesContaining("refreshed"));
    }

    /**
     * Verify throttle in "OnOver" and the wait happens for the correct amount of time
     *
     * @author Alex Taylor
     */
    @Test
    public void OnOverThrottleTimingRateLimitCheck() throws Exception {
        // Longer timings that test defaults for more consistent measurements.
        ApiRateLimitChecker.setExpirationWaitMillis(60);
        ApiRateLimitChecker.setNotificationWaitMillis(200);

        // set up scenarios
        List<RateLimit> scenarios = new ArrayList<>();
        // set remaining quota to over buffer to trigger throttle
        int limit = 5000;
        int buffer = ApiRateLimitChecker.calculateBuffer(limit);
        int expectedNumThrottles = 5;

        //This is going to not throttle for 5 values and then throttle the next 5
        for (int i = -5; i <= expectedNumThrottles; i++) {
            scenarios.add(new RateLimit(limit, buffer - i, soon));
        }

        // finally, stop throttling by restoring quota
        scenarios.add(new RateLimit(limit, limit, soon));
        setupStubs(scenarios);

        long start = System.currentTimeMillis();

        // check rate limit to hit the first 10 scenarios
        for (int i = 0; i < 6; i++) {
            ApiRateLimitChecker.ThrottleOnOver.checkApiRateLimit(listener, github);
        }

        //should be no ouput
        assertEquals(0, countOfOutputLinesContaining("Sleeping"));

        // check rate limit to hit the next 5 scenarios
        ApiRateLimitChecker.ThrottleOnOver.checkApiRateLimit(listener, github);

        //want to make sure that the 5 API checks (the last one is resetting) are taking at least 1000 MS
        assertTrue((System.currentTimeMillis() - start) > 1000);

        //output for all the throttled scenarios. Again the first will show the remaining and then the rest will just sleep
        assertEquals(1, countOfOutputLinesContaining("Sleeping"));
        assertEquals(expectedNumThrottles - 1, countOfOutputLinesContaining("Still sleeping"));

        //no new output
        ApiRateLimitChecker.ThrottleOnOver.checkApiRateLimit(listener, github);
        assertEquals(1, countOfOutputLinesContaining("Sleeping"));
        assertEquals(expectedNumThrottles - 1, countOfOutputLinesContaining("Still sleeping"));
    }

    /**
     * Verify the "OnNormalize" throttle and wait is happening for the correct amount of time
     *
     * @author  Alex Taylor
     */
    @Test
    public void NormalizeThrottleTimingRateLimitCheck() throws Exception {
        ApiRateLimitChecker.setExpirationWaitMillis(60);
        ApiRateLimitChecker.setNotificationWaitMillis(200);

        // Set up scenarios
        List<RateLimit> scenarios = new ArrayList<>();
        int limit = 5000;
        // estimate the ideal
        int approximateIdeal = 4000;
        int burst = ApiRateLimitChecker.calculateNormalizedBurst(limit);
        // Warm up server
        scenarios.add(new RateLimit(limit, limit, soon));
        // Trigger a throttle for normalize
        scenarios.add(new RateLimit(limit, approximateIdeal - burst, soon));
        // Trigger a wait until rate limit
        scenarios.add(new RateLimit(limit, approximateIdeal - burst, soon));
        // Trigger a wait until rate limit
        scenarios.add(new RateLimit(limit, approximateIdeal - burst, soon));
        // Refresh rate limit
        scenarios.add(new RateLimit(limit, limit, soon));
        setupStubs(scenarios);
        ApiRateLimitChecker.ThrottleForNormalize.checkApiRateLimit(listener, github);

        //start timing
        long start = System.currentTimeMillis();

        // Run check
        ApiRateLimitChecker.ThrottleForNormalize.checkApiRateLimit(listener, github);

        // Want to make sure that the 3 API checks are taking at least 600 MS
        assertTrue((System.currentTimeMillis() - start) > 600);
        // Expect a triggered throttle for normalize
        assertEquals(1, countOfOutputLinesContaining("Sleeping"));
        // Expect a wait until rate limit
        assertEquals(2, countOfOutputLinesContaining("Still sleeping"));
        // Expect that we stopped waiting on a refresh
        assertEquals(1, countOfOutputLinesContaining("refreshed"));
    }


    /**
     * Verify the throttle is happening for the "OnNormalize"
     * and proves the ideal "limit" changes correctly with time
     *
     * @author  Alex Taylor
     */
    @Test
    public void NormalizeExpectedIdealOverTime() throws Exception {
        // Set up scenarios
        List<RateLimit> scenarios = new ArrayList<>();
        long start = System.currentTimeMillis();

        /*
         * With the limit at 1000: the burst will be limit/5 and buffer will be limit/20
         */
        int limit = 1000;
        // estimate the ideal
        // Formula should be the ((limit - (burst + buffer)) * % of hour left before reset) + buffer
        // buffer for this limit will be limit/20 = 250
        // burst for this will be limit/5 = 1000
        // Ideal calculated at 45, 30, 15, and 0 minutes
        int[] morePreciseIdeal = {612, 424, 237, 50};

        // deadline set for those times as well
        for (int i = 0; i < 4; i++) {
            scenarios.add(new RateLimit(limit, morePreciseIdeal[i], new Date(start + TimeUnit.MINUTES.toMillis((3 - i) * 15))));
        }
        /*
         * With the limit at 400: the burst will be limit/10 and buffer will be limit/20
         */
        limit = 400;
        morePreciseIdeal = new int[]{274, 189, 104, 20};

        // deadline set for those times as well
        for (int i = 0; i < 4; i++) {
            scenarios.add(new RateLimit(limit, morePreciseIdeal[i], new Date(start + TimeUnit.MINUTES.toMillis((3 - i) * 15))));
        }
        /*
         * With the limit at 1000: the burst will be limit/5 and buffer will be 15
         */
        limit = 200;
        morePreciseIdeal = new int[]{138, 97, 56, 15};

        // deadline set for those times as well
        for (int i = 0; i < 4; i++) {
            scenarios.add(new RateLimit(limit, morePreciseIdeal[i], new Date(start + TimeUnit.MINUTES.toMillis((3 - i) * 15))));
        }

        setupStubs(scenarios);

        for (int i = 0; i < 12; i++) {
            ApiRateLimitChecker.ThrottleForNormalize.checkApiRateLimit(listener, github);
        }

        // Expect a triggered throttle for normalize
        assertEquals(12, countOfOutputLinesContaining("Current quota"));
        //Making sure the budgets are correct
        assertEquals(12, countOfOutputLinesContaining("0 under budget"));
        // no occurences of sleeping
        assertEquals(0, countOfOutputLines(m -> m.matches("[sS]leeping")));
    }

    /**
     * Verify when the throttle is happening for the "OnOver"
     * and prove the current "limit" does not change the same way as Normalize
     *
     * @author  Alex Taylor
     */
    @Test
    public void OnOverExpectedIdealOverTime() throws Exception {
        long start = System.currentTimeMillis();
        // Set up scenarios
        List<RateLimit> scenarios = new ArrayList<>();
        int limit = 1000;
        // estimate the ideal(which does not apply in this scenario)
        int[] morePreciseIdeal = {612, 424, 237, 49};

        // deadline set for those times as well
        for (int i = 0; i < 4; i++) {
            scenarios.add(new RateLimit(limit, morePreciseIdeal[i], new Date(start + TimeUnit.MINUTES.toMillis((3 - i) * 15))));
        }
        // Refresh rate limit
        scenarios.add(new RateLimit(limit, limit, soon));
        setupStubs(scenarios);

        // Run check a few times to ensure we don't get stuck
        for (int i = 0; i < 5; i++) {
            ApiRateLimitChecker.ThrottleOnOver.checkApiRateLimit(listener, github);
        }

        // Expect this to only get throttled  when we are over the buffer limit
        assertEquals(1, countOfOutputLinesContaining("Current quota"));
        //Making sure the budget messages are correct
        assertEquals(1, countOfOutputLinesContaining("1 over buffer"));
    }

    /**
     * Verify the expected reset happens and notifications happen on time in the logs for Normalize
     *
     * @author  Alex Taylor
     */
    @Test
    public void ExpectedResetTimingNormalize() throws Exception {

        // Use a longer notification interval to make the test produce stable output
        ApiRateLimitChecker.setNotificationWaitMillis(2000);

        // Set up scenarios
        List<RateLimit> scenarios = new ArrayList<>();
        int limit = 1000;
        int buffer = 50;
        scenarios.add(new RateLimit(limit, limit, soon));
        //Giving a bit of time to make sure the setup happens on time
        long start = System.currentTimeMillis() + 7000;

        for (int i = 0; i <= 3; i++) {
            scenarios.add(new RateLimit(limit, buffer - 5, new Date(start)));
        }
        // Refresh rate limit
        scenarios.add(new RateLimit(limit, limit, soon));
        setupStubs(scenarios);
        // First server warm up
        ApiRateLimitChecker.ThrottleForNormalize.checkApiRateLimit(listener, github);

        while(System.currentTimeMillis() + 5500 < start)
        {
            Thread.sleep(25);
        }

        // Run check a few times to ensure we don't get stuck
        ApiRateLimitChecker.ThrottleForNormalize.checkApiRateLimit(listener, github);

        // Expect a triggered throttle for normalize
        assertEquals(2, countOfOutputLinesContaining("Current quota"));
        assertEquals(2, countOfOutputLinesContaining("Still sleeping"));
    }

    /**
     * Verify the expected reset happens and notifications happen on time in the logs for OnOver
     *
     * @author  Alex Taylor
     */
    @Test
    public void ExpectedResetTimingOnOver() throws Exception {

        // Use a longer notification interval to make the test produce stable output
        ApiRateLimitChecker.setNotificationWaitMillis(2000);

        // Set up scenarios
        List<RateLimit> scenarios = new ArrayList<>();
        int limit = 1000;
        int buffer = 50;
        scenarios.add(new RateLimit(limit, limit, soon));
        //Giving a bit of time to make sure the setup happens on time
        long start = System.currentTimeMillis() + 7000;

        for (int i = 0; i <= 3; i++) {
            scenarios.add(new RateLimit(limit, buffer - 5, new Date(start)));
        }
        // Refresh rate limit
        scenarios.add(new RateLimit(limit, limit, soon));
        setupStubs(scenarios);
        // First server warm up
        ApiRateLimitChecker.ThrottleOnOver.checkApiRateLimit(listener, github);

        while(System.currentTimeMillis() + 5500 < start)
        {
            Thread.sleep(25);
        }

        ApiRateLimitChecker.ThrottleOnOver.checkApiRateLimit(listener, github);

        // We have 7 "notify" type messages and 2 "expired" type messages
        assertEquals(2, countOfOutputLinesContaining("Current quota"));
        assertEquals(2, countOfOutputLinesContaining("Still sleeping"));
    }
}
