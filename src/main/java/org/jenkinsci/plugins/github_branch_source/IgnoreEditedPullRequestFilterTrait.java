package org.jenkinsci.plugins.github_branch_source;

import hudson.Extension;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import jenkins.scm.impl.trait.Selection;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

/** Trait to suppress new builds when a pull request title or description is edited. */
public class IgnoreEditedPullRequestFilterTrait extends SCMSourceTrait {

    @DataBoundConstructor
    public IgnoreEditedPullRequestFilterTrait() {}

    @Override
    protected void decorateContext(SCMSourceContext<?, ?> context) {
        if (context instanceof GitHubSCMSourceContext) {
            ((GitHubSCMSourceContext) context).withIgnoreEditedPullRequests(true);
        }
    }

    @Symbol("gitHubIgnoreEditedPullRequestFilter")
    @Extension
    @Selection
    public static class DescriptorImpl extends SCMSourceTraitDescriptor {

        public DescriptorImpl() {}

        @Override
        public String getDisplayName() {
            return Messages.IgnoreEditedPullRequestFilterTrait_DisplayName();
        }

        @Override
        public Class<? extends SCMSourceContext> getContextClass() {
            return GitHubSCMSourceContext.class;
        }

        @Override
        public Class<? extends SCMSource> getSourceClass() {
            return GitHubSCMSource.class;
        }
    }
}
