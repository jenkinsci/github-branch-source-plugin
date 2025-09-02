package org.jenkinsci.plugins.github_branch_source.app_credentials;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.util.List;
import org.jenkinsci.plugins.github_branch_source.GitHubAppUsageContext;
import org.junit.Test;

public class AccessSpecifiedRepositoriesTest {

    private final RepositoryAccessStrategy strategy =
            new AccessSpecifiedRepositories("owner", List.of("repo-one", "repo-two"));

    @Test
    public void smokes() {
        assertThat(
                strategy.forContext(GitHubAppUsageContext.builder()
                        .inferredOwner("inferred-owner")
                        .inferredRepository("inferred-repo")
                        .build()),
                equalTo(new AccessibleRepositories("owner", List.of("repo-one", "repo-two"))));
    }

    @Test
    public void trustedUsageAllowsArbitraryRepositoryAccess() {
        assertThat(
                strategy.forContext(GitHubAppUsageContext.builder()
                        .inferredOwner("inferred-owner")
                        .inferredRepository("inferred-repo")
                        .trust()
                        .build()),
                equalTo(new AccessibleRepositories("owner", List.of("repo-one", "repo-two"))));
    }
}
