package org.jenkinsci.plugins.github_branch_source;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Util;
import hudson.model.Item;
import hudson.model.ItemGroup;
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
     * The cache expiry in minutes. Default to 15 minutes.
     */
    private static final long CACHE_DURATION_SECONDS = SystemProperties.getLong(
            CredentialsCache.class.getName() + ".CACHE_DURATION_SECONDS", TimeUnit.MINUTES.toSeconds(1L));

    /**
     * Whether the cache is disabled.
     */
    private static final boolean CACHE_DISABLED =
            SystemProperties.getBoolean(CredentialsCache.class.getName() + ".CACHE_DISABLED", false);

    private static final ThreadLocal<Map<Integer, CacheValue>> scanCredentialsRequests = new ThreadLocal<>();

    /**
     * Retrieve the cached credentials in the specified context for use against the specified API endpoint.
     * If not found,
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

        if (CACHE_DISABLED) {
            return lookup.get();
        }

        if (scanCredentialsRequests.get() == null) {
            scanCredentialsRequests.set(new HashMap<>());
        }

        int cacheKey = Objects.hash(apiUri, credentialsId, context == null ? null : context.getFullName());
        CacheValue result = scanCredentialsRequests.get().get(cacheKey);
        if (result == null || result.expired()) {
            if (context != null) {
                /*
                 * Check if the credentials is cached for the parent context (SCMNavigator). If it is, it will be
                 * stored in cache for the parent context and also the requested context for better cache hit
                 * throughput.
                 * That guarantees that the credentials cache is used throughout an entire Organization Scan process
                 * that use the context of the OrganizationFolder and then a different child context for all repository
                 * MultibranchProjects that it visits.
                 */
                ItemGroup<?> parent = context.getParent();
                int parentCacheKey = Objects.hash(apiUri, credentialsId, parent == null ? null : parent.getFullName());
                result = scanCredentialsRequests.get().get(parentCacheKey);

                if (result == null || result.expired()) {
                    result = new CacheValue(lookup.get());
                    scanCredentialsRequests.get().put(parentCacheKey, result);
                }
            } else {
                result = new CacheValue(lookup.get());
            }
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
