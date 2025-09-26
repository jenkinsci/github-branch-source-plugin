package org.jenkinsci.plugins.github_branch_source;

import hudson.model.Actionable;
import hudson.model.Run;
import jenkins.model.details.Detail;
import jenkins.model.details.DetailGroup;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.scm.api.SCMDetailGroup;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMRevisionAction;
import jenkins.scm.api.SCMSource;

public class GitHubCommitDetail extends Detail {
    public GitHubCommitDetail(Actionable object) {
        super(object);
    }

    public String getIconClassName() {
        return "symbol-git-commit-outline plugin-ionicons-api";
    }

    @Override
    public String getDisplayName() {
        SCMRevisionAction scmRevisionAction = getObject().getAction(SCMRevisionAction.class);
        SCMRevision revision = scmRevisionAction.getRevision();

        if (revision instanceof AbstractGitSCMSource.SCMRevisionImpl abstractRevision) {
            return abstractRevision.getHash().substring(0, 7);
        } else if (revision instanceof PullRequestSCMRevision pullRequestSCMRevision) {
            return pullRequestSCMRevision.getPullHash().substring(0, 7);
        }

        return null;
    }

    @Override
    public String getLink() {
        SCMRevisionAction scmRevisionAction = getObject().getAction(SCMRevisionAction.class);
        SCMRevision revision = scmRevisionAction.getRevision();

        if (revision instanceof AbstractGitSCMSource.SCMRevisionImpl abstractRevision) {
            GitHubSCMSource src = (GitHubSCMSource) SCMSource.SourceByItem.findSource(((Run) getObject()).getParent());

            if (src == null) {
                return null;
            }

            return src.getRepositoryUrl() + "/commit/" + abstractRevision.getHash();
        } else if (revision instanceof PullRequestSCMRevision pullRequestSCMRevision) {
            var run = (Run<?, ?>) getObject();
            GitHubLink repoLink = run.getParent().getAction(GitHubLink.class);
            return repoLink.getUrl() + "/commits/" + pullRequestSCMRevision.getPullHash();
        }

        return null;
    }

    @Override
    public DetailGroup getGroup() {
        return SCMDetailGroup.get();
    }
}
