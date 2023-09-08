package org.jenkinsci.plugins.github_branch_source;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import io.jenkins.plugins.casc.misc.RoundTripAbstractTest;
import org.jenkinsci.plugins.workflow.libs.GlobalLibraries;
import org.jenkinsci.plugins.workflow.libs.LibraryConfiguration;
import org.jenkinsci.plugins.workflow.libs.SCMSourceRetriever;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.RestartableJenkinsRule;

public class GitHubBranchSourcesJCasCCompatibilityTest extends RoundTripAbstractTest {

    @Issue("JENKINS-57557")
    @Override
    protected void assertConfiguredAsExpected(RestartableJenkinsRule restartableJenkinsRule, String s) {
        assertEquals(1, GlobalLibraries.get().getLibraries().size());
        final LibraryConfiguration library =
                GlobalLibraries.get().getLibraries().get(0);
        assertEquals("jenkins-pipeline-lib", library.getName());
        final SCMSourceRetriever retriever = (SCMSourceRetriever) library.getRetriever();
        final GitHubSCMSource scm = (GitHubSCMSource) retriever.getScm();
        assertEquals("e43d6600-ba0e-46c5-8eae-3989bf654055", scm.getId());
        assertEquals("jenkins-infra", scm.getRepoOwner());
        assertEquals("pipeline-library", scm.getRepository());
        assertEquals(3, scm.getTraits().size());
        final BranchDiscoveryTrait branchDiscovery =
                (BranchDiscoveryTrait) scm.getTraits().get(0);
        assertEquals(1, branchDiscovery.getStrategyId());
        final OriginPullRequestDiscoveryTrait prDiscovery =
                (OriginPullRequestDiscoveryTrait) scm.getTraits().get(1);
        assertEquals(2, prDiscovery.getStrategyId());
        final ForkPullRequestDiscoveryTrait forkDiscovery =
                (ForkPullRequestDiscoveryTrait) scm.getTraits().get(2);
        assertEquals(3, forkDiscovery.getStrategyId());
        assertThat(forkDiscovery.getTrust(), instanceOf(ForkPullRequestDiscoveryTrait.TrustPermission.class));
    }

    @Override
    protected String stringInLogExpected() {
        return "Setting class org.jenkinsci.plugins.github_branch_source.GitHubSCMSource.repoOwner = jenkins-infra";
    }
}
