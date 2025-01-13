package org.jenkinsci.plugins.github_branch_source;

import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.model.Actionable;
import hudson.model.Run;
import jenkins.model.Detail;
import jenkins.model.DetailGroup;
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
        GitHubSCMSource source = (GitHubSCMSource) SCMSource.SourceByItem.findSource(((Run) getObject()).getParent());

        if (source == null) {
            return null;
        }

        return source.getRepoOwner() + "/" + source.getRepository();
    }

    @Override
    public String getUrl() {
        GitHubSCMSource source = (GitHubSCMSource) SCMSource.SourceByItem.findSource(((Run) getObject()).getParent());

        if (source == null) {
            return null;
        }

        // TODO - Has .git on the end
        return source.getRepositoryUrl();
    }

    @Override
    public boolean isApplicable() {
        var source = SCMSource.SourceByItem.findSource(((Run) getObject()).getParent());
        return source instanceof GitHubSCMSource;
    }

    @Override
    public DetailGroup getGroup() {
        return DetailGroup.SCM;
    }
}
