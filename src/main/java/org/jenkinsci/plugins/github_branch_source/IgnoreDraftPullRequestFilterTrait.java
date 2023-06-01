package org.jenkinsci.plugins.github_branch_source;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import java.io.IOException;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.trait.SCMHeadFilter;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceRequest;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import jenkins.scm.impl.trait.Selection;
import org.jenkinsci.Symbol;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.stapler.DataBoundConstructor;

/** Trait used to filter any pull requests current set as a draft from building. */
public class IgnoreDraftPullRequestFilterTrait extends SCMSourceTrait {

    @DataBoundConstructor
    public IgnoreDraftPullRequestFilterTrait() {}

    protected void decorateContext(SCMSourceContext<?, ?> context) {
        context.withFilter(new SCMHeadFilter() {
            @Override
            public boolean isExcluded(@NonNull final SCMSourceRequest request, @NonNull final SCMHead head)
                    throws IOException {
                if (!(request instanceof GitHubSCMSourceRequest) || !(head instanceof PullRequestSCMHead)) {
                    return false;
                }
                GitHubSCMSourceRequest githubRequest = (GitHubSCMSourceRequest) request;
                PullRequestSCMHead prHead = (PullRequestSCMHead) head;
                for (GHPullRequest pullRequest : githubRequest.getPullRequests()) {
                    if (pullRequest.getNumber() != prHead.getNumber()) {
                        continue;
                    }
                    if (pullRequest.isDraft()) {
                        request.listener()
                                .getLogger()
                                .format("%n    Won't Build PR %s. Marked as a draft.%n", "#" + prHead.getNumber());
                        return true;
                    }
                    return false;
                }
                return false;
            }
        });
    }

    @Symbol({"gitHubIgnoreDraftPullRequestFilter"})
    @Extension
    @Selection
    public static class DescriptorImpl extends SCMSourceTraitDescriptor {
        public DescriptorImpl() {}

        public String getDisplayName() {
            return Messages.IgnoreDraftPullRequestFilterTrait_DisplayName();
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
