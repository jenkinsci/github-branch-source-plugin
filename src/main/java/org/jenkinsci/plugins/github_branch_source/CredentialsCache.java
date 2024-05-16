package org.jenkinsci.plugins.github_branch_source;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Util;
import hudson.model.Item;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Caches the {@link com.cloudbees.plugins.credentials.common.StandardCredentials} per credentials request based on
 * context, apiUri and credentialsId. The credentials are cached within a thread context using
 * a {@link java.lang.ThreadLocal}. Typically, this would yield a lot of cache it in repeated usage within the same
 * single thread process such as Branch Indexing and Organization Scans.
 */
public class CredentialsCache {

    private static final ThreadLocal<Map<CredentialsRequest, StandardCredentials>> scanCredentialsRequests =
            ThreadLocal.withInitial(HashMap::new);

    /**
     * Retrieve the cached credentials in the specified context for use against the specified API endpoint. Otherwise
     *
     *
     * @param context the context.
     * @param apiUri the API endpoint.
     * @param scanCredentialsId the credentials to resolve.
     * @return the {@link StandardCredentials} or {@code null}
     */
    @CheckForNull
    static StandardCredentials getOrCreate(
            @CheckForNull Item context,
            @CheckForNull String apiUri,
            @CheckForNull String scanCredentialsId,
            Supplier<StandardCredentials> lookup) {
        if (Util.fixEmpty(scanCredentialsId) == null) {
            return null;
        }
        CredentialsRequest credentialsRequest =
                CredentialsRequest.from(apiUri, scanCredentialsId, context == null ? null : context.getFullName());
        StandardCredentials result = scanCredentialsRequests.get().get(credentialsRequest);
        if (result == null) {
            scanCredentialsRequests.get().put(credentialsRequest, lookup.get());
        }
        return scanCredentialsRequests.get().get(credentialsRequest);
    }

    private static class CredentialsRequest {

        private final String apiUri;

        private final String credentialsId;

        private final String context;

        CredentialsRequest(String apiUri, String credentialsId, String context) {
            this.apiUri = apiUri;
            this.credentialsId = credentialsId;
            this.context = context;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CredentialsRequest)) return false;
            CredentialsRequest that = (CredentialsRequest) o;
            return Objects.equals(apiUri, that.apiUri)
                    && Objects.equals(credentialsId, that.credentialsId)
                    && Objects.equals(context, that.context);
        }

        @Override
        public int hashCode() {
            return Objects.hash(apiUri, credentialsId, context);
        }

        static CredentialsRequest from(String apiUri, String credentialsId, String context) {
            return new CredentialsRequest(apiUri, credentialsId, context);
        }
    }
}
