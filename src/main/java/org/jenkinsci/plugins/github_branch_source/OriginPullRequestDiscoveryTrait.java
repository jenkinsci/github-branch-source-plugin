package org.jenkinsci.plugins.github_branch_source;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.scm.SCM;
import hudson.util.ListBoxModel;
import java.util.Collections;
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

public class OriginPullRequestDiscoveryTrait extends SCMSourceTrait {
    private int strategyId;

    @DataBoundConstructor
    public OriginPullRequestDiscoveryTrait(int strategyId) {
        this.strategyId = strategyId;
    }

    public OriginPullRequestDiscoveryTrait(boolean buildMerge, boolean buildHead) {
        this.strategyId = (buildMerge ? 1 : 0) + (buildHead ? 2 : 0);
    }

    public int getStrategyId() {
        return strategyId;
    }

    @Override
    protected <B extends SCMBuilder<B, S>, S extends SCM> void decorateBuilder(B builder) {
        GitHubSCMSourceContext b = GitHubSCMSourceContext.class.cast(builder);
        b.wantOriginPRs(true);
        b.withAuthority(new OriginChangeRequestSCMHeadAuthority());
        if ((strategyId & 1) != 0) {
            b.withOriginPRStrategies(Collections.singleton(ChangeRequestCheckoutStrategy.MERGE));
        }
        if ((strategyId & 2) != 0) {
            b.withOriginPRStrategies(Collections.singleton(ChangeRequestCheckoutStrategy.HEAD));
        }
    }

    @Override
    public boolean includeCategory(@NonNull SCMHeadCategory category) {
        return category instanceof ChangeRequestSCMHeadCategory;
    }

    @Restricted(NoExternalUse.class)
    public boolean isPRMerge() {
        return (strategyId & 1) != 0;
    }

    @Restricted(NoExternalUse.class)
    public boolean isPRHead() {
        return (strategyId & 1) != 0;
    }

    @Extension
    public static class DescriptorImpl extends SCMSourceTraitDescriptor {

        @Override
        public String getDisplayName() {
            return "Discover pull requests from origin";
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
    }

    public static class OriginChangeRequestSCMHeadAuthority
            extends SCMHeadAuthority<SCMSourceRequest, ChangeRequestSCMHead2, SCMRevision> {
        @Override
        protected boolean checkTrusted(@NonNull SCMSourceRequest request, @NonNull ChangeRequestSCMHead2 head) {
            return SCMHeadOrigin.DEFAULT.equals(head.getOrigin());
        }

        @Extension
        public static class DescriptorImpl extends SCMHeadAuthorityDescriptor {
            @Override
            public String getDisplayName() {
                return null;
            }

            @Override
            public boolean isApplicableToOrigin(@NonNull Class<? extends SCMHeadOrigin> originClass) {
                return SCMHeadOrigin.Default.class.isAssignableFrom(originClass);
            }
        }
    }
}
