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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import jenkins.scm.api.SCMHeadCategory;
import jenkins.scm.api.SCMHeadOrigin;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import jenkins.scm.api.mixin.ChangeRequestSCMHead2;
import jenkins.scm.api.trait.SCMHeadAuthority;
import jenkins.scm.api.trait.SCMHeadAuthorityDescriptor;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceRequest;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import jenkins.scm.impl.ChangeRequestSCMHeadCategory;
import jenkins.scm.impl.trait.Discovery;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.github.GHPermissionType;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * A {@link Discovery} trait for GitHub that will discover pull requests from forks of the
 * repository.
 *
 * @since 2.2.0
 */
public class ForkPullRequestDiscoveryTrait extends SCMSourceTrait {
    /** None strategy. */
    public static final int NONE = 0;
    /** Merging the pull request with the current target branch revision. */
    public static final int MERGE = 1;
    /** The current pull request revision. */
    public static final int HEAD = 2;
    /**
     * Both the current pull request revision and the pull request merged with the current target
     * branch revision.
     */
    public static final int HEAD_AND_MERGE = 3;
    /** The strategy encoded as a bit-field. */
    private final int strategyId;
    /** The authority. */
    @NonNull
    private final SCMHeadAuthority<
                    ? super GitHubSCMSourceRequest, ? extends ChangeRequestSCMHead2, ? extends SCMRevision>
            trust;

    /**
     * Constructor for stapler.
     *
     * @param strategyId the strategy id.
     * @param trust the authority to use.
     */
    @DataBoundConstructor
    public ForkPullRequestDiscoveryTrait(
            int strategyId,
            @NonNull
                    SCMHeadAuthority<
                                    ? super GitHubSCMSourceRequest,
                                    ? extends ChangeRequestSCMHead2,
                                    ? extends SCMRevision>
                            trust) {
        this.strategyId = strategyId;
        this.trust = trust;
    }

    /**
     * Constructor for programmatic instantiation.
     *
     * @param strategies the {@link ChangeRequestCheckoutStrategy} instances.
     * @param trust the authority.
     */
    public ForkPullRequestDiscoveryTrait(
            @NonNull Set<ChangeRequestCheckoutStrategy> strategies,
            @NonNull
                    SCMHeadAuthority<
                                    ? super GitHubSCMSourceRequest,
                                    ? extends ChangeRequestSCMHead2,
                                    ? extends SCMRevision>
                            trust) {
        this(
                (strategies.contains(ChangeRequestCheckoutStrategy.MERGE) ? MERGE : NONE)
                        + (strategies.contains(ChangeRequestCheckoutStrategy.HEAD) ? HEAD : NONE),
                trust);
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
            case ForkPullRequestDiscoveryTrait.MERGE:
                return EnumSet.of(ChangeRequestCheckoutStrategy.MERGE);
            case ForkPullRequestDiscoveryTrait.HEAD:
                return EnumSet.of(ChangeRequestCheckoutStrategy.HEAD);
            case ForkPullRequestDiscoveryTrait.HEAD_AND_MERGE:
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
    public SCMHeadAuthority<? super GitHubSCMSourceRequest, ? extends ChangeRequestSCMHead2, ? extends SCMRevision>
            getTrust() {
        return trust;
    }

    /** {@inheritDoc} */
    @Override
    protected void decorateContext(SCMSourceContext<?, ?> context) {
        GitHubSCMSourceContext ctx = (GitHubSCMSourceContext) context;
        ctx.wantForkPRs(true);
        ctx.withAuthority(trust);
        ctx.withForkPRStrategies(getStrategies());
    }

    /** {@inheritDoc} */
    @Override
    public boolean includeCategory(@NonNull SCMHeadCategory category) {
        return category instanceof ChangeRequestSCMHeadCategory;
    }

    /** Our descriptor. */
    @Symbol("gitHubForkDiscovery")
    @Extension
    @Discovery
    public static class DescriptorImpl extends SCMSourceTraitDescriptor {

        /** {@inheritDoc} */
        @Override
        public String getDisplayName() {
            return Messages.ForkPullRequestDiscoveryTrait_displayName();
        }

        /** {@inheritDoc} */
        @Override
        public Class<? extends SCMSourceContext> getContextClass() {
            return GitHubSCMSourceContext.class;
        }

        /** {@inheritDoc} */
        @Override
        public Class<? extends SCMSource> getSourceClass() {
            return GitHubSCMSource.class;
        }

        /**
         * Populates the strategy options.
         *
         * @return the strategy options.
         */
        @NonNull
        @Restricted(NoExternalUse.class)
        @SuppressWarnings("unused") // stapler
        public ListBoxModel doFillStrategyIdItems() {
            ListBoxModel result = new ListBoxModel();
            result.add(Messages.ForkPullRequestDiscoveryTrait_mergeOnly(), String.valueOf(MERGE));
            result.add(Messages.ForkPullRequestDiscoveryTrait_headOnly(), String.valueOf(HEAD));
            result.add(Messages.ForkPullRequestDiscoveryTrait_headAndMerge(), String.valueOf(HEAD_AND_MERGE));
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
                    SCMHeadOrigin.Fork.class);
        }

        /**
         * Returns the default trust for new instances of {@link ForkPullRequestDiscoveryTrait}.
         *
         * @return the default trust for new instances of {@link ForkPullRequestDiscoveryTrait}.
         */
        @NonNull
        @SuppressWarnings("unused") // stapler
        public SCMHeadAuthority<?, ?, ?> getDefaultTrust() {
            return new TrustPermission();
        }
    }

