package org.jenkinsci.plugins.github_branch_source;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import jenkins.scm.api.trait.SCMNavigatorContext;
import jenkins.scm.api.trait.SCMNavigatorTrait;
import jenkins.scm.api.trait.SCMNavigatorTraitDescriptor;
import jenkins.scm.impl.trait.Selection;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * A {@link Selection} trait that will restrict the discovery of repositories that have been forked.
 */
public class ExcludeForkedRepositoriesTrait extends SCMNavigatorTrait {

    /** Constructor for stapler. */
    @DataBoundConstructor
    public ExcludeForkedRepositoriesTrait() {}

    /** {@inheritDoc} */
    @Override
    protected void decorateContext(SCMNavigatorContext<?, ?> context) {
        super.decorateContext(context);
        GitHubSCMNavigatorContext ctx = (GitHubSCMNavigatorContext) context;
        ctx.setExcludeForkedRepositories(true);
    }

    /** Exclude forked repositories filter */
    @Symbol("gitHubExcludeForkedRepositories")
    @Extension
    @Selection
    public static class DescriptorImpl extends SCMNavigatorTraitDescriptor {

        @Override
        public Class<? extends SCMNavigatorContext> getContextClass() {
            return GitHubSCMNavigatorContext.class;
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.ExcludeForkedRepositoriesTrait_displayName();
        }
    }
}
