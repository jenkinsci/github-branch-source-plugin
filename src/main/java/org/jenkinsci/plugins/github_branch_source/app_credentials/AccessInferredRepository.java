package org.jenkinsci.plugins.github_branch_source.app_credentials;

import hudson.Extension;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.github_branch_source.GitHubAppCredentials;
import org.jenkinsci.plugins.github_branch_source.GitHubAppUsageContext;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * This mode allows {@link GitHubAppCredentials} to generate an installation token whose owner and
 * accessible repositories depend on the context in which the credential is used.
 *
 * <p>For example, when used in a multibranch project, the generated installation tokens will only
 * allow access to the repository for the multibranch project itself. When used with an organization
 * folder, each multibranch project will get an access token that is only valid for its own
 * repository.
 *
 * <p>Note that some organization folder functionality (e.g. org scans) uses tokens that are not
 * limited to a specific repository, but these tokens should never be accessible in the context of a
 * {@code Run}.
 */
public class AccessInferredRepository extends RepositoryAccessStrategy {

    @DataBoundConstructor
    public AccessInferredRepository() {}

    @Override
    public AccessibleRepositories forContext(final GitHubAppUsageContext context) {
        final var inferredOwner = context.getInferredOwner();
        if (inferredOwner == null) {
            return null;
        }
        if (context.isTrusted()) {
            return new AccessibleRepositories(inferredOwner);
        }
        final var inferredRepository = context.getInferredRepository();
        if (inferredRepository == null) {
            return null;
        }
        return new AccessibleRepositories(inferredOwner, inferredRepository);
    }

    @Symbol("inferRepository")
    @Extension
    public static class DescriptorImpl extends RepositoryAccessStrategyDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.AccessInferredRepository_displayName();
        }
    }
}
