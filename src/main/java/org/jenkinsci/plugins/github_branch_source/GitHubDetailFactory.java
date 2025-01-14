package org.jenkinsci.plugins.github_branch_source;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Run;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import jenkins.model.Detail;
import jenkins.model.DetailFactory;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMRevisionAction;

@Extension
public final class GitHubDetailFactory extends DetailFactory<Run> {

    @Override
    public Class<Run> type() {
        return Run.class;
    }

    @NonNull
    @Override
    public Collection<? extends Detail> createFor(@NonNull Run target) {
        SCMRevisionAction scmRevisionAction = target.getAction(SCMRevisionAction.class);
        if (scmRevisionAction == null) {
            return Collections.emptyList();
        }

        List<Detail> details = new ArrayList<>();

        SCMRevision revision = scmRevisionAction.getRevision();

        if (revision instanceof PullRequestSCMRevision) {
            details.add(new GitHubPullRequestDetail(target));
        } else {
            details.add(new GitHubBranchDetail(target));
        }

        details.add(new GitHubCommitDetail(target));
        details.add(new GitHubRepositoryDetail(target));

        return details;
    }
}
