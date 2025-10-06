package org.jenkinsci.plugins.github_branch_source;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Run;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jenkins.model.details.Detail;
import jenkins.model.details.DetailFactory;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMRevisionAction;
import jenkins.scm.api.SCMSource;

@Extension
public final class GitHubDetailFactory extends DetailFactory<Run> {

    @Override
    public Class<Run> type() {
        return Run.class;
    }

    @NonNull
    @Override
    public List<? extends Detail> createFor(@NonNull Run target) {
        SCMSource src = SCMSource.SourceByItem.findSource(target.getParent());

        // Don't add details for non-GitHub SCM sources
        if (!(src instanceof GitHubSCMSource)) {
            return Collections.emptyList();
        }

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
