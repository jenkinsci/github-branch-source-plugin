package org.jenkinsci.plugins.github_branch_source;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.resetAllScenarios;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ScenarioMappingBuilder;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import hudson.util.LogTaskListener;
import hudson.util.RingBufferLogHandler;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.jenkinsci.plugins.github.config.GitHubServerConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.kohsuke.github.GHRateLimit;
import org.kohsuke.github.GitHub;
import org.mockito.Mockito;

public class ApiRateLimitCheckerTest extends AbstractGitHubWireMockTest {

    private RingBufferLogHandler handler;
    private LogTaskListener listener;

    private final Random entropy = new Random(1000);

    private final Date soon = Date.from(
            LocalDateTime.now().plusMinutes(60).atZone(ZoneId.systemDefault()).toInstant());

    private GitHub github;
    private int initialRequestCount;

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
        return server.countRequestsMatching(RequestPatternBuilder.allRequests().build())
                .getCount();
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
        resetAllScenarios();

        handler = new RingBufferLogHandler(1000);
        final Logger logger = Logger.getLogger(getClass().getName());
        logger.addHandler(handler);
        listener = new LogTaskListener(logger, Level.INFO);

        final Logger defaultLogger = Logger.getLogger(ApiRateLimitChecker.class.getName());
        defaultLogger.addHandler(handler);

        // Set the random to a known state for testing
        ApiRateLimitChecker.setEntropy(entropy);

        // Default the expiration window to a small but measurable time for testing
        ApiRateLimitChecker.setExpirationWaitMillis(20);

        // Default the notification interval to a small but measurable time for testing
        ApiRateLimitChecker.setNotificationWaitMillis(60);

