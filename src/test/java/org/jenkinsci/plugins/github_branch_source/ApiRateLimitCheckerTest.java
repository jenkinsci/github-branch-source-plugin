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
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.github.GHRateLimit;
import org.kohsuke.github.GitHub;

import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

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

    private final ApiRateLimitChecker apiRateLimitCheckerThrottleOnOver = ApiRateLimitChecker.ThrottleOnOver;
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

        handler = new RingBufferLogHandler(20);
        final Logger logger = Logger.getLogger(getClass().getName());
        logger.addHandler(handler);
        listener = new LogTaskListener(logger, Level.INFO);
    }

    private void setupStubs(List<JsonRateLimit> scenarios) {
        githubApi.listAllStubMappings().getMappings().stream()
                .filter(x -> x.getScenarioName() != null && x.getScenarioName() == ("API Rate Limit"))
                .forEach(x -> githubApi.removeStubMapping(x));

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
                    .inScenario("API Rate Limit")
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

    private void setupThrottleOnOver() {
        List<JsonRateLimit> scenarios = new ArrayList<>();
        scenarios.add(new JsonRateLimit(100, 8, soon));
        scenarios.add(new JsonRateLimit(100, 100, soon));
        scenarios.add(new JsonRateLimit(100, 99, soon));
        setupStubs(scenarios);

        apiRateLimitCheckerThrottleOnOver.setEntropy(entropy);
        apiRateLimitCheckerThrottleOnOver.setExpirationWaitMillis(1);
        apiRateLimitCheckerThrottleOnOver.setNotificationWaitMillis(1);
    }

    private void setupThrottleForNormalize() {
        List<JsonRateLimit> scenarios = new ArrayList<>();
        scenarios.add(new JsonRateLimit(100, 50, soon));
        scenarios.add(new JsonRateLimit(100, 30, soon));
        scenarios.add(new JsonRateLimit(100, 10, soon));
        scenarios.add(new JsonRateLimit(100, 100, soon));
        setupStubs(scenarios);

        apiRateLimitCheckerThrottleForNormalize.setEntropy(entropy);
        apiRateLimitCheckerThrottleForNormalize.setExpirationWaitMillis(1);
        apiRateLimitCheckerThrottleForNormalize.setNotificationWaitMillis(1);
    }

    // TODO: remove timeout
    @Test(timeout = 2000)
    public void ThrottleOnOverTest() throws Exception {
        setupThrottleOnOver();

        for (int i = 0; i < 3; i++) {
            apiRateLimitCheckerThrottleOnOver.checkApiRateLimit(listener, github);
        }

        assertTrue(handler.getView().size() > 0);
        assertTrue(handler.getView().stream().anyMatch(m -> m.getMessage().contains("quota")));
    }

    @Test
    public void ThrottleForNormalizeTest() throws Exception {
        setupThrottleForNormalize();

        for (int i = 0; i < 3; i++) {
            apiRateLimitCheckerThrottleForNormalize.checkApiRateLimit(listener, github);
        }

        assertTrue(handler.getView().size() > 0);
        assertTrue(handler.getView().stream().anyMatch(m -> m.getMessage().contains("quota")));
    }
}