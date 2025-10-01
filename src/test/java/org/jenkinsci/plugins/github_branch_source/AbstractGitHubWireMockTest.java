package org.jenkinsci.plugins.github_branch_source;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.Response;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/** @author Liam Newman */
@WithJenkins
public abstract class AbstractGitHubWireMockTest {

    // By default the wiremock tests will run without proxy
    // The tests will use only the stubbed data and will fail if requests are made for missing data.
    // You can use the proxy while writing and debugging tests.
    private static final boolean USE_PROXY =
            !System.getProperty("test.github.useProxy", "false").equals("false");

    protected static JenkinsRule r;

    private static final WireMockExtensionFactory FACTORY = new WireMockExtensionFactory();

    @RegisterExtension
    protected final WireMockExtension githubRaw =
            FACTORY.getExtension(WireMockConfiguration.options().dynamicPort().usingFilesUnderClasspath("raw"));

    @RegisterExtension
    protected final WireMockExtension githubApi = FACTORY.getExtension(WireMockConfiguration.options()
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
                                                "https://api.github.com/",
                                                "http://localhost:" + githubApi.getPort() + "/")
                                        .replace(
                                                "https://raw.githubusercontent.com/",
                                                "http://localhost:" + githubRaw.getPort() + "/"))
                                .build();
                    }
                    return response;
                }

                @Override
                public String getName() {
                    return "url-rewrite";
                }
            }));

    @BeforeAll
    static void beforeAll(JenkinsRule rule) {
        r = rule;
    }

    @BeforeEach
    void beforeEach() throws Exception {
        if (USE_PROXY) {
            githubApi.stubFor(get(urlMatching(".*"))
                    .atPriority(10)
                    .willReturn(aResponse().proxiedFrom("https://api.github.com/")));
            githubRaw.stubFor(get(urlMatching(".*"))
                    .atPriority(10)
                    .willReturn(aResponse().proxiedFrom("https://raw.githubusercontent.com/")));
        }
    }
}