        ApiRateLimitChecker.resetLocalChecker();
    }

    @After
    public void tearDown() throws Exception {
        GitHubConfiguration.get().setEndpoints(new ArrayList<>());
    }

    private void setupStubs(List<RateLimit> scenarios) throws Exception {

        githubApi.stubFor(get(urlEqualTo("/meta"))
                .willReturn(aResponse().withStatus(200).withBody("{\"verifiable_password_authentication\": false}")));

        githubApi.stubFor(get(urlEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("{\"rate_limit_url\": \"https://localhost/placeholder/\"}")));

        scenarios.add(0, new RateLimit(1000, 1000, new Date(0)));
        String scenarioName = UUID.randomUUID().toString();
        for (int i = 0; i < scenarios.size(); i++) {
            String state = (i == 0) ? Scenario.STARTED : Integer.toString(i);
            String nextState = Integer.toString(i + 1);
            RateLimit scenarioResponse = scenarios.get(i);

            String limit = Integer.toString(scenarioResponse.limit);
            String remaining = Integer.toString(scenarioResponse.remaining);
            String reset = Long.toString(scenarioResponse.reset.toInstant().getEpochSecond());
            String body = "{"
                    + String.format(
                            " \"rate\": { \"limit\": %s, \"remaining\": %s, \"reset\": %s },", limit, remaining, reset)
                    + " \"resources\": {"
                    + String.format(
                            " \"core\": { \"limit\": %s, \"remaining\": %s, \"reset\": %s },", limit, remaining, reset)
                    + String.format(
                            " \"search\": { \"limit\": %s, \"remaining\": %s, \"reset\": %s },",
                            limit, remaining, reset)
                    + String.format(
                            " \"graphql\": { \"limit\": %s, \"remaining\": %s, \"reset\": %s },",
                            limit, remaining, reset)
                    + String.format(
                            " \"integration_manifest\": { \"limit\": %s, \"remaining\": %s, \"reset\": %s }",
                            limit, remaining, reset)
                    + " } }";
            ScenarioMappingBuilder scenario = get(urlEqualTo("/rate_limit"))
                    .inScenario(scenarioName)
                    .whenScenarioStateIs(state)
                    .willReturn(aResponse()
                            .withHeader("Content-Type", "application/json; charset=utf-8")
                            .withHeader("X-RateLimit-Limit", limit)
                            .withHeader("X-RateLimit-Remaining", remaining)
                            .withHeader("X-RateLimit-Reset", reset)
                            .withBody(body));
            if (i != scenarios.size() - 1) {
                scenario = scenario.willSetStateTo(nextState);
            }
            githubApi.stubFor(scenario);
        }

        github = Connector.connect("http://localhost:" + githubApi.port(), null);
        initialRequestCount = getRequestCount(githubApi);
        assertEquals(2, initialRequestCount);
    }

    @Test
    public void NoCheckerConfigured() throws Exception {
        // set up scenarios
        List<RateLimit> scenarios = new ArrayList<>();
        long now = System.currentTimeMillis();
        int limit = 5000;
        scenarios.add(new RateLimit(limit, 30, new Date(now - 10000)));
        scenarios.add(new RateLimit(limit, limit, new Date(now - 8000)));
        scenarios.add(new RateLimit(limit, 20, new Date(now - 6000)));
        scenarios.add(new RateLimit(limit, limit, new Date(now - 4000)));
        scenarios.add(new RateLimit(limit, 10, new Date(now - 2000)));
        scenarios.add(new RateLimit(limit, limit, new Date(now)));
        setupStubs(scenarios);

        GitHubConfiguration.get().setApiRateLimitChecker(ApiRateLimitChecker.ThrottleForNormalize);
        github.getMeta();
        ApiRateLimitChecker.resetLocalChecker();

        GitHubConfiguration.get().setApiRateLimitChecker(ApiRateLimitChecker.ThrottleOnOver);
        github.getMeta();
        ApiRateLimitChecker.resetLocalChecker();

        GitHubConfiguration.get().setApiRateLimitChecker(ApiRateLimitChecker.NoThrottle);
        github.getMeta();

        assertEquals(3, countOfOutputLinesContaining("LocalChecker for rate limit was not set for this thread."));
        assertEquals(3, countOfOutputLinesContaining("with API URL 'https://api.github.com'"));
        assertEquals(3, countOfOutputLines(m -> m.matches(".*[sS]leeping.*")));
        // github rate_limit endpoint should be contacted for ThrottleOnOver
        // rateLimit()
        // getRateLimit()
        // meta endpoint

        // Do not use NoThrottle when apiUrl is not known
        assertEquals(1, countOfOutputLinesContaining("ThrottleOnOver will be used instead"));

        assertEquals(initialRequestCount + 9, getRequestCount(githubApi));
    }

    @Test
    public void NoCheckerConfiguredWithEndpoint() throws Exception {
        // set up scenarios
        List<RateLimit> scenarios = new ArrayList<>();
        long now = System.currentTimeMillis();
        int limit = 5000;
        scenarios.add(new RateLimit(limit, 30, new Date(now - 10000)));
        scenarios.add(new RateLimit(limit, limit, new Date(now - 8000)));
        scenarios.add(new RateLimit(limit, 20, new Date(now - 6000)));
        scenarios.add(new RateLimit(limit, limit, new Date(now - 4000)));
        scenarios.add(new RateLimit(limit, 10, new Date(now - 2000)));
        scenarios.add(new RateLimit(limit, limit, new Date(now)));
        setupStubs(scenarios);

        List<Endpoint> endpoints = new ArrayList<>();
        endpoints.add(new Endpoint("https://git.company.com/api/v3", "Company GitHub"));
        endpoints.add(new Endpoint("https://git2.company.com/api/v3", "Company GitHub 2"));
        GitHubConfiguration.get().setEndpoints(endpoints);

        GitHubConfiguration.get().setApiRateLimitChecker(ApiRateLimitChecker.NoThrottle);
        github.getMeta();

        assertEquals(1, countOfOutputLinesContaining("LocalChecker for rate limit was not set for this thread."));
        assertEquals(1, countOfOutputLinesContaining("with API URL 'https://git.company.com/api/v3'"));
        // ThrottleOnOver should not be used for NoThrottle since it is not the public GitHub endpoint
        assertEquals(0, countOfOutputLinesContaining("ThrottleOnOver will be used instead"));

        assertEquals(initialRequestCount + 2, getRequestCount(githubApi));
    }

    /**
     * Verify that the throttle does not happen in OnOver throttle when none of the quota has been
     * used
     *
     * @author Julian V. Modesto
     */
    @Test
    public void ThrottleOnOverTestWithQuota() throws Exception {
        GitHubConfiguration.get().setApiRateLimitChecker(ApiRateLimitChecker.ThrottleOnOver);

        // set up scenarios
        List<RateLimit> scenarios = new ArrayList<>();
        int limit = 5000;
        scenarios.add(new RateLimit(limit, limit, soon));
        setupStubs(scenarios);

        ApiRateLimitChecker.configureThreadLocalChecker(listener, github);

        // Given a full rate limit quota, then we expect no throttling
        // Also only 1 call to get rate limit, since rate limit record is valid for a while
        for (int i = 0; i < 100; i++) {
            github.getMeta();
        }

        assertEquals(0, countOfOutputLinesContaining("Sleeping"));
        // Rate limit record remains valid so only one rate limit request made
        assertEquals(initialRequestCount + 101, getRequestCount(githubApi));
    }

    /**
     * Verify when the throttle is not happening in "OnNormalize" throttle when none of the quota has
     * been used
     *
     * @author Julian V. Modesto
     */
    @Test
    public void ThrottleOnNormalizeTestWithQuota() throws Exception {
        GitHubConfiguration.get().setApiRateLimitChecker(ApiRateLimitChecker.ThrottleForNormalize);

        // set up scenarios
        List<RateLimit> scenarios = new ArrayList<>();
        int limit = 5000;
        scenarios.add(new RateLimit(limit, limit, soon));
        setupStubs(scenarios);

        ApiRateLimitChecker.configureThreadLocalChecker(listener, github);

        // Given a full rate limit quota, then we expect no throttling
        for (int i = 0; i < 100; i++) {
            github.getMeta();
        }

        assertEquals(0, countOfOutputLinesContaining("Sleeping"));
        // Rate limit record remains valid so only one rate limit request made
        assertEquals(initialRequestCount + 101, getRequestCount(githubApi));
    }

    /**
     * Verify that "NoThrottle" does not contact the GitHub api nor output any logs
     *
     * @author Marc Salles Navarro
     */
    @Test
    public void NoThrottleTestShouldNotThrottle() throws Exception {
        // set up scenarios
        List<RateLimit> scenarios = new ArrayList<>();
        int limit = 5000;
        // Have so little quota it should always fire.
        scenarios.add(new RateLimit(limit, 10, soon));
        setupStubs(scenarios);
        GitHubConfiguration.get().setApiRateLimitChecker(ApiRateLimitChecker.NoThrottle);

        ApiRateLimitChecker.configureThreadLocalChecker(listener, github);

        for (int i = 0; i < 100; i++) {
            github.getMeta();
        }

        // there should be no output
        assertEquals(0, countOfOutputLinesContaining("ThrottleOnOver will be used instead"));
        assertEquals(0, countOfOutputLines(m -> m.matches(".*[sS]leeping.*")));
        // github rate_limit endpoint should be contacted once
        assertEquals(initialRequestCount + 101, getRequestCount(githubApi));
    }

    /**
     * Verify that "NoThrottle" does not contact the GitHub api nor output any logs
     *
     * @author Marc Salles Navarro
     */
    @Test
    public void NoThrottleTestShouldNotThrottle404() throws Exception {

        setupStubs(new ArrayList<>());
        GHRateLimit.Record initial = github.lastRateLimit().getCore();
        assertEquals(2, getRequestCount(githubApi));
        GitHubConfiguration.get().setApiRateLimitChecker(ApiRateLimitChecker.NoThrottle);

        // Return 404 for /rate_limit
        githubApi.stubFor(get(urlEqualTo("/rate_limit")).willReturn(aResponse().withStatus(404)));

        ApiRateLimitChecker.configureThreadLocalChecker(listener, github);

        github.getMeta();

        // The core should be unknown, but different from initial
        assertTrue(github.rateLimit().getCore() instanceof GHRateLimit.UnknownLimitRecord);
        assertNotEquals(initial, github.rateLimit().getCore());

        // there should be no output
        assertEquals(0, countOfOutputLinesContaining("ThrottleOnOver will be used instead"));
        assertEquals(0, countOfOutputLines(m -> m.matches(".*[sS]leeping.*")));
        // github rate_limit endpoint should be contacted once + meta
        assertEquals(initialRequestCount + 2, getRequestCount(githubApi));
    }

    /**
     * Verify that "NoThrottle" falls back to "ThrottleOnOver" if using GitHub.com
     *
     * @author Marc Salles Navarro
     */
    @Test
    public void NoThrottleTestShouldFallbackToThrottleOnOverForGitHubDotCom() throws Exception {
        GitHubConfiguration.get().setApiRateLimitChecker(ApiRateLimitChecker.ThrottleOnOver);

        // set up scenarios
        List<RateLimit> scenarios = new ArrayList<>();
        int limit = 5000;
        int buffer = ApiRateLimitChecker.calculateBuffer(limit);
        long now = System.currentTimeMillis();
        scenarios.add(new RateLimit(limit, buffer - 1, new Date(now)));
        scenarios.add(new RateLimit(limit, limit, soon));
        setupStubs(scenarios);
        GitHubConfiguration.get().setApiRateLimitChecker(ApiRateLimitChecker.NoThrottle);

        GitHub spy = Mockito.spy(github);
        Mockito.when(spy.getApiUrl()).thenReturn(GitHubServerConfig.GITHUB_URL);

        ApiRateLimitChecker.configureThreadLocalChecker(listener, spy);

        spy.getMeta();

        assertEquals(1, countOfOutputLinesContaining("ThrottleOnOver will be used instead"));
        assertEquals(1, countOfOutputLines(m -> m.matches(".*[sS]leeping.*")));
        // github rate_limit endpoint should be contacted by ThrottleOnOver + meta
        assertEquals(initialRequestCount + 3, getRequestCount(githubApi));
    }

    /**
     * Verify exactly when the throttle is occurring in "OnOver"
     *
     * @author Julian V. Modesto
     */
    @Test
    public void ThrottleOnOverTest() throws Exception {
        GitHubConfiguration.get().setApiRateLimitChecker(ApiRateLimitChecker.ThrottleOnOver);

        // set up scenarios
        List<RateLimit> scenarios = new ArrayList<>();
        // set remaining quota to over buffer to trigger throttle
        int limit = 5000;
        int buffer = ApiRateLimitChecker.calculateBuffer(limit);
        int expectedNumThrottles = 10;

        // This is going to not throttle for 10 values and then throttle the next 20
        for (int i = -10; i <= expectedNumThrottles; i++) {
            scenarios.add(new RateLimit(limit, buffer - i, soon));
        }

        // finally, stop throttling by restoring quota
        scenarios.add(new RateLimit(limit, limit, new Date(soon.getTime() + 2000)));
        setupStubs(scenarios);
        ApiRateLimitChecker.configureThreadLocalChecker(listener, github);

        ApiRateLimitChecker.LocalChecker currentChecker = ApiRateLimitChecker.getLocalChecker();

        // check rate limit to hit the first 11 scenarios because the throttle (add more here)
        // does not happen until under buffer
        for (int i = 0; i < 11; i++) {
            assertFalse(currentChecker.checkRateLimit(github.getRateLimit().getCore(), 0));
        }

        // should be no output
        assertEquals(0, countOfOutputLines(m -> m.matches(".*[sS]leeping.*")));

        assertEquals(initialRequestCount + 11, getRequestCount(githubApi));

        // check rate limit to hit the next 9 scenarios
        for (int i = 0; i < 10; i++) {
            assertTrue(currentChecker.checkRateLimit(github.getRateLimit().getCore(), i));
        }
        // This simulates the waiting until refreshed
        currentChecker.resetExpiration();
        assertFalse(currentChecker.checkRateLimit(github.getRateLimit().getCore(), 10));

        // output for all the throttled scenarios. Sleeps normally on the first and then the `notify`
        // hits the next 9
        assertEquals(1, countOfOutputLinesContaining("Sleeping until reset."));
        assertEquals(expectedNumThrottles - 1, countOfOutputLinesContaining("Still sleeping"));
        // Refresh functionality was removed
        assertEquals(0, countOfOutputLinesContaining("refreshed"));
        assertEquals(initialRequestCount + 22, getRequestCount(githubApi));

        // Make sure no new output
        github.getMeta();
        assertEquals(1, countOfOutputLinesContaining("Sleeping until reset"));
        assertEquals(expectedNumThrottles - 1, countOfOutputLinesContaining("Still sleeping"));
        // Refresh functionality was removed
        assertEquals(0, countOfOutputLinesContaining("refreshed"));
        // Only new request should be to meta, the existing rate limit is valid
        assertEquals(initialRequestCount + 23, getRequestCount(githubApi));
    }

    /**
     * Verify the bounds of the throttle for "Normalize"
     *
     * @author Julian V. Modesto
     */
    @Test
    public void ThrottleForNormalizeTestWithinIdeal() throws Exception {
        GitHubConfiguration.get().setApiRateLimitChecker(ApiRateLimitChecker.ThrottleForNormalize);

        List<RateLimit> scenarios = new ArrayList<>();
        int limit = 5000;
        int buffer = ApiRateLimitChecker.calculateBuffer(limit);

        // Approximate the ideal here
        int approximateIdeal = 4000;

        // NOTE: The behavior below is no longer interesting.
        // All of the value adjustments do not matter.
        // The checker no longer rechecks values  until after the expiration time, no matter what.
        // Changes before then will be ignored.

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

        ApiRateLimitChecker.configureThreadLocalChecker(listener, github);

        // First check will not say under budget (add counts)
        github.getMeta();

        assertEquals(4, getRequestCount(githubApi));
        // Feature removed, no output for under budget
        assertEquals(0, countOfOutputLinesContaining("under budget"));
        assertFalse(handler.getView().stream().anyMatch(m -> m.getMessage().contains("Sleeping")));

        ApiRateLimitChecker.LocalChecker currentChecker = ApiRateLimitChecker.getLocalChecker();

        // check rate limit to hit the next 6 scenarios
        for (int i = 0; i < 6; i++) {
            assertTrue(currentChecker.checkRateLimit(github.getRateLimit().getCore(), i));
        }

        assertEquals(initialRequestCount + 5, handler.getView().size());

        // This simulates the waiting until refreshed
        currentChecker.resetExpiration();
        assertFalse(currentChecker.checkRateLimit(github.getRateLimit().getCore(), 9));

        assertEquals(initialRequestCount + 9, getRequestCount(githubApi));
        // Functionality removed
        assertEquals(0, countOfOutputLinesContaining("rechecking"));
        assertEquals(5, countOfOutputLinesContaining("Still sleeping"));
        assertEquals(1, countOfOutputLinesContaining("Sleeping for"));
        // Functionality removed
        assertEquals(0, countOfOutputLinesContaining("under budget"));
        assertEquals(1, countOfOutputLinesContaining("over budget"));
        assertEquals(1, countOfOutputLinesContaining("Jenkins is attempting to evenly distribute GitHub API requests"));

        // The last scenario will trigger back to under budget with a full limit but no new messages
        assertEquals(initialRequestCount + 5, handler.getView().size());
    }

    /**
     * Verify OnNormal throttling when past the buffer
     *
     * @author Julian V. Modesto
     */
    @Test
    public void NormalizeThrottleWithBurnedBuffer() throws Exception {
        GitHubConfiguration.get().setApiRateLimitChecker(ApiRateLimitChecker.ThrottleForNormalize);

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

        ApiRateLimitChecker.configureThreadLocalChecker(listener, github);

        // Run check against API limit
        ApiRateLimitChecker.LocalChecker currentChecker = ApiRateLimitChecker.getLocalChecker();

        assertTrue(currentChecker.checkRateLimit(github.getRateLimit().getCore(), 0));
        assertTrue(currentChecker.checkRateLimit(github.getRateLimit().getCore(), 1));
        assertTrue(currentChecker.checkRateLimit(github.getRateLimit().getCore(), 2));
        assertTrue(currentChecker.checkRateLimit(github.getRateLimit().getCore(), 3));
        assertTrue(currentChecker.checkRateLimit(github.getRateLimit().getCore(), 4));
        assertTrue(currentChecker.checkRateLimit(github.getRateLimit().getCore(), 5));

        // Expect a triggered throttle for normalize
        // GitHubRateLimitChecker add 1 second to notification loop, this hides the entropy value
        assertEquals(
                3,
                countOfOutputLinesContaining(
                        "Current quota for Github API usage has 0 remaining (250 over budget). Next quota of 5000 due now. Sleeping for 1 sec."));
        assertEquals(
                4,
                countOfOutputLinesContaining(
                        "Jenkins is attempting to evenly distribute GitHub API requests. To configure a different rate limiting strategy, such as having Jenkins restrict GitHub API requests only when near or above the GitHub rate limit, go to \"GitHub API usage\" under \"Configure System\" in the Jenkins settings."));
        assertEquals(4, countOfOutputLinesContaining("Sleeping"));
        assertEquals(2, countOfOutputLinesContaining("now only 59 min remaining"));
        // Refresh functionality was removed
        assertEquals(0, countOfOutputLinesContaining("refreshed"));
        assertEquals(initialRequestCount + 6, getRequestCount(githubApi));
    }

    /**
     * Verify throttle in "OnOver" and the wait happens for the correct amount of time
     *
     * @author Alex Taylor
     */
    @Test
    public void OnOverThrottleTimingRateLimitCheck() throws Exception {
        GitHubConfiguration.get().setApiRateLimitChecker(ApiRateLimitChecker.ThrottleOnOver);

        // Longer timings that test defaults for more consistent measurements.
        ApiRateLimitChecker.setExpirationWaitMillis(60);
        ApiRateLimitChecker.setNotificationWaitMillis(200);

        // set up scenarios
        List<RateLimit> scenarios = new ArrayList<>();
        // set remaining quota to over buffer to trigger throttle
        int limit = 5000;
        int buffer = ApiRateLimitChecker.calculateBuffer(limit);
        int expectedNumThrottles = 5;

        // This is going to not throttle for 5 values and then throttle the next 5
        for (int i = -5; i <= expectedNumThrottles; i++) {
            scenarios.add(new RateLimit(limit, buffer - i, soon));
        }

        // finally, stop throttling by restoring quota
        scenarios.add(new RateLimit(limit, limit, new Date(soon.getTime() + 2000)));
        setupStubs(scenarios);

        ApiRateLimitChecker.configureThreadLocalChecker(listener, github);

        long start = System.currentTimeMillis();

        // check rate limit to hit the first 10 scenarios
        for (int i = 0; i < 6; i++) {
            github.getRateLimit();
            // calls rateLimit() for first loop so we have to getRateLimit() for each loop
            github.getMeta();
        }

        // (rate_limit + meta) x 6
        assertEquals(initialRequestCount + 12, getRequestCount(githubApi));

        // should be no output
        assertEquals(0, countOfOutputLinesContaining("Sleeping"));

        ApiRateLimitChecker.LocalChecker currentChecker = ApiRateLimitChecker.getLocalChecker();

        // check rate limit to hit the next 5 scenarios
        for (int i = 0; i < 5; i++) {
            assertTrue(currentChecker.checkRateLimit(github.getRateLimit().getCore(), i));
        }
        // This simulates the waiting until refreshed
        currentChecker.resetExpiration();
        assertFalse(currentChecker.checkRateLimit(github.getRateLimit().getCore(), 5));

        assertEquals(initialRequestCount + 18, getRequestCount(githubApi));

        // want to make sure that the 5 API checks (the last one is resetting) are taking at least 1000
        // MS
        assertTrue((System.currentTimeMillis() - start) > 1000);

        // output for all the throttled scenarios. Again the first will show the remaining and then the
        // rest will just sleep
        assertEquals(1, countOfOutputLinesContaining("Sleeping"));
        assertEquals(expectedNumThrottles - 1, countOfOutputLinesContaining("Still sleeping"));

        // no new output
        github.getMeta();
        // No new rate_limit request should be made, the existing rate limit is valid
        assertEquals(initialRequestCount + 19, getRequestCount(githubApi));
        assertEquals(1, countOfOutputLinesContaining("Sleeping"));
        assertEquals(expectedNumThrottles - 1, countOfOutputLinesContaining("Still sleeping"));
    }

    /**
     * Verify the "OnNormalize" throttle and wait is happening for the correct amount of time
     *
     * @author Alex Taylor
     */
    @Test
    public void NormalizeThrottleTimingRateLimitCheck() throws Exception {
        GitHubConfiguration.get().setApiRateLimitChecker(ApiRateLimitChecker.ThrottleForNormalize);

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
        ApiRateLimitChecker.configureThreadLocalChecker(listener, github);

        github.getMeta();

        // start timing
        long start = System.currentTimeMillis();

        // Run check
        ApiRateLimitChecker.LocalChecker currentChecker = ApiRateLimitChecker.getLocalChecker();

        assertTrue(currentChecker.checkRateLimit(github.getRateLimit().getCore(), 0));
        assertTrue(currentChecker.checkRateLimit(github.getRateLimit().getCore(), 1));
        assertTrue(currentChecker.checkRateLimit(github.getRateLimit().getCore(), 2));
        // This simulates the waiting until refreshed
        currentChecker.resetExpiration();
        assertFalse(currentChecker.checkRateLimit(github.getRateLimit().getCore(), 3));

        assertEquals(initialRequestCount + 6, getRequestCount(githubApi));

        // Want to make sure that the 3 API checks are taking at least 600 MS
        assertTrue((System.currentTimeMillis() - start) > 600);
        // Expect a triggered throttle for normalize
        assertEquals(1, countOfOutputLinesContaining("Sleeping"));
        // Expect a wait until rate limit
        assertEquals(2, countOfOutputLinesContaining("Still sleeping"));
        // Refresh functionality was removed
        assertEquals(0, countOfOutputLinesContaining("refreshed"));
    }

    /**
     * Verify the throttle is happening for the "OnNormalize" and proves the ideal "limit" changes
     * correctly with time
     *
     * @author Alex Taylor
     */
    @Test
    public void NormalizeExpectedIdealOverTime() throws Exception {
        GitHubConfiguration.get().setApiRateLimitChecker(ApiRateLimitChecker.ThrottleForNormalize);

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
            scenarios.add(
                    new RateLimit(limit, morePreciseIdeal[i], new Date(start + TimeUnit.MINUTES.toMillis(i * 15))));
        }
        /*
         * With the limit at 400: the burst will be limit/10 and buffer will be limit/20
         */
        limit = 400;
        morePreciseIdeal = new int[] {20, 104, 189, 274};

        // deadline set for those times as well
        for (int i = 0; i < 4; i++) {
            scenarios.add(
                    new RateLimit(limit, morePreciseIdeal[i], new Date(start + TimeUnit.MINUTES.toMillis(i * 15))));
        }
        /*
         * With the limit at 1000: the burst will be limit/5 and buffer will be 15
         */
        limit = 200;
        morePreciseIdeal = new int[] {15, 56, 97, 138};

        // deadline set for those times as well
        for (int i = 0; i < 4; i++) {
            scenarios.add(
                    new RateLimit(limit, morePreciseIdeal[i], new Date(start + TimeUnit.MINUTES.toMillis(i * 15))));
        }

        setupStubs(scenarios);
        ApiRateLimitChecker.configureThreadLocalChecker(listener, github);

        for (int i = 0; i < 12; i++) {
            if (i > 1) {
                github.getRateLimit();
            }
            // calls rateLimit() for first loop so we have to getRateLimit() for each loop
            github.getMeta();
        }

        // rate_limit + meta x 12
        assertEquals(initialRequestCount + 24, getRequestCount(githubApi));

        // Expect a triggered throttle for normalize, feature removed
        assertEquals(0, countOfOutputLinesContaining("Current quota"));
        // Making sure the budgets are correct, feature removed
        assertEquals(0, countOfOutputLinesContaining("0 under budget"));
        // no occurrences of sleeping
        assertEquals(0, countOfOutputLines(m -> m.matches(".*[sS]leeping.*")));
    }

    /**
     * Verify when the throttle is happening for the "OnOver" and prove the current "limit" does not
     * change the same way as Normalize
     *
     * @author Alex Taylor
     */
    @Test
    public void OnOverExpectedIdealOverTime() throws Exception {
        GitHubConfiguration.get().setApiRateLimitChecker(ApiRateLimitChecker.ThrottleOnOver);

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
            scenarios.add(
                    new RateLimit(limit, morePreciseIdeal[i], new Date(start + TimeUnit.MINUTES.toMillis((i) * 15))));
        }

        // Refresh rate limit
        scenarios.add(new RateLimit(limit, limit, soon));
        setupStubs(scenarios);

        ApiRateLimitChecker.configureThreadLocalChecker(listener, github);

        // Run check a few times to ensure we don't get stuck
        for (int i = 0; i < 5; i++) {
            if (i > 1) {
                github.getRateLimit();
            }
            // calls rateLimit() for first loop so we have to getRateLimit() for each loop
            github.getMeta();
        }

        assertEquals(12, getRequestCount(githubApi));

        // Expect this to only get throttled  when we are over the buffer limit
        assertEquals(1, countOfOutputLinesContaining("Current quota"));
        // Making sure the budget messages are correct
        assertEquals(1, countOfOutputLinesContaining("1 over budget"));
        assertEquals(
                1,
                countOfOutputLinesContaining(
                        "Jenkins is restricting GitHub API requests only when near or above the rate limit. To configure a different rate limiting strategy, such as having Jenkins attempt to evenly distribute GitHub API requests, go to \"GitHub API usage\" under \"Configure System\" in the Jenkins settings."));
    }

    /**
     * Verify the expected reset happens and notifications happen on time in the logs for Normalize
     *
     * @author Alex Taylor
     */
    @Test
    public void ExpectedResetTimingNormalize() throws Exception {
        GitHubConfiguration.get().setApiRateLimitChecker(ApiRateLimitChecker.ThrottleForNormalize);

        // Use a longer notification interval to make the test produce stable output
        // The GitHubRateLimitChecker adds a one second sleep to each notification loop
        ApiRateLimitChecker.setNotificationWaitMillis(1000);

        // Set up scenarios
        List<RateLimit> scenarios = new ArrayList<>();
        int limit = 1000;
        int buffer = 50;
        // Giving a bit of time to make sure the setup happens on time
        long start = System.currentTimeMillis() + 7000;
        scenarios.add(new RateLimit(limit, limit, new Date(start)));

        for (int i = 0; i <= 3; i++) {
            scenarios.add(new RateLimit(limit, buffer - 5, new Date(start)));
        }
        // Refresh rate limit
        scenarios.add(new RateLimit(limit, limit, soon));
        setupStubs(scenarios);

        ApiRateLimitChecker.configureThreadLocalChecker(listener, github);

        // First server warm up
        github.getRateLimit();
        github.getRateLimit();

        while (System.currentTimeMillis() + 6000 < start) {
            Thread.sleep(25);
        }

        github.getMeta();

        // Expect a triggered throttle for normalize
        assertEquals(2, countOfOutputLinesContaining("Current quota"));
        assertEquals(2, countOfOutputLinesContaining("Still sleeping"));
        assertEquals(initialRequestCount + 7, getRequestCount(githubApi));
    }

    /**
     * Verify the expected reset happens and notifications happen on time in the logs for OnOver
     *
     * @author Alex Taylor
     */
    @Test
    public void ExpectedResetTimingOnOver() throws Exception {
        GitHubConfiguration.get().setApiRateLimitChecker(ApiRateLimitChecker.ThrottleOnOver);

        // Use a longer notification interval to make the test produce stable output
        // The GitHubRateLimitChecker adds a one second sleep to each notification loop
        ApiRateLimitChecker.setNotificationWaitMillis(1000);

        // Set up scenarios
        List<RateLimit> scenarios = new ArrayList<>();
        int limit = 1000;
        int buffer = 50;
        // Giving a bit of time to make sure the setup happens on time
        long start = System.currentTimeMillis() + 8000;
        scenarios.add(new RateLimit(limit, limit, new Date(start)));

        for (int i = 0; i <= 3; i++) {
            scenarios.add(new RateLimit(limit, buffer - 5, new Date(start)));
        }
        // Refresh rate limit
        scenarios.add(new RateLimit(limit, limit, soon));
        setupStubs(scenarios);
        // First server warm up
        github.getRateLimit();
        github.getRateLimit();

        ApiRateLimitChecker.configureThreadLocalChecker(listener, github);

        while (System.currentTimeMillis() + 6000 < start) {
            Thread.sleep(25);
        }

        github.getMeta();

        // This test exercises the case where an expired rate limit is returned after the
        // time where it should have expired. The checker should continue to wait and notify
        // at the same rate not faster
        assertEquals(2, countOfOutputLinesContaining("Current quota"));
        assertEquals(2, countOfOutputLinesContaining("Still sleeping"));
        assertEquals(initialRequestCount + 7, getRequestCount(githubApi));
    }
}
