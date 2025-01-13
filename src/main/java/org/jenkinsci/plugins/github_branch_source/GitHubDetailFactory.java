package org.jenkinsci.plugins.github_branch_source;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.model.Run;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import jenkins.model.Detail;
import jenkins.model.DetailFactory;
import jenkins.model.DetailGroup;
import jenkins.plugins.git.AbstractGitSCMSource;
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
    public Collection<? extends Detail> createFor(@NonNull Run target) {

//        SCMRevisionAction scmRevisionAction = target.getAction(SCMRevisionAction.class);
//        if (scmRevisionAction == null) {
//            return Collections.emptyList();
//        }
//
//        List<Detail> details = new ArrayList<>();
//        return details;

        return List.of(
                new Detail(target) {
                    @Override
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
                    public String getUrl() {
                        SCMRevisionAction scmRevisionAction = getObject().getAction(SCMRevisionAction.class);
                        SCMRevision revision = scmRevisionAction.getRevision();

                        if (revision instanceof AbstractGitSCMSource.SCMRevisionImpl abstractRevision) {
                            GitHubSCMSource src = (GitHubSCMSource) SCMSource.SourceByItem.findSource(((Run)getObject()).getParent());
                            // TODO - ends with .git
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
                        return DetailGroup.SCM;
                    }
                },
        new GitHubPullRequestDetail(target),
                new GitHubBranchDetail(target),
                new GitHubRepositoryDetail(target));
    }
}
