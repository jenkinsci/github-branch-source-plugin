package org.jenkinsci.plugins.github_branch_source;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThrows;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import java.io.File;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
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

    @Test
    public void reopeningCacheRevalidatesWithConditionalRequest() throws Exception {
        // A revalidation carrying the stored ETag is answered with 304 Not Modified.
        server.stubFor(get(urlEqualTo("/data"))
                .atPriority(1)
                .withHeader("If-None-Match", equalTo("\"v1\""))
                .willReturn(aResponse().withStatus(304)));
        // The initial response is storable but must be revalidated before reuse, and carries an ETag.
        server.stubFor(get(urlEqualTo("/data"))
                .atPriority(2)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("Cache-Control", "no-cache")
                        .withHeader("ETag", "\"v1\"")
                        .withBody("{\"hello\":\"world\"}")));

        File cacheDir = new File(tmp.getRoot(), "response-cache");
        Request request = new Request.Builder().url(server.baseUrl() + "/data").build();

        // Populate the cache, then close it as connection eviction would.
        Cache cache = new Cache(cacheDir, 10L * 1024 * 1024);
        OkHttpClient client = new OkHttpClient.Builder().cache(cache).build();
        try (Response response = client.newCall(request).execute()) {
            assertThat(response.code(), is(200));
            assertThat(response.body().string(), is("{\"hello\":\"world\"}"));
        }
        Connector.evictConnectionCache(cache, "test-connection");

        // Reopen a fresh cache over the same directory: the persisted ETag must drive a conditional
        // request whose 304 response is served from the on-disk cache rather than refetched in full.
        Cache reopened = new Cache(cacheDir, 10L * 1024 * 1024);
        OkHttpClient reopenedClient = new OkHttpClient.Builder().cache(reopened).build();
        try (Response response = reopenedClient.newCall(request).execute()) {
            assertThat("a 304 revalidation should be served from cache as a 200", response.code(), is(200));
            assertThat(response.body().string(), is("{\"hello\":\"world\"}"));
            assertThat(
                    "the response should have been served from the on-disk cache",
                    response.cacheResponse(),
                    is(notNullValue()));
            assertThat(
                    "the revalidation must have hit the network with a 304",
                    response.networkResponse(),
                    is(notNullValue()));
            assertThat(response.networkResponse().code(), is(304));
        } finally {
            reopened.close();
        }

        // The server must have received a revalidation carrying the stored ETag.
        server.verify(getRequestedFor(urlEqualTo("/data")).withHeader("If-None-Match", equalTo("\"v1\"")));
    }

    @Test
    public void pruneStaleCachesRemovesOnlyStaleOrphanDirectories() throws Exception {
        File cacheBase = tmp.newFolder("caches");
        File live = new File(cacheBase, "live");
        File staleOrphan = new File(cacheBase, "stale-orphan");
        File freshOrphan = new File(cacheBase, "fresh-orphan");
        for (File d : new File[] {live, staleOrphan, freshOrphan}) {
            assertThat(d.mkdirs(), is(true));
            // A file inside gives the recursive delete something to remove.
            assertThat(new File(d, "marker").createNewFile(), is(true));
        }
        long now = System.currentTimeMillis();
        long tenDaysAgo = now - TimeUnit.DAYS.toMillis(10);
        live.setLastModified(tenDaysAgo);
        staleOrphan.setLastModified(tenDaysAgo);
        freshOrphan.setLastModified(now);

        long staleBefore = now - TimeUnit.DAYS.toMillis(7);
        Connector.pruneStaleCaches(cacheBase, Collections.singleton(live), staleBefore);

        assertThat("a directory backing a live connection must be kept", live.isDirectory(), is(true));
        assertThat("a recently used directory must be kept for reuse", freshOrphan.isDirectory(), is(true));
        assertThat("a stale orphaned directory must be pruned", staleOrphan.exists(), is(false));
    }

    @Test
    public void pruneStaleCachesToleratesMissingBaseDirectory() {
        File missing = new File(tmp.getRoot(), "does-not-exist");
        // Must not throw when the cache base directory has never been created.
        Connector.pruneStaleCaches(missing, Collections.emptySet(), System.currentTimeMillis());
    }
}