    /** An {@link SCMHeadAuthority} that trusts nothing. */
    public static class TrustNobody
            extends SCMHeadAuthority<SCMSourceRequest, PullRequestSCMHead, PullRequestSCMRevision> {

        /** Constructor. */
        @DataBoundConstructor
        public TrustNobody() {}

        /** {@inheritDoc} */
        @Override
        public boolean checkTrusted(@NonNull SCMSourceRequest request, @NonNull PullRequestSCMHead head) {
            return false;
        }

        /** Our descriptor. */
        @Symbol("gitHubTrustNobody")
        @Extension
        public static class DescriptorImpl extends SCMHeadAuthorityDescriptor {

            /** {@inheritDoc} */
            @Override
            public String getDisplayName() {
                return Messages.ForkPullRequestDiscoveryTrait_nobodyDisplayName();
            }

            /** {@inheritDoc} */
            @Override
            public boolean isApplicableToOrigin(@NonNull Class<? extends SCMHeadOrigin> originClass) {
                return SCMHeadOrigin.Fork.class.isAssignableFrom(originClass);
            }
        }
    }

    /** An {@link SCMHeadAuthority} that trusts contributors to the repository. */
    public static class TrustContributors
            extends SCMHeadAuthority<GitHubSCMSourceRequest, PullRequestSCMHead, PullRequestSCMRevision> {
        /** Constructor. */
        @DataBoundConstructor
        public TrustContributors() {}

        /** {@inheritDoc} */
        @Override
        protected boolean checkTrusted(@NonNull GitHubSCMSourceRequest request, @NonNull PullRequestSCMHead head) {
            return !head.getOrigin().equals(SCMHeadOrigin.DEFAULT)
                    && request.getCollaboratorNames().contains(head.getSourceOwner());
        }

        /** Our descriptor. */
        @Symbol("gitHubTrustContributors")
        @Extension
        public static class DescriptorImpl extends SCMHeadAuthorityDescriptor {

            /** {@inheritDoc} */
            @Override
            public String getDisplayName() {
                return Messages.ForkPullRequestDiscoveryTrait_contributorsDisplayName();
            }

            /** {@inheritDoc} */
            @Override
            public boolean isApplicableToOrigin(@NonNull Class<? extends SCMHeadOrigin> originClass) {
                return SCMHeadOrigin.Fork.class.isAssignableFrom(originClass);
            }
        }
    }

    /** An {@link SCMHeadAuthority} that trusts those with write permission to the repository. */
    public static class TrustPermission
            extends SCMHeadAuthority<GitHubSCMSourceRequest, PullRequestSCMHead, PullRequestSCMRevision> {

        /** Constructor. */
        @DataBoundConstructor
        public TrustPermission() {}

        /** {@inheritDoc} */
        @Override
        protected boolean checkTrusted(@NonNull GitHubSCMSourceRequest request, @NonNull PullRequestSCMHead head)
                throws IOException, InterruptedException {
            if (!head.getOrigin().equals(SCMHeadOrigin.DEFAULT)) {
                GHPermissionType permission = request.getPermissions(head.getSourceOwner());
                switch (permission) {
                    case ADMIN:
                    case WRITE:
                        return true;
                    default:
                        return false;
                }
            }
            return false;
        }

        /** Our descriptor. */
        @Symbol("gitHubTrustPermissions")
        @Extension
        public static class DescriptorImpl extends SCMHeadAuthorityDescriptor {

            /** {@inheritDoc} */
            @Override
            public String getDisplayName() {
                return Messages.ForkPullRequestDiscoveryTrait_permissionsDisplayName();
            }

            /** {@inheritDoc} */
            @Override
            public boolean isApplicableToOrigin(@NonNull Class<? extends SCMHeadOrigin> originClass) {
                return SCMHeadOrigin.Fork.class.isAssignableFrom(originClass);
            }
        }
    }

    /** An {@link SCMHeadAuthority} that trusts everyone. */
    public static class TrustEveryone
            extends SCMHeadAuthority<SCMSourceRequest, PullRequestSCMHead, PullRequestSCMRevision> {
        /** Constructor. */
        @DataBoundConstructor
        public TrustEveryone() {}

        /** {@inheritDoc} */
        @Override
        protected boolean checkTrusted(@NonNull SCMSourceRequest request, @NonNull PullRequestSCMHead head) {
            return true;
        }

        /** Our descriptor. */
        @Symbol("gitHubTrustEveryone")
        @Extension
        public static class DescriptorImpl extends SCMHeadAuthorityDescriptor {

            /** {@inheritDoc} */
            @Override
            public String getDisplayName() {
                return Messages.ForkPullRequestDiscoveryTrait_everyoneDisplayName();
            }

            /** {@inheritDoc} */
            @Override
            public boolean isApplicableToOrigin(@NonNull Class<? extends SCMHeadOrigin> originClass) {
                return SCMHeadOrigin.Fork.class.isAssignableFrom(originClass);
            }
        }
    }
}
