package org.jenkinsci.plugins.github_branch_source;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

class GitHubSCMSourceHelperTest {

    @Test
    void httpsUrl_non_github() {
        GitHubRepositoryInfo sut = GitHubRepositoryInfo.forRepositoryUrl("https://mygithub.com/jenkinsci/jenkins");
        assertThat(sut.getRepoOwner(), is("jenkinsci"));
        assertThat(sut.getRepository(), is("jenkins"));
        assertThat(sut.getRepositoryUrl(), is("https://mygithub.com/jenkinsci/jenkins"));
    }

    @Test
    void httpsUrl_github() {
        GitHubRepositoryInfo sut = GitHubRepositoryInfo.forRepositoryUrl("https://github.com/jenkinsci/jenkins");
        assertThat(sut.getRepoOwner(), is("jenkinsci"));
        assertThat(sut.getRepository(), is("jenkins"));
        assertThat(sut.getRepositoryUrl(), is("https://github.com/jenkinsci/jenkins"));
    }
}
