package org.jenkinsci.plugins.github_branch_source;

import hudson.Extension;
import jenkins.scm.api.trait.SCMNavigatorContext;
import jenkins.scm.api.trait.SCMNavigatorTrait;
import jenkins.scm.api.trait.SCMNavigatorTraitDescriptor;
import jenkins.scm.impl.trait.Selection;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;

/**
 * A {@link Selection} trait that will restrict the discovery of repositories that have been archived.
 */
public class ExcludeArchivedRepositoriesTrait extends SCMNavigatorTrait {

    /**
     * Constructor for stapler.
     */
    @DataBoundConstructor
    public ExcludeArchivedRepositoriesTrait() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void decorateContext(SCMNavigatorContext<?, ?> context) {
        super.decorateContext(context);
        GitHubSCMNavigatorContext ctx = (GitHubSCMNavigatorContext) context;
        ctx.setExcludeArchivedRepositories(true);
    }

    /**
     * Exclude archived repositories filter
     */
    @Symbol("gitHubExcludeArchivedRepositories")
    @Extension
    @Selection
    public static class DescriptorImpl extends SCMNavigatorTraitDescriptor {

        @Override
        public Class<? extends SCMNavigatorContext> getContextClass() {
            return GitHubSCMNavigatorContext.class;
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return Messages.ExcludeArchivedRepositoriesTrait_displayName();
        }
    }
}
