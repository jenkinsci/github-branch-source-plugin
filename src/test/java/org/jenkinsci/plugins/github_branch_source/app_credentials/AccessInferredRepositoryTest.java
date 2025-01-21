package org.jenkinsci.plugins.github_branch_source.app_credentials;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;
import org.jenkinsci.plugins.github_branch_source.GitHubAppUsageContext;
import org.junit.Test;

public class AccessInferredRepositoryTest {

    private final RepositoryAccessStrategy strategy = new AccessInferredRepository();

    @Test
    public void smokes() {
        final var strategy = new AccessInferredRepository();
        assertThat(
                strategy.forContext(GitHubAppUsageContext.builder()
                        .inferredOwner("inferred-owner")
                        .inferredRepository("inferred-repo")
                        .build()),
                equalTo(new AccessibleRepositories("inferred-owner", List.of("inferred-repo"))));
    }

    @Test
    public void constrainedUsageAllowsMultiRepositoryAccess() {
        final var strategy = new AccessInferredRepository();

        assertThat(
                strategy.forContext(GitHubAppUsageContext.builder()
                        .inferredOwner("inferred-owner")
                        .trust()
                        .build()),
                equalTo(new AccessibleRepositories("inferred-owner")));
        assertThat(
                strategy.forContext(GitHubAppUsageContext.builder()
                        .inferredOwner("inferred-owner")
                        .inferredRepository("inferred-repo-fake")
                        .trust()
                        .build()),
                equalTo(new AccessibleRepositories("inferred-owner")));
    }

    @Test
    public void requiresInferredOwner() {
        assertThat(strategy.forContext(GitHubAppUsageContext.builder().build()), nullValue());
        assertThat(
                strategy.forContext(GitHubAppUsageContext.builder()
                        .inferredRepository("inferred-repo")
                        .build()),
                nullValue());
        assertThat(
                strategy.forContext(GitHubAppUsageContext.builder()
                        .inferredRepository("inferred-repo")
                        .trust()
                        .build()),
                nullValue());
    }

    @Test
    public void requiresInferredRepository() {
        assertThat(
                strategy.forContext(GitHubAppUsageContext.builder()
                        .inferredOwner("inferred-owner")
                        .build()),
                nullValue());
    }
}
