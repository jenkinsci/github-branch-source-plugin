package org.jenkinsci.plugins.github_branch_source;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.io.File;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import okhttp3.Cache;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.HttpException;

@WithJenkins
class ConnectorTest {

    private static final String REPO_PATH = "/repos/cloudbeers/yolo";
    private static final String REPO_JSON =
            "{\"id\": 1, \"name\": \"yolo\", \"full_name\": \"cloudbeers/yolo\", \"owner\": {\"login\": \"cloudbeers\"}}";

    @RegisterExtension
    static WireMockExtension githubApi = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void requestsGoThroughTheClientInterceptors() throws Exception {
        githubApi.stubFor(get(urlEqualTo(REPO_PATH)).willReturn(okJson(REPO_JSON)));
        List<String> application = new CopyOnWriteArrayList<>();
        List<String> network = new CopyOnWriteArrayList<>();
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    application.add(chain.request().method() + " "
                            + chain.request().url().encodedPath());
                    return chain.proceed(chain.request());
                })
                .addNetworkInterceptor(chain -> {
                    Response response = chain.proceed(chain.request());
                    network.add(chain.request().method() + " "
                            + chain.request().url().encodedPath() + " " + response.code());
                    return response;
                })
                .build();

        GitHub github =
                Connector.createGitHubBuilder(githubApi.baseUrl(), null, client).build();
        assertEquals("yolo", github.getRepository("cloudbeers/yolo").getName());

        assertThat(application, hasItem("GET " + REPO_PATH));
        assertThat(network, hasItem("GET " + REPO_PATH + " 200"));
    }

    @Test
    void cachedResponseIsRevalidatedWithETag(@TempDir File cacheDir) throws Exception {
        githubApi.stubFor(get(urlEqualTo(REPO_PATH))
                .withHeader("If-None-Match", absent())
                .willReturn(okJson(REPO_JSON).withHeader("ETag", "\"v1\"")));
        githubApi.stubFor(get(urlEqualTo(REPO_PATH))
                .withHeader("If-None-Match", equalTo("\"v1\""))
                .willReturn(aResponse().withStatus(304).withHeader("ETag", "\"v1\"")));
        Cache cache = new Cache(cacheDir, 1024 * 1024);
        try {
            GitHub github = Connector.createGitHubBuilder(githubApi.baseUrl(), cache, new OkHttpClient())
                    .build();

            assertEquals("yolo", github.getRepository("cloudbeers/yolo").getName());
            assertEquals("yolo", github.getRepository("cloudbeers/yolo").getName());

            // Every request asks GitHub (max-age=0); the second one is conditional and is answered from the cache.
            githubApi.verify(
                    2, getRequestedFor(urlEqualTo(REPO_PATH)).withHeader("Cache-Control", equalTo("max-age=0")));
            githubApi.verify(1, getRequestedFor(urlEqualTo(REPO_PATH)).withHeader("If-None-Match", equalTo("\"v1\"")));
            assertEquals(1, cache.hitCount());
        } finally {
            cache.close();
        }
    }

    @Test
    void rateLimitExceededReportsTheRateLimitHeaders() throws Exception {
        long reset = System.currentTimeMillis() / 1000 + 3600;
        githubApi.stubFor(get(urlEqualTo(REPO_PATH))
                .willReturn(aResponse()
                        .withStatus(403)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("X-RateLimit-Limit", "5000")
                        .withHeader("X-RateLimit-Remaining", "0")
                        .withHeader("X-RateLimit-Reset", Long.toString(reset))
                        .withBody("{\"message\": \"API rate limit exceeded\"}")));

        GitHub github = Connector.createGitHubBuilder(githubApi.baseUrl()).build();
        // github-api reports the handler's exception as the cause of the request's HttpException.
        HttpException thrown = assertThrows(HttpException.class, () -> github.getRepository("cloudbeers/yolo"));
        RateLimitExceededException e = assertInstanceOf(RateLimitExceededException.class, thrown.getCause());

        assertEquals(403, thrown.getResponseCode());
        assertEquals(5000, e.getLimit());
        assertEquals(0, e.getRemaining());
        assertEquals(reset, e.getReset());
    }
}
