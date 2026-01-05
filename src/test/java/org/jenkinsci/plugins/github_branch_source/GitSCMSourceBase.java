package org.jenkinsci.plugins.github_branch_source;

import java.util.Arrays;
import java.util.EnumSet;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

class GitSCMSourceBase extends AbstractGitHubWireMockTest {

    protected GitHubSCMSource source;
    protected GitHub github;
    protected GHRepository repo;

    @BeforeEach
    @Override
    void beforeEach() throws Exception {
        super.beforeEach();
        // force apiUri to point to test server
        source.forceApiUri("http://localhost:" + githubApi.getPort());
        source.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, true),
                new ForkPullRequestDiscoveryTrait(
                        EnumSet.of(ChangeRequestCheckoutStrategy.MERGE),
                        new ForkPullRequestDiscoveryTrait.TrustContributors())));
        github = Connector.connect("http://localhost:" + githubApi.getPort(), null);
        repo = github.getRepository("cloudbeers/yolo");
    }
}
