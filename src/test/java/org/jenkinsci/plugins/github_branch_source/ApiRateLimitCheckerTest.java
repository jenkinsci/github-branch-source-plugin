package org.jenkinsci.plugins.github_branch_source;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.common.SingleRootFileSource;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.http.Response;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import hudson.util.LogTaskListener;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.github.GitHub;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

public class ApiRateLimitCheckerTest {
    /**
     * All tests in this class only use Jenkins for the extensions
     */
    @ClassRule
    public static JenkinsRule r = new JenkinsRule();

    public static WireMockRuleFactory factory = new WireMockRuleFactory();

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

    @Before
    public void prepareMockGitHub() throws Exception {
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
    }

    @Test(timeout=120000)
    public void ThrottleOnOverTest() throws Exception {
        githubApi.stubFor(
                get(urlEqualTo("/rate_limit"))
                        .inScenario("API Rate Limit Throttle on Over")
                        .whenScenarioStateIs(Scenario.STARTED)
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json; charset=utf-8")
                                        .withHeader("Accept-Encoding", "gzip,deflate")
                                        .withBody("{ \"rate\": { \"limit\": 100, \"remaining\": 8, \"reset\": 1 } }"))
                        .willSetStateTo("API Rate Limit Depleted"));

        githubApi.stubFor(
                get(urlEqualTo("/rate_limit"))
                        .inScenario("API Rate Limit Throttle on Over")
                        .whenScenarioStateIs("API Rate Limit Depleted")
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json; charset=utf-8")
                                        .withHeader("Accept-Encoding", "gzip,deflate")
                                        .withBody("{ \"rate\": { \"limit\": 100, \"remaining\": 100, \"reset\": 1000 } }"))
                        .willSetStateTo("API Rate Limit Refreshed"));

        githubApi.stubFor(
                get(urlEqualTo("/rate_limit"))
                        .inScenario("API Rate Limit Throttle on Over")
                        .whenScenarioStateIs("API Rate Limit Refreshed")
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json; charset=utf-8")
                                        .withHeader("Accept-Encoding", "gzip,deflate")
                                        .withBody("{ \"rate\": { \"limit\": 100, \"remaining\": 99, \"reset\": 1000 } }")));

        final Logger logger = Logger.getLogger(getClass().getName());
        final LogTaskListener listener = new LogTaskListener(logger, Level.INFO);
        final ApiRateLimitChecker apiRateLimitChecker = ApiRateLimitChecker.ThrottleOnOver;
        apiRateLimitChecker.setExpirationWaitMillis(1);
        apiRateLimitChecker.checkApiRateLimit(listener, github);
        apiRateLimitChecker.checkApiRateLimit(listener, github);
    }

    @Test(timeout=120000)
    public void ThrottleForNormalizeTest() throws Exception {
        githubApi.stubFor(
                get(urlEqualTo("/rate_limit"))
                        .inScenario("API Rate Limit Throttle for Normalize")
                        .whenScenarioStateIs(Scenario.STARTED)
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json; charset=utf-8")
                                        .withHeader("Accept-Encoding", "gzip,deflate")
                                        .withBody("{ \"rate\": { \"limit\": 100, \"remaining\": 8, \"reset\": 1 } }"))
                        .willSetStateTo("API Rate Limit Depleted"));

        githubApi.stubFor(
                get(urlEqualTo("/rate_limit"))
                        .inScenario("API Rate Limit Throttle for Normalize")
                        .whenScenarioStateIs("API Rate Limit Depleted")
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json; charset=utf-8")
                                        .withHeader("Accept-Encoding", "gzip,deflate")
                                        .withBody("{ \"rate\": { \"limit\": 100, \"remaining\": 100, \"reset\": 1000 } }"))
                        .willSetStateTo("API Rate Limit Refreshed"));

        githubApi.stubFor(
                get(urlEqualTo("/rate_limit"))
                        .inScenario("API Rate Limit Throttle on Over")
                        .whenScenarioStateIs("API Rate Limit Refreshed")
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json; charset=utf-8")
                                        .withHeader("Accept-Encoding", "gzip,deflate")
                                        .withBody("{ \"rate\": { \"limit\": 100, \"remaining\": 99, \"reset\": 1000 } }")));

        final Logger logger = Logger.getLogger(getClass().getName());
        final LogTaskListener listener = new LogTaskListener(logger, Level.INFO);
        final ApiRateLimitChecker apiRateLimitChecker = ApiRateLimitChecker.ThrottleForNormalize;
        apiRateLimitChecker.setExpirationWaitMillis(1);
        apiRateLimitChecker.checkApiRateLimit(listener, github);
        apiRateLimitChecker.checkApiRateLimit(listener, github);
    }
}