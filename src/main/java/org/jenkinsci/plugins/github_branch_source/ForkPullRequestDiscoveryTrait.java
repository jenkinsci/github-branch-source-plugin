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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
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
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import jenkins.scm.impl.ChangeRequestSCMHeadCategory;
import jenkins.scm.impl.trait.Discovery;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHPermissionType;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

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
    public ForkPullRequestDiscoveryTrait(int strategyId, @NonNull GitHubForkTrustPolicy trust) {
        this.strategyId = strategyId;
        this.trust = trust;
    }

    @Deprecated
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

    /** Trust policy for forked pull requests.
     * <p>
     * This reduces generics in the DataBoundConstructor signature as a workaround for JENKINS-26535.
     */
    public abstract static class GitHubForkTrustPolicy
            extends SCMHeadAuthority<GitHubSCMSourceRequest, PullRequestSCMHead, PullRequestSCMRevision> {}

    /** An {@link SCMHeadAuthority} that trusts nothing. */
    public static class TrustNobody extends GitHubForkTrustPolicy {

        /** Constructor. */
        @DataBoundConstructor
        public TrustNobody() {}

        /** {@inheritDoc} */
        @Override
        public boolean checkTrusted(@NonNull GitHubSCMSourceRequest request, @NonNull PullRequestSCMHead head) {
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
    public static class TrustContributors extends GitHubForkTrustPolicy {
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
    public static class TrustPermission extends GitHubForkTrustPolicy {

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
    public static class TrustEveryone extends GitHubForkTrustPolicy {
        /** Constructor. */
        @DataBoundConstructor
        public TrustEveryone() {}

        /** {@inheritDoc} */
        @Override
        protected boolean checkTrusted(@NonNull GitHubSCMSourceRequest request, @NonNull PullRequestSCMHead head) {
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

    /**
     * An {@link SCMHeadAuthority} that requires external approval before fork pull requests can
     * build. Jobs are created as disabled with a pending approval marker. An administrator must
     * approve via the UI or API before the job will run.
     */
    public static class TrustExternalApproval extends GitHubForkTrustPolicy {
        private boolean requireApprovalForNewCommits;

        @CheckForNull
        private List<String> autoApprovalLabels;

        @CheckForNull
        private List<String> autoApprovalUsers;

        /** Constructor. */
        @DataBoundConstructor
        public TrustExternalApproval() {}

        /**
         * Returns whether a new approval is required when new commits are pushed to the PR.
         *
         * @return {@code true} if approval is required for each new commit.
         */
        public boolean isRequireApprovalForNewCommits() {
            return requireApprovalForNewCommits;
        }

        /**
         * Sets whether a new approval is required when new commits are pushed to the PR.
         *
         * @param requireApprovalForNewCommits {@code true} to require re-approval on new commits.
         */
        @DataBoundSetter
        public void setRequireApprovalForNewCommits(boolean requireApprovalForNewCommits) {
            this.requireApprovalForNewCommits = requireApprovalForNewCommits;
        }

        /**
         * Returns the list of PR labels that trigger automatic approval.
         *
         * @return the list of label names, or {@code null} if not configured.
         */
        @CheckForNull
        public List<String> getAutoApprovalLabels() {
            return autoApprovalLabels;
        }

        /**
         * Returns the auto-approval labels as a comma-separated string for form binding.
         *
         * @return comma-separated label names, or {@code null} if not configured.
         */
        @CheckForNull
        public String getAutoApprovalLabelsString() {
            return autoApprovalLabels == null ? null : String.join(", ", autoApprovalLabels);
        }

        /**
         * Sets the list of PR labels from a comma-separated string (Stapler form binding).
         *
         * @param autoApprovalLabels comma-separated label names.
         */
        @DataBoundSetter
        public void setAutoApprovalLabels(@CheckForNull String autoApprovalLabels) {
            this.autoApprovalLabels = parseCommaSeparated(autoApprovalLabels);
        }

        /**
         * Sets the list of PR labels that trigger automatic approval.
         *
         * @param autoApprovalLabels the label names to auto-approve.
         */
        public void setAutoApprovalLabelsList(@CheckForNull List<String> autoApprovalLabels) {
            if (autoApprovalLabels == null || autoApprovalLabels.isEmpty()) {
                this.autoApprovalLabels = null;
            } else {
                this.autoApprovalLabels = Collections.unmodifiableList(new ArrayList<>(autoApprovalLabels));
            }
        }

        /**
         * Returns the list of GitHub user logins that are automatically trusted.
         *
         * @return the list of user logins, or {@code null} if not configured.
         */
        @CheckForNull
        public List<String> getAutoApprovalUsers() {
            return autoApprovalUsers;
        }

        /**
         * Returns the auto-approval users as a comma-separated string for form binding.
         *
         * @return comma-separated user logins, or {@code null} if not configured.
         */
        @CheckForNull
        public String getAutoApprovalUsersString() {
            return autoApprovalUsers == null ? null : String.join(", ", autoApprovalUsers);
        }

        /**
         * Sets the list of GitHub user logins from a comma-separated string (Stapler form
         * binding).
         *
         * @param autoApprovalUsers comma-separated GitHub login names.
         */
        @DataBoundSetter
        public void setAutoApprovalUsers(@CheckForNull String autoApprovalUsers) {
            this.autoApprovalUsers = parseCommaSeparated(autoApprovalUsers);
        }

        /**
         * Sets the list of GitHub user logins that are automatically trusted.
         *
         * @param autoApprovalUsers the GitHub login names to auto-approve.
         */
        public void setAutoApprovalUsersList(@CheckForNull List<String> autoApprovalUsers) {
            if (autoApprovalUsers == null || autoApprovalUsers.isEmpty()) {
                this.autoApprovalUsers = null;
            } else {
                this.autoApprovalUsers = Collections.unmodifiableList(new ArrayList<>(autoApprovalUsers));
            }
        }

        @CheckForNull
        private static List<String> parseCommaSeparated(@CheckForNull String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            List<String> result = new ArrayList<>();
            for (String entry : value.split(",")) {
                String trimmed = entry.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
            return result.isEmpty() ? null : Collections.unmodifiableList(result);
        }

        /** {@inheritDoc} */
        @Override
        protected boolean checkTrusted(@NonNull GitHubSCMSourceRequest request, @NonNull PullRequestSCMHead head)
                throws IOException, InterruptedException {
            if (autoApprovalUsers != null && autoApprovalUsers.contains(head.getSourceOwner())) {
                return true;
            }
            if (autoApprovalLabels != null && !autoApprovalLabels.isEmpty()) {
                for (GHPullRequest pr : request.getPullRequests()) {
                    if (pr.getNumber() != head.getNumber()) {
                        continue;
                    }
                    for (GHLabel label : pr.getLabels()) {
                        if (autoApprovalLabels.contains(label.getName())) {
                            return true;
                        }
                    }
                    break;
                }
            }
            return false;
        }

        /** Our descriptor. */
        @Symbol("gitHubTrustExternalApproval")
        @Extension
        public static class DescriptorImpl extends SCMHeadAuthorityDescriptor {

            /** {@inheritDoc} */
            @Override
            public String getDisplayName() {
                return Messages.ForkPullRequestDiscoveryTrait_externalApprovalDisplayName();
            }

            /** {@inheritDoc} */
            @Override
            public boolean isApplicableToOrigin(@NonNull Class<? extends SCMHeadOrigin> originClass) {
                return SCMHeadOrigin.Fork.class.isAssignableFrom(originClass);
            }

            @Restricted(NoExternalUse.class)
            @SuppressWarnings("unused") // stapler
            public FormValidation doCheckAutoApprovalUsers(@QueryParameter String value) {
                if (value == null || value.isBlank()) {
                    return FormValidation.ok();
                }
                for (String entry : value.split(",")) {
                    String trimmed = entry.trim();
                    if (trimmed.isEmpty()) {
                        continue;
                    }
                    if (trimmed.contains(" ")) {
                        return FormValidation.warning("GitHub logins should not contain spaces: '" + trimmed + "'");
                    }
                }
                return FormValidation.ok();
            }
        }
    }
}
