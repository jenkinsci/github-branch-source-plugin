package org.jenkinsci.plugins.github_branch_source;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import org.jenkinsci.plugins.github_branch_source.app_credentials.RepositoryAccessStrategy;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.github.GHPermissionType;

/**
 * Holds the inferred owner, repository, and required permissions for whatever operation is going
 * to be performed by the code that looked up these credentials.
 *
 * <p>Context is inferred either in {@link Connector#lookupScanCredentials} or {@link GitHubAppCredentials#forRun}.
 * Each call to {@link GitHubAppCredentials#contextualize} for a distinct context returns a different instance of
 * these {@link GitHubAppCredentials}.
 *
 * @see GitHubAppCredentials#contextualize
 * @see GitHubAppCredentials#cachedCredentials
 * @see GitHubAppCredentials#getAccessibleRepositories
 * @see GitHubAppCredentials#getPermissions
 * @see RepositoryAccessStrategy#forContext
 */
@Restricted(NoExternalUse.class)
public class GitHubAppUsageContext {

    private String inferredOwner;
    private String inferredRepository;
    private Map<String, GHPermissionType> permissions = Collections.emptyMap();
    private boolean trusted;

    public static final class Builder {

        private final GitHubAppUsageContext result = new GitHubAppUsageContext();

        private Builder() {}

        public Builder inferredOwner(final String inferredOwner) {
            result.inferredOwner = inferredOwner;
            return this;
        }

        public Builder inferredRepository(final String inferredRepository) {
            result.inferredRepository = inferredRepository;
            return this;
        }

        public Builder permissions(final Map<String, GHPermissionType> permissions) {
            result.permissions = permissions;
            return this;
        }

        public Builder trust() {
            result.trusted = true;
            return this;
        }

        public GitHubAppUsageContext build() {
            return result;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    @CheckForNull
    public String getInferredOwner() {
        return inferredOwner;
    }

    @CheckForNull
    public String getInferredRepository() {
        return inferredRepository;
    }

    @CheckForNull
    public Map<String, GHPermissionType> getPermissions() {
        return permissions;
    }

    /**
     * @return {@code true} if the generated installation access token will only be used in a
     *     controlled scenario such as an organization folder scan or multibranch project branch
     *     indexing. {@code false} if the token will be available for arbitrary use, for example if it
     *     is bound using {@code withCredentials} in a Pipeline.
     */
    @CheckForNull
    public boolean isTrusted() {
        return trusted;
    }

    @Override
    public int hashCode() {
        return Objects.hash(inferredOwner, inferredRepository, permissions, trusted);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final GitHubAppUsageContext other = (GitHubAppUsageContext) obj;
        return Objects.equals(inferredOwner, other.inferredOwner)
                && Objects.equals(inferredRepository, other.inferredRepository)
                && Objects.equals(permissions, other.permissions)
                && trusted == other.trusted;
    }
}
