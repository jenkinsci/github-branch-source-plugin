package org.jenkinsci.plugins.github_branch_source;

import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.model.Actionable;
import jenkins.model.Detail;
import jenkins.model.DetailGroup;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMRevisionAction;

public class GitHubRepositoryDetail extends Detail {
    public GitHubRepositoryDetail(Actionable object) {
        super(object);
    }

    @Nullable
    @Override
    public String getDisplayName() {
        SCMRevisionAction scmRevisionAction = getObject().getAction(SCMRevisionAction.class);
        SCMRevision revision = scmRevisionAction.getRevision();

        if (revision instanceof PullRequestSCMRevision pullRequestSCMRevision) {
            PullRequestSCMHead head = (PullRequestSCMHead) pullRequestSCMRevision.getHead();
            String sourceOwner = head.getSourceOwner();
            String sourceRepo = head.getSourceRepo();

            return sourceOwner + "/" + sourceRepo;
        }

        return null;
    }

    public String getUrl() {
        SCMRevisionAction scmRevisionAction = getObject().getAction(SCMRevisionAction.class);
        SCMRevision revision = scmRevisionAction.getRevision();

        if (revision instanceof PullRequestSCMRevision pullRequestSCMRevision) {
            PullRequestSCMHead head = (PullRequestSCMHead) pullRequestSCMRevision.getHead();
            String sourceOwner = head.getSourceOwner();
            String sourceRepo = head.getSourceRepo();

            return "https://github.com/" + sourceOwner + "/" + sourceRepo;
        }

        return null;
    }

    @Override
    public DetailGroup getGroup() {
        return DetailGroup.SCM;
    }
}
