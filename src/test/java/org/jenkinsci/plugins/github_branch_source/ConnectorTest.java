package org.jenkinsci.plugins.github_branch_source;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import java.io.File;
import okhttp3.Cache;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Tests for the on-disk HTTP response cache lifecycle when unused connections are evicted from the
 * pool by {@link Connector.UnusedConnectionDestroyer}.
 */
public class ConnectorTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Rule
    public WireMockRule server =
            new WireMockRule(WireMockConfiguration.options().dynamicPort());

    @Test
    public void evictingConnectionClosesCacheButPreservesDirectoryForReuse() throws Exception {
        server.stubFor(get(urlEqualTo("/data"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("Cache-Control", "max-age=60")
                        .withHeader("ETag", "\"v1\"")
                        .withBody("{\"hello\":\"world\"}")));

        File cacheDir = new File(tmp.getRoot(), "response-cache");
        Cache cache = new Cache(cacheDir, 10L * 1024 * 1024);

        // Populate the cache with a real, cacheable response so its on-disk journal exists.
        OkHttpClient client = new OkHttpClient.Builder().cache(cache).build();
        Request request = new Request.Builder().url(server.baseUrl() + "/data").build();
        try (Response response = client.newCall(request).execute()) {
            response.body().string();
        }
        assertThat("the request should have populated the on-disk cache", cacheDir.isDirectory(), is(true));

        Connector.evictConnectionCache(cache, "test-connection");

        assertThat(
                "the cache directory must be preserved after eviction so the next connection can "
                        + "reuse the stored ETags for conditional requests",
                cacheDir.isDirectory(),
                is(true));

        // The cache must have been closed, so further use of the evicted instance fails.
        assertThrows(IllegalStateException.class, cache::flush);

        // The stored entry survives on disk: a fresh cache over the same directory still holds it.
        Cache reopened = new Cache(cacheDir, 10L * 1024 * 1024);
        try {
            assertThat("the cached response should survive eviction", reopened.size(), greaterThan(0L));
        } finally {
            reopened.close();
        }
    }

    @Test
    public void evictingConnectionWithoutCacheIsANoOp() {
        // A connection created while caching is disabled has no cache; eviction must not fail.
        Connector.evictConnectionCache(null, "test-connection-without-cache");
    }
}
