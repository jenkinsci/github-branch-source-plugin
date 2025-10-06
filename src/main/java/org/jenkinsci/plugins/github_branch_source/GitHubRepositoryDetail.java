package org.jenkinsci.plugins.github_branch_source;

import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.model.Actionable;
import hudson.model.Run;
import jenkins.model.details.Detail;
import jenkins.model.details.DetailGroup;
import jenkins.scm.api.SCMDetailGroup;
import jenkins.scm.api.SCMSource;

public class GitHubRepositoryDetail extends Detail {
    public GitHubRepositoryDetail(Actionable object) {
        super(object);
    }

    @Nullable
    @Override
    public String getIconClassName() {
        return "symbol-logo-github plugin-ionicons-api";
    }

    @Nullable
    @Override
    public String getDisplayName() {
        GitHubSCMSource source = getSCMSource();

        if (source == null) {
            return null;
        }

        return source.getRepoOwner() + "/" + source.getRepository();
    }

    @Override
    public String getLink() {
        GitHubSCMSource source = getSCMSource();

        if (source == null) {
            return null;
        }

        return source.getRepositoryUrl();
    }

    @Override
    public DetailGroup getGroup() {
        return SCMDetailGroup.get();
    }

    private GitHubSCMSource getSCMSource() {
        var source = SCMSource.SourceByItem.findSource(((Run) getObject()).getParent());

        if (source instanceof GitHubSCMSource githubSource) {
            return githubSource;
        }

        return null;
    }
}
