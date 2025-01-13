package org.jenkinsci.plugins.github_branch_source;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Run;
import jenkins.model.Detail;
import jenkins.model.DetailFactory;

import java.util.Collection;
import java.util.List;

@Extension
public final class DetailThingyFactory extends DetailFactory<Run> {

    @Override
    public Class<Run> type() {
        return Run.class;
    }

    @NonNull
    @Override public Collection<? extends Detail> createFor(@NonNull Run target) {
        return List.of(new GitHubPullRequestDetail(target), new GitHubBranchDetail(target), new GitHubRepositoryDetail(target));
    }
}
