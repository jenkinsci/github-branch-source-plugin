package org.jenkinsci.plugins.github_branch_source;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.scm.SCM;
import hudson.util.ListBoxModel;
import jenkins.scm.api.trait.SCMBuilder;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadCategory;
import jenkins.scm.api.trait.SCMHeadAuthority;
import jenkins.scm.api.trait.SCMHeadAuthorityDescriptor;
import jenkins.scm.api.trait.SCMHeadFilter;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceRequest;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.stapler.DataBoundConstructor;

public class BranchDiscoveryTrait extends SCMSourceTrait {
    private int strategyId;

    @DataBoundConstructor
    public BranchDiscoveryTrait(int strategyId) {
        this.strategyId = strategyId;
    }

    public BranchDiscoveryTrait(boolean buildBranch, boolean buildBranchWithPr) {
        this.strategyId = (buildBranch ? 1 : 0) + (buildBranchWithPr ? 2 : 0);
    }

    public int getStrategyId() {
        return strategyId;
    }

    @Restricted(NoExternalUse.class)
    public boolean isBuildBranch() {
        return (strategyId & 1) != 0;

    }

    @Restricted(NoExternalUse.class)
    public boolean isBuildBranchesWithPR() {
        return (strategyId & 2) != 0;
    }

    @Override
    protected <B extends SCMBuilder<B, S>, S extends SCM> void decorateBuilder(B builder) {
        GitHubSCMSourceContext b = GitHubSCMSourceContext.class.cast(builder);
        b.wantBranches(true);
        b.withAuthority(new BranchSCMHeadAuthority());
        switch (strategyId) {
            case 1:
                b.wantOriginPRs(true);
                b.withFilter(new SCMHeadFilter() {
                    @Override
                    public boolean isExcluded(@NonNull SCMSourceRequest request, @NonNull SCMHead head) {
                        if (head instanceof BranchSCMHead && request instanceof GitHubSCMSourceRequest) {
                            for (GHPullRequest pullRequest: ((GitHubSCMSourceRequest) request).getPullRequests()) {
                                if (pullRequest.getHead().getRef().equals(head.getName())) {
                                    // TODO correct is also a PR test
                                    return true;
                                }
                            }
                        }
                        return false;
                    }
                });
                break;
            case 2:
                b.wantOriginPRs(true);
                b.withFilter(new SCMHeadFilter() {
                    @Override
                    public boolean isExcluded(@NonNull SCMSourceRequest request, @NonNull SCMHead head) {
                        if (head instanceof BranchSCMHead && request instanceof GitHubSCMSourceRequest) {
                            for (GHPullRequest pullRequest : ((GitHubSCMSourceRequest) request).getPullRequests()) {
                                if (!pullRequest.getHead().getRef().equals(head.getName())) {
                                    // TODO correct is also a PR test
                                    return true;
                                }
                            }
                        }
                        return false;
                    }
                });
                break;
            case 3:
            default:
                // we don't care if it is a PR or not, we're taking them all, no need to ask for PRs and no need
                // to filter
                break;

        }
    }

    @Override
    public boolean includeCategory(@NonNull SCMHeadCategory category) {
        return category.isUncategorized();
    }

    @Extension
    public static class DescriptorImpl extends SCMSourceTraitDescriptor {

        @Override
        public String getDisplayName() {
            return "Discover branches";
        }

        @Override
        public boolean isApplicableToContext(Class<? extends SCMSourceContext> contextClass) {
            return GitHubSCMSourceRequest.class.isAssignableFrom(contextClass);
        }

        public ListBoxModel doFillStrategyIdItems() {
            ListBoxModel result = new ListBoxModel();
            result.add("Only branches that are not also filed as PRs", "1");
            result.add("Only branches that are also filed as PRs", "2");
            result.add("All branches", "3");
            return result;
        }
    }

    public static class BranchSCMHeadAuthority extends SCMHeadAuthority<SCMSourceRequest,BranchSCMHead> {
        @Override
        protected boolean checkTrusted(@NonNull SCMSourceRequest request, @NonNull BranchSCMHead head) {
            return true;
        }

        @Extension
        public static class DescriptorImpl extends SCMHeadAuthorityDescriptor {
            @Override
            public String getDisplayName() {
                return null;
            }
        }
    }
}
