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
        return getObjectMetadataAction().getObjectDisplayName();
    }

    @Override
    public String getLink() {
        return getObjectMetadataAction().getObjectUrl();
    }

    @Override
    public DetailGroup getGroup() {
        return SCMDetailGroup.get();
    }

    private ObjectMetadataAction getObjectMetadataAction() {
        Run<?, ?> run = (Run<?, ?>) getObject();
        return run.getParent().getAction(ObjectMetadataAction.class);
    }
}
