package org.jenkinsci.plugins.github_branch_source;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.scm.api.trait.SCMSourceBuilder;

/**
 * @author Stephen Connolly
 */
public class GitHubSCMSourceBuilder extends SCMSourceBuilder<GitHubSCMSourceBuilder, GitHubSCMSource> {
    @NonNull
    private final String id;
    private final String apiUri;
    @NonNull
    private final String repoOwner;
    private String credentialsId;
    public GitHubSCMSourceBuilder(@NonNull String id, @CheckForNull String apiUri, @CheckForNull String credentialsId,
                                  @NonNull String repoOwner,@NonNull String repoName) {
        super(GitHubSCMSource.class, repoName);
        this.id = id;
        this.apiUri = apiUri;
        this.repoOwner = repoOwner;
        this.credentialsId = credentialsId;
    }

    @NonNull
    public final String id() {
        return id;
    }

    public final String apiUri() {
        return apiUri;
    }

    @NonNull
    public final String repoOwner() {
        return repoOwner;
    }

    public final String credentialsId() {
        return credentialsId;
    }

    public final GitHubSCMSourceBuilder withCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
        return this;
    }

    @NonNull
    @Override
    public GitHubSCMSource build() {
        GitHubSCMSource result = new GitHubSCMSource(repoOwner, projectName());
        result.setId(id());
        result.setApiUri(apiUri());
        result.setCredentialsId(credentialsId());
        result.setTraits(traits());
        return result;
    }
}
