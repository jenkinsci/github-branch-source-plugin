package org.jenkinsci.plugins.github_branch_source;

import hudson.model.Actionable;
import hudson.model.Run;
import jenkins.model.details.Detail;
import jenkins.model.details.DetailGroup;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.scm.api.SCMDetailGroup;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMRevisionAction;

public class GitHubCommitDetail extends Detail {
    public GitHubCommitDetail(Actionable object) {
        super(object);
    }

    public String getIconClassName() {
        return "symbol-git-commit-outline plugin-ionicons-api";
    }

    @Override
    public String getDisplayName() {
        SCMRevision revision = getRevision();

        if (revision == null) {
            return null;
        }

        if (revision instanceof AbstractGitSCMSource.SCMRevisionImpl abstractRevision) {
            return abstractRevision.getHash().substring(0, 7);
        }

        if (revision instanceof PullRequestSCMRevision pullRequestSCMRevision) {
            return pullRequestSCMRevision.getPullHash().substring(0, 7);
        }

        return null;
    }

    @Override
    public String getLink() {
        SCMRevision revision = getRevision();

        if (revision == null) {
            return null;
        }

        if (revision instanceof AbstractGitSCMSource.SCMRevisionImpl abstractRevision) {
            return new GitHubRepositoryDetail(getObject()).getLink() + "/commit/" + abstractRevision.getHash();
        }

        if (revision instanceof PullRequestSCMRevision pullRequestSCMRevision) {
            Run<?, ?> run = (Run<?, ?>) getObject();
            GitHubLink repoLink = run.getParent().getAction(GitHubLink.class);
            return repoLink.getUrl() + "/commits/" + pullRequestSCMRevision.getPullHash();
        }

        return null;
    }

    @Override
    public DetailGroup getGroup() {
        return SCMDetailGroup.get();
    }

    private SCMRevision getRevision() {
        SCMRevisionAction scmRevisionAction = getObject().getAction(SCMRevisionAction.class);

        if (scmRevisionAction == null) {
            return null;
        }

        return scmRevisionAction.getRevision();
    }
}
