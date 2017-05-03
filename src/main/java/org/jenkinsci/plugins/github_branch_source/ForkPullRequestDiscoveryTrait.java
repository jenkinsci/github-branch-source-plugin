package org.jenkinsci.plugins.github_branch_source;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.scm.SCM;
import hudson.util.ListBoxModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jenkins.scm.api.SCMHeadCategory;
import jenkins.scm.api.SCMHeadOrigin;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import jenkins.scm.api.mixin.ChangeRequestSCMHead2;
import jenkins.scm.api.trait.SCMBuilder;
import jenkins.scm.api.trait.SCMHeadAuthority;
import jenkins.scm.api.trait.SCMHeadAuthorityDescriptor;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceRequest;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import jenkins.scm.impl.ChangeRequestSCMHeadCategory;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;

public class ForkPullRequestDiscoveryTrait extends SCMSourceTrait {
    private final int strategyId;
    private final SCMHeadAuthority<? super GitHubSCMSourceRequest, ? extends ChangeRequestSCMHead2, ? extends
            SCMRevision>
            trust;

    @DataBoundConstructor
    public ForkPullRequestDiscoveryTrait(int strategyId,
                                         SCMHeadAuthority<? super GitHubSCMSourceRequest, ? extends
                                                 ChangeRequestSCMHead2, ? extends SCMRevision> trust) {
        this.strategyId = strategyId;
        this.trust = trust;
    }

    public ForkPullRequestDiscoveryTrait(boolean buildMerge, boolean buildHead) {
        this.strategyId = (buildMerge ? 1 : 0) + (buildHead ? 2 : 0);
        this.trust = new TrustContributors();
    }

    public int getStrategyId() {
        return strategyId;
    }

    @Restricted(NoExternalUse.class)
    public boolean isPRMerge() {
        return (strategyId & 1) != 0;
    }

    @Restricted(NoExternalUse.class)
    public boolean isPRHead() {
        return (strategyId & 1) != 0;
    }

    public SCMHeadAuthority<? super GitHubSCMSourceRequest, ? extends ChangeRequestSCMHead2, ? extends SCMRevision> getTrust() {
        return trust;
    }

    @Override
    protected <B extends SCMBuilder<B, S>, S extends SCM> void decorateBuilder(B builder) {
        GitHubSCMSourceContext b = GitHubSCMSourceContext.class.cast(builder);
        b.wantForkPRs(true);
        if ((strategyId & 1) != 0) {
            b.withForkPRStrategies(Collections.singleton(ChangeRequestCheckoutStrategy.MERGE));
        }
        if ((strategyId & 2) != 0) {
            b.withForkPRStrategies(Collections.singleton(ChangeRequestCheckoutStrategy.HEAD));
        }
    }

    @Override
    public boolean includeCategory(@NonNull SCMHeadCategory category) {
        return category instanceof ChangeRequestSCMHeadCategory;
    }

    @Extension
    public static class DescriptorImpl extends SCMSourceTraitDescriptor {

        @Override
        public String getDisplayName() {
            return "Discover pull requests from forks";
        }

        @Override
        public boolean isApplicableToContext(Class<? extends SCMSourceContext> contextClass) {
            return GitHubSCMSourceRequest.class.isAssignableFrom(contextClass);
        }

        public ListBoxModel doFillStrategyIdItems() {
            ListBoxModel result = new ListBoxModel();
            result.add("Merging the pull request with the current target branch revision", "1");
            result.add("The current pull request revision", "2");
            result.add("Build the current pull request revision", "3");
            return result;
        }

        public List<SCMHeadAuthorityDescriptor> getTrustDescriptors() {
            List<SCMHeadAuthorityDescriptor> result = new ArrayList<>();
            for (SCMHeadAuthorityDescriptor d : ExtensionList.lookup(SCMHeadAuthorityDescriptor.class)) {
                if (d.isApplicableToRequest(GitHubSCMSourceRequest.class)
                        && d.isApplicableToHead(PullRequestSCMHead.class)) {
                    result.add(d);
                }
            }
            return result;
        }
    }


    public static class TrustContributors
            extends SCMHeadAuthority<GitHubSCMSourceRequest, PullRequestSCMHead, PullRequestSCMRevision> {
        @DataBoundConstructor
        public TrustContributors() {
        }


        @Override
        protected boolean checkTrusted(@NonNull GitHubSCMSourceRequest request, @NonNull PullRequestSCMHead head) {
            return !head.getOrigin().equals(SCMHeadOrigin.DEFAULT)
                    && request.getCollaboratorNames().contains(head.getSourceOwner());
        }

        @Extension
        public static class DescriptorImpl extends SCMHeadAuthorityDescriptor {

            @Override
            public String getDisplayName() {
                return "Contributors";
            }
        }
    }

    public static class TrustNobody extends SCMHeadAuthority<SCMSourceRequest, ChangeRequestSCMHead2, SCMRevision> {
        @DataBoundConstructor
        public TrustNobody() {
        }

        @Override
        public boolean checkTrusted(@NonNull SCMSourceRequest request, @NonNull ChangeRequestSCMHead2 head) {
            return false;
        }

        @Extension
        public static class DescriptorImpl extends SCMHeadAuthorityDescriptor {

            @Override
            public String getDisplayName() {
                return "Nobody";
            }
        }
    }

    public static class TrustEveryone extends SCMHeadAuthority<SCMSourceRequest, ChangeRequestSCMHead2, SCMRevision> {
        @DataBoundConstructor
        public TrustEveryone() {
        }

        @Override
        protected boolean checkTrusted(@NonNull SCMSourceRequest request, @NonNull ChangeRequestSCMHead2 head) {
            return !head.getOrigin().equals(SCMHeadOrigin.DEFAULT);
        }

        @Extension
        public static class DescriptorImpl extends SCMHeadAuthorityDescriptor {

            @Override
            public String getDisplayName() {
                return "Everyone";
            }
        }
    }
}
