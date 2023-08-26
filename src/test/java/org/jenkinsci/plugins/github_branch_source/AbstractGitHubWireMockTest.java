package org.jenkinsci.plugins.github_branch_source;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;

import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.common.SingleRootFileSource;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.Response;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import java.io.File;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;

/** @author Liam Newman */
public abstract class AbstractGitHubWireMockTest {

    // By default the wiremock tests will run without proxy
    // The tests will use only the stubbed data and will fail if requests are made for missing data.
    // You can use the proxy while writing and debugging tests.
    private static final boolean useProxy =
            !System.getProperty("test.github.useProxy", "false").equals("false");

    @ClassRule
    public static JenkinsRule r = new JenkinsRule();

    public static WireMockRuleFactory factory = new WireMockRuleFactory();

    @Rule
    public WireMockRule githubRaw =
            factory.getRule(WireMockConfiguration.options().dynamicPort().usingFilesUnderClasspath("raw"));

    @Rule
    public WireMockRule githubApi = factory.getRule(WireMockConfiguration.options()
            .dynamicPort()
            .usingFilesUnderClasspath("api")
            .extensions(new ResponseTransformer() {
                @Override
                public Response transform(Request request, Response response, FileSource files, Parameters parameters) {
                    if ("application/json"
                            .equals(response.getHeaders().getContentTypeHeader().mimeTypePart())) {
                        return Response.Builder.like(response)
                                .but()
                                .body(response.getBodyAsString()
                                        .replace(
                                                "https://api.github.com/", "http://localhost:" + githubApi.port() + "/")
                                        .replace(
                                                "https://raw.githubusercontent.com/",
                                                "http://localhost:" + githubRaw.port() + "/"))
                                .build();
                    }
                    return response;
                }

                @Override
                public String getName() {
                    return "url-rewrite";
                }
            }));

    @Before
    public void prepareMockGitHub() {
        prepareMockGitHubFileMappings();

        if (useProxy) {
            githubApi.stubFor(get(urlMatching(".*"))
                    .atPriority(10)
                    .willReturn(aResponse().proxiedFrom("https://api.github.com/")));
            githubRaw.stubFor(get(urlMatching(".*"))
                    .atPriority(10)
                    .willReturn(aResponse().proxiedFrom("https://raw.githubusercontent.com/")));
        }
    }

    void prepareMockGitHubFileMappings() {
        new File("src/test/resources/api/mappings").mkdirs();
        new File("src/test/resources/api/__files").mkdirs();
        new File("src/test/resources/raw/mappings").mkdirs();
        new File("src/test/resources/raw/__files").mkdirs();
        githubApi.enableRecordMappings(
                new SingleRootFileSource("src/test/resources/api/mappings"),
                new SingleRootFileSource("src/test/resources/api/__files"));
        githubRaw.enableRecordMappings(
                new SingleRootFileSource("src/test/resources/raw/mappings"),
                new SingleRootFileSource("src/test/resources/raw/__files"));
    }
}
