package org.jenkinsci.plugins.github_branch_source;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

import org.junit.Test;

public class GitHubSCMSourceHelperTest {

    @Test
    public void httpsUrl_non_github() {
        GitHubRepositoryInfo sut = GitHubRepositoryInfo.forRepositoryUrl("https://mygithub.com/jenkinsci/jenkins");
        assertThat(sut.getRepoOwner(), is("jenkinsci"));
        assertThat(sut.getRepository(), is("jenkins"));
        assertThat(sut.getRepositoryUrl(), is("https://mygithub.com/jenkinsci/jenkins"));
    }

    @Test
    public void httpsUrl_github() {
        GitHubRepositoryInfo sut = GitHubRepositoryInfo.forRepositoryUrl("https://github.com/jenkinsci/jenkins");
        assertThat(sut.getRepoOwner(), is("jenkinsci"));
        assertThat(sut.getRepository(), is("jenkins"));
        assertThat(sut.getRepositoryUrl(), is("https://github.com/jenkinsci/jenkins"));
    }
}
