/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkinsci.plugins.github_branch_source;

import com.google.inject.Inject;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.util.ListBoxModel;
import jenkins.scm.api.SCMHeadCategory;
import jenkins.scm.api.SCMHeadOrigin;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import jenkins.scm.api.mixin.ChangeRequestSCMHead2;
import jenkins.scm.api.trait.*;
import jenkins.scm.impl.ChangeRequestSCMHeadCategory;
import jenkins.scm.impl.trait.Discovery;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.github.GHPermissionType;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * A {@link Discovery} trait for GitHub that will discover pull requests from forks of the repository.
 *
 * @since 2.2.0
 */
public class ForkPullRequestDiscoveryTrait extends SCMSourceTrait {

    private static final Logger LOGGER = Logger.getLogger(ForkPullRequestDiscoveryTrait.class.getName());

    /**
     * The strategy encoded as a bit-field.
     */
    private final int strategyId;
    /**
     * The authority.
     */
    @NonNull
    private final SCMHeadAuthority<? super GitHubSCMSourceRequest, ? extends ChangeRequestSCMHead2, ? extends
            SCMRevision>
            trust;

    /**
     * Constructor for stapler.
     *
     * @param strategyId the strategy id.
     * @param trust      the authority to use.
     */
    @DataBoundConstructor
    public ForkPullRequestDiscoveryTrait(int strategyId,
                                        @NonNull SCMHeadAuthority<? super GitHubSCMSourceRequest, ? extends
                                                 ChangeRequestSCMHead2, ? extends SCMRevision> trust) {
        this.strategyId = strategyId;
        this.trust = trust;
    }

    /**
     * Constructor for programmatic instantiation.
     *
     * @param strategies the {@link ChangeRequestCheckoutStrategy} instances.
     * @param trust      the authority.
     */
    public ForkPullRequestDiscoveryTrait(@NonNull Set<ChangeRequestCheckoutStrategy> strategies,
                                         @NonNull SCMHeadAuthority<? super GitHubSCMSourceRequest, ? extends
                                                 ChangeRequestSCMHead2, ? extends SCMRevision> trust) {
        this((strategies.contains(ChangeRequestCheckoutStrategy.MERGE) ? 1 : 0)
                + (strategies.contains(ChangeRequestCheckoutStrategy.HEAD) ? 2 : 0), trust);
    }

    /**
     * Gets the strategy id.
     *
     * @return the strategy id.
     */
    public int getStrategyId() {
        return strategyId;
    }

    /**
     * Returns the strategies.
     *
     * @return the strategies.
     */
    @NonNull
    public Set<ChangeRequestCheckoutStrategy> getStrategies() {
        switch (strategyId) {
            case 1:
                return EnumSet.of(ChangeRequestCheckoutStrategy.MERGE);
            case 2:
                return EnumSet.of(ChangeRequestCheckoutStrategy.HEAD);
            case 3:
                return EnumSet.of(ChangeRequestCheckoutStrategy.HEAD, ChangeRequestCheckoutStrategy.MERGE);
            default:
                return EnumSet.noneOf(ChangeRequestCheckoutStrategy.class);
        }
    }

