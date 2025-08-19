package org.jenkinsci.plugins.github_branch_source.app_credentials;

import hudson.Extension;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.github_branch_source.GitHubAppUsageContext;
import org.kohsuke.stapler.DataBoundConstructor;

public class AccessInferredOwner extends RepositoryAccessStrategy {

    @DataBoundConstructor
    public AccessInferredOwner() {}

    @Override
    public AccessibleRepositories forContext(final GitHubAppUsageContext context) {
        if (context.getInferredOwner() == null) {
            return null;
        }
        return new AccessibleRepositories(context.getInferredOwner());
    }

    @Symbol("inferOwner")
    @Extension
    public static class DescriptorImpl extends RepositoryAccessStrategyDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.AccessInferredOwner_displayName();
        }
    }
}
