package org.jenkinsci.plugins.github_branch_source;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Job;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jenkins.model.details.Detail;
import jenkins.model.details.DetailFactory;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.metadata.ObjectMetadataAction;

/**
 * The job level counterpart to {@link GitHubDetailFactory}.
 */
@Extension
public final class GitHubJobDetailFactory extends DetailFactory<Job> {

    @Override
    public Class<Job> type() {
        return Job.class;
    }

    @NonNull
    @Override
    public List<? extends Detail> createFor(@NonNull Job target) {
        // Don't add details for non-GitHub SCM sources
        if (!(SCMSource.SourceByItem.findSource(target) instanceof GitHubSCMSource)) {
            return Collections.emptyList();
        }

        List<Detail> details = new ArrayList<>();
        details.add(new GitHubRepositoryDetail(target));

        if (target.getAction(ObjectMetadataAction.class) != null) {
            if (SCMHead.HeadByItem.findHead(target) instanceof PullRequestSCMHead) {
                details.add(new GitHubPullRequestDetail(target));
            } else {
                details.add(new GitHubBranchDetail(target));
            }
        }

        return details;
    }
}
