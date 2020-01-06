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
import org.joda.time.DateTime;
import org.junit.*;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.github.GHRateLimit;
import org.kohsuke.github.GitHub;

import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.jenkinsci.plugins.github_branch_source.ApiRateLimitChecker.ThrottleOnOver;
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


    private class JsonRateLimit {
        GHRateLimit rate;

        JsonRateLimit(int limit, int remaining, Date reset) {
            GHRateLimit r = new GHRateLimit();
            r.limit = limit;
            r.remaining = remaining;
            r.reset = reset;
            rate = r;
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
    private GitHub github;

    private final ApiRateLimitChecker apiRateLimitCheckerThrottleOnOver = ThrottleOnOver;
    private final ApiRateLimitChecker apiRateLimitCheckerThrottleForNormalize = ApiRateLimitChecker.ThrottleForNormalize;
    private final Random entropy = new Random(1000);

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
    }

    private void setupStubs(List<JsonRateLimit> scenarios) {
        String scenarioName = UUID.randomUUID().toString();
        for (int i = 0; i < scenarios.size(); i++) {
            String state = (i == 0) ? Scenario.STARTED : Integer.toString(i);
            String nextState = Integer.toString(i + 1);
            JsonRateLimit scenarioResponse = scenarios.get(i);

            String limit = Integer.toString(scenarioResponse.rate.limit);
            String remaining = Integer.toString(scenarioResponse.rate.remaining);
            String reset = Long.toString(scenarioResponse.rate.reset.toInstant().getEpochSecond());
            String body = "{ \"rate\": { \"limit\": " + limit +  ", " +
                    "\"remaining\": " + remaining + ", " +
                    "\"reset\": " + reset + " } }";
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

    private Date soon = Date.from(LocalDateTime.now().plusMinutes(60).atZone(ZoneId.systemDefault()).toInstant());

    @Test
    public void ThrottleOffOverTestWithQuota() throws Exception {
        // set up checker: don't actually sleep
        ApiRateLimitChecker.setEntropy(entropy);
        ApiRateLimitChecker.setExpirationWaitMillis(1);
        ApiRateLimitChecker.setNotificationWaitMillis(1);

        // set up scenarios
        List<JsonRateLimit> scenarios = new ArrayList<>();
        int limit = 5000;
        scenarios.add(new JsonRateLimit(limit, limit, soon));
        setupStubs(scenarios);

        // Given a full rate limit quota, then we expect no throttling
        for (int i = 0; i < 100; i++) {
            apiRateLimitCheckerThrottleOnOver.checkApiRateLimit(listener, github);
        }

        assertEquals(0, handler.getView().stream().map(LogRecord::getMessage).filter(m -> m.contains("Sleeping")).count());
    }

    @Test
    public void ThrottleOffNormalizeTestWithQuota() throws Exception {
        // set up checker: don't actually sleep
        ApiRateLimitChecker.setEntropy(entropy);
        ApiRateLimitChecker.setExpirationWaitMillis(1);
        ApiRateLimitChecker.setNotificationWaitMillis(1);

        // set up scenarios
        List<JsonRateLimit> scenarios = new ArrayList<>();
        int limit = 5000;
        scenarios.add(new JsonRateLimit(limit, limit, soon));
        setupStubs(scenarios);

        // Given a full rate limit quota, then we expect no throttling
        for (int i = 0; i < 100; i++) {
            apiRateLimitCheckerThrottleForNormalize.checkApiRateLimit(listener, github);
        }

        assertEquals(0, handler.getView().stream().map(LogRecord::getMessage).filter(m -> m.contains("Sleeping")).count());
    }

    @Test
    public void ThrottleOnOverTest() throws Exception {
        // set up checker: don't actually sleep
        ApiRateLimitChecker.setEntropy(entropy);
        ApiRateLimitChecker.setExpirationWaitMillis(10);
        ApiRateLimitChecker.setNotificationWaitMillis(10);

        // set up scenarios
        List<JsonRateLimit> scenarios = new ArrayList<>();
        // set remaining quota to over buffer to trigger throttle
        int limit = 5000;
        int buffer = ApiRateLimitChecker.calculateBuffer(limit);
        int expectedNumThrottles = 20;

        //This is going to not throttle for 10 values and then throttle the next 20
        for (int i = -10; i <= expectedNumThrottles; i++) {
            scenarios.add(new JsonRateLimit(limit, buffer - i, soon));
        }

        // finally, stop throttling by restoring quota
        scenarios.add(new JsonRateLimit(limit, limit, soon));
        setupStubs(scenarios);

        // check rate limit to hit the first 10 scenarios
        for (int i = 0; i < 10; i++) {
            apiRateLimitCheckerThrottleOnOver.checkApiRateLimit(listener, github);
        }

        //should be no ouput
        assertEquals(0, handler.getView().stream().map(LogRecord::getMessage).filter(m -> m.contains("Sleeping")).count());

        // check rate limit to hit the next 20 scenarios
        for (int i = 10; i < 30; i++) {
            apiRateLimitCheckerThrottleOnOver.checkApiRateLimit(listener, github);
        }

        //output for all the throttled scenarios
        assertEquals(expectedNumThrottles, handler.getView().stream().map(LogRecord::getMessage).filter(m -> m.contains("Sleeping")).count());

        //one last check to grab the reset
        apiRateLimitCheckerThrottleOnOver.checkApiRateLimit(listener, github);

        //no new output
        assertEquals(expectedNumThrottles, handler.getView().stream().map(LogRecord::getMessage).filter(m -> m.contains("Sleeping")).count());
    }

    @Test
    public void ThrottleForNormalizeTestWithinIdeal() throws Exception {
        // set up checker: don't actually sleep
        ApiRateLimitChecker.setEntropy(entropy);
        ApiRateLimitChecker.setExpirationWaitMillis(1);
        ApiRateLimitChecker.setNotificationWaitMillis(1);

        List<JsonRateLimit> scenarios = new ArrayList<>();
        int limit = 5000;
        int buffer = ApiRateLimitChecker.calculateBuffer(limit);

        // Approximate the ideal here
        int approximateIdeal = 4000;

        // Check that if we're above within our ideal, then we don't throttle
        scenarios.add(new JsonRateLimit(limit, approximateIdeal + buffer - 100, soon));

        // Check that we are under our ideal so we should throttle
        scenarios.add(new JsonRateLimit(limit, approximateIdeal - 100, soon));

        // Check that we are under our ideal so we should throttle
        scenarios.add(new JsonRateLimit(limit, approximateIdeal - 100, soon));

        // Reset back to a full limit
        scenarios.add(new JsonRateLimit(limit, limit, soon));
        setupStubs(scenarios);

        // First check will say under budget
        apiRateLimitCheckerThrottleForNormalize.checkApiRateLimit(listener, github);
        assertTrue(handler.getView().stream().anyMatch(m -> m.getMessage().contains("under budget")));
        assertFalse(handler.getView().stream().anyMatch(m -> m.getMessage().contains("Sleeping")));

        // Second check will go over budget
        apiRateLimitCheckerThrottleForNormalize.checkApiRateLimit(listener, github);
        assertTrue(handler.getView().stream().anyMatch(m -> m.getMessage().contains("Sleeping")));
        assertTrue(handler.getView().stream().anyMatch(m -> m.getMessage().contains("rechecking")));
        assertTrue(handler.getView().stream().anyMatch(m -> m.getMessage().contains("Still sleeping")));

        // The last scenario will trigger back to under budget with a full limit but no new messges
        assertEquals(4, handler.getView().stream().count());
    }


    @Test
    public void ThrottleForNormalizeTest() throws Exception {
        // set up checker: don't actually sleep
        ApiRateLimitChecker.setEntropy(entropy);
        ApiRateLimitChecker.setExpirationWaitMillis(1);
        ApiRateLimitChecker.setNotificationWaitMillis(1);

        // Set up scenarios
        List<JsonRateLimit> scenarios = new ArrayList<>();
        int limit = 5000;

        // estimate the ideal for the  current reset time
        int approximateIdeal = 4000;
        int burst = ApiRateLimitChecker.calculateNormalizedBurst(limit);

        // Trigger a throttle for normalize
        scenarios.add(new JsonRateLimit(limit, approximateIdeal - burst, soon));
        // Trigger a wait until rate limit
        scenarios.add(new JsonRateLimit(limit, approximateIdeal - burst, soon));
        // Refresh rate limit
        scenarios.add(new JsonRateLimit(limit, limit, soon));
        setupStubs(scenarios);

        // Run check a few times to ensure we don't get stuck
        for (int i = 0; i < 3; i++) {
            apiRateLimitCheckerThrottleForNormalize.checkApiRateLimit(listener, github);
        }

        // Expect a triggered throttle for normalize
        assertEquals(1, handler.getView().stream().map(LogRecord::getMessage).filter(m -> m.contains("Sleeping")).count());
        // Expect a wait until rate limit
        assertEquals(1, handler.getView().stream().map(LogRecord::getMessage).filter(m -> m.contains("Still sleeping")).count());
        // Expect that we stopped waiting on a refresh
        assertEquals(1, handler.getView().stream().map(LogRecord::getMessage).filter(m -> m.contains("refreshed")).count());
    }

   @Test
    public void NormalizeThrottleTimingRateLimitCheck() throws Exception {
        // set up checker: don't actually sleep
        ApiRateLimitChecker.setEntropy(entropy);
        ApiRateLimitChecker.setExpirationWaitMillis(200);
        ApiRateLimitChecker.setNotificationWaitMillis(200);

        // Set up scenarios
        List<JsonRateLimit> scenarios = new ArrayList<>();
        int limit = 5000;
        // estimate the ideal
        int approximateIdeal = 4000;
        int burst = ApiRateLimitChecker.calculateNormalizedBurst(limit);
        // Trigger a throttle for normalize
        scenarios.add(new JsonRateLimit(limit, approximateIdeal - burst, soon));
        // Trigger a wait until rate limit
        scenarios.add(new JsonRateLimit(limit, approximateIdeal - burst, soon));
        // Refresh rate limit
        scenarios.add(new JsonRateLimit(limit, limit, soon));
        setupStubs(scenarios);
       long start = System.currentTimeMillis();

        // Run check a few times to ensure we don't get stuck
        for (int i = 0; i < 3; i++) {
            apiRateLimitCheckerThrottleForNormalize.checkApiRateLimit(listener, github);
        }

        //want to make sure that the 3 API checks are taking at least 300 MS
       System.out.println(System.currentTimeMillis() - start);
        assertTrue((System.currentTimeMillis() - start) > 300);
        // Expect a triggered throttle for normalize
        assertEquals(1, handler.getView().stream().map(LogRecord::getMessage).filter(m -> m.contains("Sleeping")).count());
        // Expect a wait until rate limit
        assertEquals(1, handler.getView().stream().map(LogRecord::getMessage).filter(m -> m.contains("Still sleeping")).count());
        // Expect that we stopped waiting on a refresh
        assertEquals(1, handler.getView().stream().map(LogRecord::getMessage).filter(m -> m.contains("refreshed")).count());
    }

    @Test
    public void NormalizeThrottleWithBurnedBuffer() throws Exception {
        // set up checker: don't actually sleep
        ApiRateLimitChecker.setEntropy(entropy);
        ApiRateLimitChecker.setExpirationWaitMillis(10);
        ApiRateLimitChecker.setNotificationWaitMillis(10);

        // Set up scenarios
        List<JsonRateLimit> scenarios = new ArrayList<>();
        int limit = 5000;
        // estimate the ideal
        int approximateIdeal = 4000;
        int burst = ApiRateLimitChecker.calculateNormalizedBurst(limit);
        // Trigger a throttle but the reset time is past
        scenarios.add(new JsonRateLimit(limit, 0, new Date(System.currentTimeMillis())));
        // Trigger a throttle but we have burned our buffer
        scenarios.add(new JsonRateLimit(limit, 0, soon));
        // Refresh rate limit
        scenarios.add(new JsonRateLimit(limit, limit, soon));
        setupStubs(scenarios);

        // Run check a few times to ensure we don't get stuck
        for (int i = 0; i < 3; i++) {
            apiRateLimitCheckerThrottleForNormalize.checkApiRateLimit(listener, github);
        }
        // Expect a triggered throttle for normalize
        assertEquals(2, handler.getView().stream().map(LogRecord::getMessage).filter(m -> m.contains("Sleeping")).count());
        // Expect that we stopped waiting on a refresh
        assertEquals(1, handler.getView().stream().map(LogRecord::getMessage).filter(m -> m.contains("refreshed")).count());
    }

    @Test
    @Ignore
    public void ExpectedIdealOverTime() throws Exception {
        // Right now this needs to be ignored because rounding errors are too common to hit the line with the ideal
        // set up checker: don't actually sleep
        ApiRateLimitChecker.setEntropy(entropy);
        ApiRateLimitChecker.setExpirationWaitMillis(10);
        ApiRateLimitChecker.setNotificationWaitMillis(10);
        // Set up scenarios
        List<JsonRateLimit> scenarios = new ArrayList<>();
        int limit = 5000;
        // estimate the ideal
        // Formula should be the ((limit - (burst + buffer)) * % of hour left before reset) + buffer
        // buffer for this limit will be limit/20 = 250
        // burst for this will be limit/5 = 1000
        // Ideal calculated at 0, 15, 30, and 45 minutes
        int[] morePreciseIdeal = {250, 1186, 2124, 3061};

        // deadline set for those times as well
        for (int i = 0; i < 4; i++) {
            scenarios.add(new JsonRateLimit(limit, morePreciseIdeal[i], new Date((System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(i*15)))));
        }
        // Refresh rate limit
        scenarios.add(new JsonRateLimit(limit, limit, soon));
        setupStubs(scenarios);

        // Run check a few times to ensure we don't get stuck
        for (int i = 0; i < 8; i++) {
            apiRateLimitCheckerThrottleForNormalize.checkApiRateLimit(listener, github);
        }

        // Expect a triggered throttle for normalize
        assertEquals(4, handler.getView().stream().map(LogRecord::getMessage).filter(m -> m.contains("Current quota")).count());
        //Making sure the budgets are correct but rounding gets tricky here
        assertEquals(4, handler.getView().stream().map(LogRecord::getMessage).filter(m -> m.contains("0 under budget")).count()
                + handler.getView().stream().map(LogRecord::getMessage).filter(m -> m.contains("1 over budget")).count()
                + handler.getView().stream().map(LogRecord::getMessage).filter(m -> m.contains("1 under budget")).count());
    }
}