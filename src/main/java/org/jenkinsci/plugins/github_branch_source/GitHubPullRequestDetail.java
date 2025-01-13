package org.jenkinsci.plugins.github_branch_source;

import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.model.Actionable;
import hudson.model.Run;
import jenkins.model.Detail;
import jenkins.model.DetailGroup;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMRevisionAction;
import jenkins.scm.api.metadata.ObjectMetadataAction;

public class GitHubPullRequestDetail extends Detail {
    public GitHubPullRequestDetail(Actionable object) {
        super(object);
    }

    @Nullable
    @Override
    public String getDisplayName() {
        var run = (Run<?, ?>)getObject();
        ObjectMetadataAction action = run.getParent().getAction(ObjectMetadataAction.class);
        return action.getObjectDisplayName();
    }

    @Override
    public boolean isApplicable() {
        SCMRevisionAction scmRevisionAction = getObject().getAction(SCMRevisionAction.class);
        SCMRevision revision = scmRevisionAction.getRevision();
        return revision instanceof PullRequestSCMRevision;
    }

    public String getUrl() {
        var run = (Run<?, ?>)getObject();
        GitHubLink repoLink = run.getParent().getAction(GitHubLink.class);
        return repoLink.getUrl();
    }

    @Override
    public DetailGroup getGroup() {
        return DetailGroup.SCM;
    }
}
