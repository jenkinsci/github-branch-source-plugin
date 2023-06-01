package org.jenkinsci.plugins.github_branch_source;

import java.util.Arrays;
import java.util.EnumSet;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import org.junit.Before;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

public class GitSCMSourceBase extends AbstractGitHubWireMockTest {

    GitHubSCMSource source;
    GitHub github;
    GHRepository repo;

    @Before
    public void setupSourceTests() throws Exception {
        super.prepareMockGitHub();
        // force apiUri to point to test server
        source.forceApiUri("http://localhost:" + githubApi.port());
        source.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, true),
                new ForkPullRequestDiscoveryTrait(
                        EnumSet.of(ChangeRequestCheckoutStrategy.MERGE),
                        new ForkPullRequestDiscoveryTrait.TrustContributors())));
        github = Connector.connect("http://localhost:" + githubApi.port(), null);
        repo = github.getRepository("cloudbeers/yolo");
    }
}
