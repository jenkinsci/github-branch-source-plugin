package org.jenkinsci.plugins.github_branch_source;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Util;
import hudson.model.Item;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import jenkins.util.SystemProperties;

/**
 * Caches the {@link com.cloudbees.plugins.credentials.common.StandardCredentials} per credentials request based on
 * context, apiUri and credentialsId. The credentials are cached within a thread context using a {@link java.lang.ThreadLocal}.
 * High cache hit ratio is expected in repeated usage within the same single threaded process such as Branch Indexing
 * and Organization Scans.
 */
public class CredentialsCache {

    /**
     * The cache expiry in minutes. Default to 10 minutes to stay within the acceptable staleness behavior of
     * {@link GitHubAppCredentials}.
     */
    private static final long CACHE_DURATION_SECONDS = SystemProperties.getLong(
            CredentialsCache.class.getName() + ".CACHE_DURATION_SECONDS", TimeUnit.MINUTES.toSeconds(1L));

    private static final ThreadLocal<Map<Integer, CacheValue>> scanCredentialsRequests = new ThreadLocal<>();

    /**
     * Retrieve the cached credentials in the specified context for use against the specified API endpoint. Otherwise
     *
     *
     * @param context the context.
     * @param apiUri the API endpoint.
     * @param credentialsId the credentials to resolve.
     * @return the {@link StandardCredentials} or {@code null}
     */
    @CheckForNull
    static StandardCredentials getOrCreate(
            @CheckForNull Item context,
            @CheckForNull String apiUri,
            @CheckForNull String credentialsId,
            Supplier<StandardCredentials> lookup) {
        if (Util.fixEmpty(credentialsId) == null) {
            return null;
        }

        if (scanCredentialsRequests.get() == null) {
            scanCredentialsRequests.set(new HashMap<>());
        }

        int cacheKey = Objects.hash(apiUri, credentialsId, context == null ? null : context.getFullName());
        CacheValue result = scanCredentialsRequests.get().get(cacheKey);
        if (result == null || result.expired()) {
            result = new CacheValue(lookup.get());
            scanCredentialsRequests.get().put(cacheKey, result);
        }
        return result.getCredentials();
    }

    private static class CacheValue {

        private final StandardCredentials credentials;

        private final long requestTimeInMs;

        public CacheValue(StandardCredentials credentials) {
            this.credentials = credentials;
            this.requestTimeInMs = System.currentTimeMillis();
        }

        public StandardCredentials getCredentials() {
            return credentials;
        }

        public boolean expired() {
            return (System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(CACHE_DURATION_SECONDS)) > requestTimeInMs;
        }
    }
}
