package org.jenkinsci.plugins.github_branch_source;

import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.model.Actionable;
import hudson.model.Run;
import jenkins.model.details.Detail;
import jenkins.model.details.DetailGroup;
import jenkins.scm.api.SCMDetailGroup;
import jenkins.scm.api.metadata.ObjectMetadataAction;

public class GitHubPullRequestDetail extends Detail {
    public GitHubPullRequestDetail(Actionable object) {
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
        var run = (Run<?, ?>) getObject();
        ObjectMetadataAction action = run.getParent().getAction(ObjectMetadataAction.class);
        return action.getObjectDisplayName();
    }

    @Override
    public String getLink() {
        var run = (Run<?, ?>) getObject();
        GitHubLink repoLink = run.getParent().getAction(GitHubLink.class);
        return repoLink.getUrl();
    }

    @Override
    public DetailGroup getGroup() {
        return SCMDetailGroup.get();
    }
}