    /**
     * Gets the authority.
     *
     * @return the authority.
     */
    @NonNull
    public SCMHeadAuthority<? super GitHubSCMSourceRequest, ? extends ChangeRequestSCMHead2, ? extends SCMRevision> getTrust() {
        return trust;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void decorateContext(SCMSourceContext<?, ?> context) {
        GitHubSCMSourceContext ctx = (GitHubSCMSourceContext) context;
        ctx.wantForkPRs(true);
        ctx.withAuthority(trust);
        ctx.withForkPRStrategies(getStrategies());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean includeCategory(@NonNull SCMHeadCategory category) {
        return category instanceof ChangeRequestSCMHeadCategory;
    }

    /**
     * Our descriptor.
     */
    @Extension
    @Discovery
    public static class DescriptorImpl extends SCMSourceTraitDescriptor {

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.ForkPullRequestDiscoveryTrait_displayName();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Class<? extends SCMSourceContext> getContextClass() {
            return GitHubSCMSourceContext.class;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Class<? extends SCMSource> getSourceClass() {
            return GitHubSCMSource.class;
        }

        /**
         * Populates the strategy options.
         *
         * @return the stategy options.
         */
        @NonNull
        @Restricted(NoExternalUse.class)
        @SuppressWarnings("unused") // stapler
        public ListBoxModel doFillStrategyIdItems() {
            ListBoxModel result = new ListBoxModel();
            result.add(Messages.ForkPullRequestDiscoveryTrait_mergeOnly(), "1");
            result.add(Messages.ForkPullRequestDiscoveryTrait_headOnly(), "2");
            result.add(Messages.ForkPullRequestDiscoveryTrait_headAndMerge(), "3");
            return result;
        }

        /**
         * Returns the list of appropriate {@link SCMHeadAuthorityDescriptor} instances.
         *
         * @return the list of appropriate {@link SCMHeadAuthorityDescriptor} instances.
         */
        @NonNull
        @SuppressWarnings("unused") // stapler
        public List<SCMHeadAuthorityDescriptor> getTrustDescriptors() {
            return SCMHeadAuthority._for(
                    GitHubSCMSourceRequest.class,
                    PullRequestSCMHead.class,
                    PullRequestSCMRevision.class,
                    SCMHeadOrigin.Fork.class
            );
        }

        /**
         * Returns the default trust for new instances of {@link ForkPullRequestDiscoveryTrait}.
         *
         * @return the default trust for new instances of {@link ForkPullRequestDiscoveryTrait}.
         */
        @NonNull
        @SuppressWarnings("unused") // stapler
        public SCMHeadAuthority<?, ?, ?> getDefaultTrust() {
            return new TrustContributors();
        }
    }


    /**
     * An {@link SCMHeadAuthority} that trusts nothing.
     */
    public static class TrustNobody extends SCMHeadAuthority<SCMSourceRequest, PullRequestSCMHead, PullRequestSCMRevision> {

        /**
         * Constructor.
         */
        @DataBoundConstructor
        public TrustNobody() {
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean checkTrusted(@NonNull SCMSourceRequest request, @NonNull PullRequestSCMHead head) {
            return false;
        }

        /**
         * Our descriptor.
         */
        @Extension
        public static class DescriptorImpl extends SCMHeadAuthorityDescriptor {

            /**
             * {@inheritDoc}
             */
            @Override
            public String getDisplayName() {
                return Messages.ForkPullRequestDiscoveryTrait_nobodyDisplayName();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean isApplicableToOrigin(@NonNull Class<? extends SCMHeadOrigin> originClass) {
                return SCMHeadOrigin.Fork.class.isAssignableFrom(originClass);
            }
        }
    }

    /**
     * An {@link SCMHeadAuthority} that trusts contributors to the repository.
     */
    public static class TrustContributors
            extends SCMHeadAuthority<GitHubSCMSourceRequest, PullRequestSCMHead, PullRequestSCMRevision> {
        /**
         * Constructor.
         */
        @DataBoundConstructor
        public TrustContributors() {
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected boolean checkTrusted(@NonNull GitHubSCMSourceRequest request, @NonNull PullRequestSCMHead head) {
            return !head.getOrigin().equals(SCMHeadOrigin.DEFAULT)
                    && request.getCollaboratorNames().contains(head.getSourceOwner());
        }

        /**
         * Our descriptor.
         */
        @Extension
        public static class DescriptorImpl extends SCMHeadAuthorityDescriptor {

            /**
             * {@inheritDoc}
             */
            @Override
            public String getDisplayName() {
                return Messages.ForkPullRequestDiscoveryTrait_contributorsDisplayName();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean isApplicableToOrigin(@NonNull Class<? extends SCMHeadOrigin> originClass) {
                return SCMHeadOrigin.Fork.class.isAssignableFrom(originClass);
            }

        }
    }

    /**
     * An {@link SCMHeadAuthority} that trusts contributors to the repository as well as those on a whitelist.
     */
    public static class TrustContributorsAndWhitelist extends TrustContributors {

        private WhitelistSource whitelist;

        /**
         * Constructor.
         */
        @Inject
        @DataBoundConstructor
        public TrustContributorsAndWhitelist(WhitelistSource whitelist) {
            this.whitelist = whitelist;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected boolean checkTrusted(@NonNull GitHubSCMSourceRequest request, @NonNull PullRequestSCMHead head) {
            boolean isContributor = super.checkTrusted(request, head);
            boolean isWhitelisted = whitelist.contains(head.getSourceOwner());
            LOGGER.log(Level.INFO, "Pull request author {0} whitelisted? {1}", head.getSourceOwner(), isWhitelisted);
            return isContributor || isWhitelisted;
        }

        /**
         * Our descriptor.
         */
        @Extension
        public static class DescriptorImpl extends SCMHeadAuthorityDescriptor {

            /**
             * {@inheritDoc}
             */
            @Override
            public String getDisplayName() {
                return Messages.ForkPullRequestDiscoveryTrait_contributorsAndWhitelistDisplayName();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean isApplicableToOrigin(@NonNull Class<? extends SCMHeadOrigin> originClass) {
                return SCMHeadOrigin.Fork.class.isAssignableFrom(originClass);
            }

        }
    }

    /**
     * An {@link SCMHeadAuthority} that trusts a whitelist of users.
     */
    public static class TrustWhitelist
            extends SCMHeadAuthority<GitHubSCMSourceRequest, PullRequestSCMHead, PullRequestSCMRevision> {

        private WhitelistSource whitelist;

        /**
         * Constructor.
         */
        @Inject
        @DataBoundConstructor
        public TrustWhitelist(WhitelistSource whitelist) {
            this.whitelist = whitelist;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected boolean checkTrusted(@NonNull GitHubSCMSourceRequest request, @NonNull PullRequestSCMHead head) {
            boolean isWhitelisted = whitelist.contains(head.getSourceOwner());
            LOGGER.log(Level.INFO, "Pull request author {0} whitelisted? {1}", head.getSourceOwner(), isWhitelisted);
            return !head.getOrigin().equals(SCMHeadOrigin.DEFAULT) && isWhitelisted;
        }

        /**
         * Our descriptor.
         */
        @Extension
        public static class DescriptorImpl extends SCMHeadAuthorityDescriptor {

            /**
             * {@inheritDoc}
             */
            @Override
            public String getDisplayName() {
                return Messages.ForkPullRequestDiscoveryTrait_whitelistDisplayName();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean isApplicableToOrigin(@NonNull Class<? extends SCMHeadOrigin> originClass) {
                return SCMHeadOrigin.Fork.class.isAssignableFrom(originClass);
            }

        }
    }

    /**
     * An {@link SCMHeadAuthority} that trusts contributors to the repository.
     */
    public static class TrustPermission
            extends SCMHeadAuthority<GitHubSCMSourceRequest, PullRequestSCMHead, PullRequestSCMRevision> {

        /**
         * Constructor.
         */
        @DataBoundConstructor
        public TrustPermission() {
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected boolean checkTrusted(@NonNull GitHubSCMSourceRequest request, @NonNull PullRequestSCMHead head)
                throws IOException, InterruptedException {
            if (!head.getOrigin().equals(SCMHeadOrigin.DEFAULT)) {
                GHPermissionType permission = request.getPermissions(head.getSourceOwner());
                switch (permission) {
                    case ADMIN:
                    case WRITE:
                        return true;
                    default:return false;
                }
            }
            return false;
        }

        /**
         * Our descriptor.
         */
        @Extension
        public static class DescriptorImpl extends SCMHeadAuthorityDescriptor {
            /**
             * {@inheritDoc}
             */
            @Override
            public String getDisplayName() {
                return Messages.ForkPullRequestDiscoveryTrait_permissionsDisplayName();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean isApplicableToOrigin(@NonNull Class<? extends SCMHeadOrigin> originClass) {
                return SCMHeadOrigin.Fork.class.isAssignableFrom(originClass);
            }
        }
    }

    /**
     * An {@link SCMHeadAuthority} that trusts everyone.
     */
    public static class TrustEveryone extends SCMHeadAuthority<SCMSourceRequest, PullRequestSCMHead, PullRequestSCMRevision> {
        /**
         * Constructor.
         */
        @DataBoundConstructor
        public TrustEveryone() {
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected boolean checkTrusted(@NonNull SCMSourceRequest request, @NonNull PullRequestSCMHead head) {
            return true;
        }

        /**
         * Our descriptor.
         */
        @Extension
        public static class DescriptorImpl extends SCMHeadAuthorityDescriptor {

            /**
             * {@inheritDoc}
             */
            @Override
            public String getDisplayName() {
                return Messages.ForkPullRequestDiscoveryTrait_everyoneDisplayName();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean isApplicableToOrigin(@NonNull Class<? extends SCMHeadOrigin> originClass) {
                return SCMHeadOrigin.Fork.class.isAssignableFrom(originClass);
            }
        }
    }
}
