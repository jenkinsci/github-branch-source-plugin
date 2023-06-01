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
 * Decorates a {@link SCMNavigatorContext} with a GitHub team slug which will allow restricting the
 * discovery of repositories by specific teams
 */
public class TeamSlugTrait extends SCMNavigatorTrait {

    /** The team slug. */
    @NonNull
    private final String teamSlug;

    /**
     * Stapler constructor.
     *
     * @param teamSlug the team slug to use when searching for github repos restricted to a specific
     *     team only.
     */
    @DataBoundConstructor
    public TeamSlugTrait(@NonNull String teamSlug) {
        this.teamSlug = teamSlug;
    }

    /**
     * Returns the teamSlug.
     *
     * @return the teamSlug.
     */
    @NonNull
    public String getTeamSlug() {
        return teamSlug;
    }

    @Override
    protected void decorateContext(final SCMNavigatorContext<?, ?> context) {
        super.decorateContext(context);
        ((GitHubSCMNavigatorContext) context).setTeamSlug(teamSlug);
    }

    /** TeamSlug descriptor. */
    @Symbol("teamSlugFilter")
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
            return Messages.TeamSlugTrait_displayName();
        }
    }
}
