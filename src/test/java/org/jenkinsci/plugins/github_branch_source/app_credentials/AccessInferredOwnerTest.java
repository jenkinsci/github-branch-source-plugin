package org.jenkinsci.plugins.github_branch_source.app_credentials;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import org.jenkinsci.plugins.github_branch_source.GitHubAppUsageContext;
import org.junit.Test;

public class AccessInferredOwnerTest {

    private final RepositoryAccessStrategy strategy = new AccessInferredOwner();

    @Test
    public void smokes() {
        assertThat(
                strategy.forContext(GitHubAppUsageContext.builder()
                        .inferredOwner("inferred-owner")
                        .inferredRepository("inferred-repo")
                        .build()),
                equalTo(new AccessibleRepositories("inferred-owner")));
    }

    @Test
    public void requiresInferredOwner() {
        assertThat(strategy.forContext(GitHubAppUsageContext.builder().build()), nullValue());
        assertThat(
                strategy.forContext(GitHubAppUsageContext.builder()
                        .inferredRepository("inferred-repository")
                        .build()),
                nullValue());
    }
}
