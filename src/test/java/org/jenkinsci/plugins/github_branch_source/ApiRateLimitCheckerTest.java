package org.jenkinsci.plugins.github_branch_source;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ScenarioMappingBuilder;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import hudson.util.LogTaskListener;
import hudson.util.RingBufferLogHandler;
import org.junit.Test;
import org.junit.Before;
import org.kohsuke.github.GitHub;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.resetAllScenarios;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

public class ApiRateLimitCheckerTest extends AbstractGitHubWireMockTest {

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

    public static int getRequestCount(WireMockServer server) {
        return server.countRequestsMatching(RequestPatternBuilder.allRequests().build()).getCount();
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


    @Before
    public void setUp() throws Exception {
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
        assertEquals(100, getRequestCount(githubApi));
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
        assertEquals(100, getRequestCount(githubApi));
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
        scenarios.add(new RateLimit(limit, limit, new Date(soon.getTime() + 2000)));
        setupStubs(scenarios);

        // check rate limit to hit the first 11 scenarios because the throttle (add more here)
        // does not happen until under buffer
        for (int i = 0; i < 11; i++) {
            ApiRateLimitChecker.ThrottleOnOver.checkApiRateLimit(listener, github);
        }

        //should be no output
        assertEquals(0, countOfOutputLines(m -> m.matches("[sS]leeping")));

        assertEquals(11, getRequestCount(githubApi));

        // check rate limit to hit the next 10 scenarios
        ApiRateLimitChecker.ThrottleOnOver.checkApiRateLimit(listener, github);

        //output for all the throttled scenarios. Sleeps normally on the first and then the `notify` hits the next 9
        assertEquals(1, countOfOutputLinesContaining("Sleeping for"));
        assertEquals(expectedNumThrottles-1, countOfOutputLinesContaining("Still sleeping"));
        assertEquals(1, countOfOutputLinesContaining("refreshed"));
        assertEquals(23, getRequestCount(githubApi));

        //Make sure no new output
        ApiRateLimitChecker.ThrottleOnOver.checkApiRateLimit(listener, github);
        assertEquals(1, countOfOutputLinesContaining("Sleeping for"));
        assertEquals(expectedNumThrottles-1, countOfOutputLinesContaining("Still sleeping"));
        assertEquals(1, countOfOutputLinesContaining("refreshed"));
        assertEquals(24, getRequestCount(githubApi));
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
        // ignored as invalid by github-api library
        scenarios.add(new RateLimit(limit, approximateIdeal - 100, new Date(soon.getTime() + 2000)));

        // "Less" under the ideal but should recheck and throttle again
        scenarios.add(new RateLimit(limit, approximateIdeal - 99, new Date(soon.getTime() + 2000)));

        // Check that we are under our ideal so we should throttle
        scenarios.add(new RateLimit(limit, approximateIdeal - 99, new Date(soon.getTime() + 2000)));

        // Reset back to a full limit
        scenarios.add(new RateLimit(limit, limit, new Date(soon.getTime() + 3000)));
        setupStubs(scenarios);

        // First check will say under budget (add counts)
        ApiRateLimitChecker.ThrottleForNormalize.checkApiRateLimit(listener, github);

        assertEquals(1, getRequestCount(githubApi));
        assertEquals(1, countOfOutputLinesContaining("under budget"));
        assertFalse(handler.getView().stream().anyMatch(m -> m.getMessage().contains("Sleeping")));

        // Second check will go over budget
        ApiRateLimitChecker.ThrottleForNormalize.checkApiRateLimit(listener, github);

        assertEquals(9, getRequestCount(githubApi));
        assertEquals(2, countOfOutputLinesContaining("rechecking"));
        assertEquals(3, countOfOutputLinesContaining("Still sleeping"));
        assertEquals(2, countOfOutputLinesContaining("Sleeping for"));
        assertEquals(1, countOfOutputLinesContaining("under budget"));
        assertEquals(2, countOfOutputLinesContaining("Jenkins is attempting to evenly distribute GitHub API requests"));

        // The last scenario will trigger back to under budget with a full limit but no new messages
        assertEquals(10, handler.getView().size());
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
        scenarios.add(new RateLimit(limit, limit, new Date(soon.getTime() + 2000)));
        setupStubs(scenarios);

        // Run check against API limit
        ApiRateLimitChecker.ThrottleForNormalize.checkApiRateLimit(listener, github);

        // Expect a triggered throttle for normalize
        assertEquals(1, countOfOutputLinesContaining(
            "Current quota for Github API usage has 0 remaining (250 over budget). Next quota of 5000 due now. Sleeping for 7 ms."));
        assertEquals(1, countOfOutputLinesContaining(
            "Current quota for Github API usage has 0 remaining (250 over budget). Next quota of 5000 due now. Sleeping for 15 ms."));
        assertEquals(1, countOfOutputLinesContaining(
            "Current quota for Github API usage has 0 remaining (250 over budget). Next quota of 5000 due now. Sleeping for 16 ms."));
        assertEquals(4, countOfOutputLinesContaining(
            "Jenkins is attempting to evenly distribute GitHub API requests. To configure a different rate limiting strategy, such as having Jenkins restrict GitHub API requests only when near or above the GitHub rate limit, go to \"GitHub API usage\" under \"Configure System\" in the Jenkins settings."));
        assertEquals(4, countOfOutputLinesContaining("Sleeping"));
        // Expect that we stopped waiting on a refresh
        assertEquals(1, countOfOutputLinesContaining("refreshed"));
        assertEquals(6, getRequestCount(githubApi));
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
        scenarios.add(new RateLimit(limit, limit, new Date(soon.getTime() + 2000)));
        setupStubs(scenarios);

        long start = System.currentTimeMillis();

        // check rate limit to hit the first 10 scenarios
        for (int i = 0; i < 6; i++) {
            ApiRateLimitChecker.ThrottleOnOver.checkApiRateLimit(listener, github);
        }

        assertEquals(6, getRequestCount(githubApi));

        //should be no output
        assertEquals(0, countOfOutputLinesContaining("Sleeping"));

        // check rate limit to hit the next 5 scenarios
        ApiRateLimitChecker.ThrottleOnOver.checkApiRateLimit(listener, github);

        assertEquals(13, getRequestCount(githubApi));

        //want to make sure that the 5 API checks (the last one is resetting) are taking at least 1000 MS
        assertTrue((System.currentTimeMillis() - start) > 1000);

        //output for all the throttled scenarios. Again the first will show the remaining and then the rest will just sleep
        assertEquals(1, countOfOutputLinesContaining("Sleeping"));
        assertEquals(expectedNumThrottles - 1, countOfOutputLinesContaining("Still sleeping"));

        //no new output
        ApiRateLimitChecker.ThrottleOnOver.checkApiRateLimit(listener, github);
        assertEquals(14, getRequestCount(githubApi));
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
        // github-api will ignore ratelimit responses that appear invalid
        // Rate limit only goes up when the the reset date is later than previous records.
        scenarios.add(new RateLimit(limit, limit, new Date(soon.getTime() + 2000)));
        setupStubs(scenarios);
        ApiRateLimitChecker.ThrottleForNormalize.checkApiRateLimit(listener, github);

        //start timing
        long start = System.currentTimeMillis();

        // Run check
        ApiRateLimitChecker.ThrottleForNormalize.checkApiRateLimit(listener, github);

        assertEquals(6, getRequestCount(githubApi));

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
        int[] morePreciseIdeal = {50, 237, 424, 612};

        // deadline set for those times as well
        for (int i = 0; i < 4; i++) {
            scenarios.add(new RateLimit(limit, morePreciseIdeal[i], new Date(start + TimeUnit.MINUTES.toMillis(i * 15))));
        }
        /*
         * With the limit at 400: the burst will be limit/10 and buffer will be limit/20
         */
        limit = 400;
        morePreciseIdeal = new int[]{20, 104, 189, 274};

        // deadline set for those times as well
        for (int i = 0; i < 4; i++) {
            scenarios.add(new RateLimit(limit, morePreciseIdeal[i], new Date(start + TimeUnit.MINUTES.toMillis(i * 15))));
        }
        /*
         * With the limit at 1000: the burst will be limit/5 and buffer will be 15
         */
        limit = 200;
        morePreciseIdeal = new int[]{15, 56, 97, 138};

        // deadline set for those times as well
        for (int i = 0; i < 4; i++) {
            scenarios.add(new RateLimit(limit, morePreciseIdeal[i], new Date(start + TimeUnit.MINUTES.toMillis(i * 15))));
        }

        setupStubs(scenarios);

        for (int i = 0; i < 12; i++) {
            ApiRateLimitChecker.ThrottleForNormalize.checkApiRateLimit(listener, github);
        }

        assertEquals(12, getRequestCount(githubApi));

        // Expect a triggered throttle for normalize
        assertEquals(12, countOfOutputLinesContaining("Current quota"));
        // Making sure the budgets are correct
        assertEquals(12, countOfOutputLinesContaining("0 under budget"));
        // no occurrences of sleeping
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
        // Rate limit
        int[] morePreciseIdeal = {49, 237, 424, 612};

        // Rate limit records that expire early than the last returned are ignored as invalid
        // Must be the same or greater
        // deadline set for those times as well
        for (int i = 0; i < 4; i++) {
            scenarios.add(new RateLimit(limit, morePreciseIdeal[i], new Date(start + TimeUnit.MINUTES.toMillis((i) * 15))));
        }

        // Refresh rate limit
        scenarios.add(new RateLimit(limit, limit, soon));
        setupStubs(scenarios);

        // Run check a few times to ensure we don't get stuck
        for (int i = 0; i < 5; i++) {
            ApiRateLimitChecker.ThrottleOnOver.checkApiRateLimit(listener, github);
        }

        assertEquals(6, getRequestCount(githubApi));

        // Expect this to only get throttled  when we are over the buffer limit
        assertEquals(1, countOfOutputLinesContaining("Current quota"));
        //Making sure the budget messages are correct
        assertEquals(1, countOfOutputLinesContaining("1 over buffer"));
        assertEquals(1, countOfOutputLinesContaining("Jenkins is restricting GitHub API requests only when near or above the rate limit. To configure a different rate limiting strategy, such as having Jenkins attempt to evenly distribute GitHub API requests, go to \"GitHub API usage\" under \"Configure System\" in the Jenkins settings."));
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
        //Giving a bit of time to make sure the setup happens on time
        long start = System.currentTimeMillis() + 7000;
        scenarios.add(new RateLimit(limit, limit, new Date(start)));

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
        assertEquals(6, getRequestCount(githubApi));
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
        //Giving a bit of time to make sure the setup happens on time
        long start = System.currentTimeMillis() + 7000;
        scenarios.add(new RateLimit(limit, limit, new Date(start)));

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
        assertEquals(6, getRequestCount(githubApi));
    }
}
