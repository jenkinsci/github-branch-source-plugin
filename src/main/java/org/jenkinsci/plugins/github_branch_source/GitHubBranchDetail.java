package org.jenkinsci.plugins.github_branch_source;

import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.model.Actionable;
import hudson.model.Run;
import jenkins.model.Detail;
import jenkins.model.DetailGroup;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMRevisionAction;
import jenkins.scm.api.metadata.ObjectMetadataAction;

public class GitHubBranchDetail extends Detail {
    public GitHubBranchDetail(Actionable object) {
        super(object);
    }

    @Nullable
    @Override
    public String getIconClassName() {
        return "symbol-git-pull-request-outline plugin-ionicons-api";
    }

    @Nullable
    @Override
    public String getDisplayName() {
        SCMRevisionAction scmRevisionAction = getObject().getAction(SCMRevisionAction.class);
        SCMRevision revision = scmRevisionAction.getRevision();

        if (revision instanceof PullRequestSCMRevision pullRequestSCMRevision) {
            PullRequestSCMHead head = (PullRequestSCMHead) pullRequestSCMRevision.getHead();
            return head.getSourceBranch();
        }

        SCMHead head = revision.getHead();
        return head.getName();
    }

    @Override
    public String getUrl() {
        var run = (Run<?, ?>) getObject();
        ObjectMetadataAction action = run.getParent().getAction(ObjectMetadataAction.class);
        return action.getObjectUrl();
    }

    @Override
    public boolean isApplicable() {
        SCMRevisionAction scmRevisionAction = getObject().getAction(SCMRevisionAction.class);
        return scmRevisionAction != null;
    }

    @Override
    public DetailGroup getGroup() {
        return DetailGroup.SCM;
    }
}
