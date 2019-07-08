package org.jenkinsci.plugins.github_branch_source;

import org.junit.Test;

import java.net.MalformedURLException;
import java.net.URL;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

public class GitHubSCMSourceHelperTest {

    @Test
    public void github() throws MalformedURLException {
        GitHubSCMSource source = new GitHubSCMSource("jenkinsci", "jenkins", "", "scan");
        source.setApiUri("");
        GitHubSCMSourceHelper sut = GitHubSCMSourceHelper.build(source);

        assertThat(sut.apiURI, is("https://api.github.com"));
        assertThat(sut.repo, is("jenkinsci/jenkins"));
        assertThat(sut.owner, is("jenkinsci"));
        assertThat(sut.repoName, is("jenkins"));
        assertThat(sut.url, is(new URL("https://github.com/jenkinsci/jenkins")));
    }
    @Test
    public void githubEnterprise() throws MalformedURLException {
        GitHubSCMSource source = new GitHubSCMSource("jenkinsci", "jenkins", "", "scan");
        source.setApiUri("https://github.beescloud.com/api/v3");
        GitHubSCMSourceHelper sut = GitHubSCMSourceHelper.build(source);

        assertThat(sut.apiURI, is("https://github.beescloud.com/api/v3"));
        assertThat(sut.repo, is("jenkinsci/jenkins"));
        assertThat(sut.owner, is("jenkinsci"));
        assertThat(sut.repoName, is("jenkins"));
        assertThat(source.getRepoOwner(), is("jenkinsci"));
        assertThat(sut.url, is(new URL("https://github.beescloud.com/jenkinsci/jenkins")));
    }
    @Test
    public void rawUrl_non_github() throws MalformedURLException {
        GitHubSCMSource source = new GitHubSCMSource("", "", "https://mygithub.com/jenkinsci/jenkins", "raw");
        source.setApiUri("https://mygithub.com/api/v3");
        GitHubSCMSourceHelper sut = GitHubSCMSourceHelper.build(source);

        assertThat(sut.apiURI, is("https://mygithub.com/api/v3"));
        assertThat(sut.repo, is("jenkinsci/jenkins"));
        assertThat(sut.owner, is("jenkinsci"));
        assertThat(source.getRepoOwnerInternal(), is(""));
        assertThat(source.getRepoOwner(), is("jenkinsci"));
        assertThat(sut.repoName, is("jenkins"));
        assertThat(sut.url, is(new URL("https://mygithub.com/jenkinsci/jenkins")));
    }
    @Test
    public void rawUrl_non_github_dot_git() throws MalformedURLException {
        GitHubSCMSource source = new GitHubSCMSource("", "", "https://mygithub.com/jenkinsci/jenkins.git", "raw");
        source.setApiUri("https://mygithub.com/api/v3");
        GitHubSCMSourceHelper sut = GitHubSCMSourceHelper.build(source);

        assertThat(sut.apiURI, is("https://mygithub.com/api/v3"));
        assertThat(sut.repo, is("jenkinsci/jenkins"));
        assertThat(sut.owner, is("jenkinsci"));
        assertThat(sut.repoName, is("jenkins"));
        assertThat(source.getRepoOwner(), is("jenkinsci"));
        assertThat(sut.url, is(new URL("https://mygithub.com/jenkinsci/jenkins")));
    }

    @Test
    public void rawUrl_github() throws MalformedURLException {
        GitHubSCMSource source = new GitHubSCMSource("", "", "https://github.com/jenkinsci/jenkins", "raw");
        source.setApiUri("");
        GitHubSCMSourceHelper sut = GitHubSCMSourceHelper.build(source);

        assertThat(sut.apiURI, is("https://api.github.com"));
        assertThat(sut.repo, is("jenkinsci/jenkins"));
        assertThat(sut.owner, is("jenkinsci"));
        assertThat(source.getRepoOwner(), is("jenkinsci"));
        assertThat(sut.repoName, is("jenkins"));
        assertThat(sut.url, is(new URL("https://github.com/jenkinsci/jenkins")));
    }

    @Test
    public void rawUrl_github_dot_git() throws MalformedURLException {
        GitHubSCMSource source = new GitHubSCMSource("", "", "https://github.com/jenkinsci/jenkins.git", "raw");
        source.setApiUri("");
        GitHubSCMSourceHelper sut = GitHubSCMSourceHelper.build(source);

        assertThat(sut.apiURI, is("https://api.github.com"));
        assertThat(sut.repo, is("jenkinsci/jenkins"));
        assertThat(sut.owner, is("jenkinsci"));
        assertThat(source.getRepoOwner(), is("jenkinsci"));
        assertThat(sut.repoName, is("jenkins"));
        assertThat(sut.url, is(new URL("https://github.com/jenkinsci/jenkins")));
    }

}