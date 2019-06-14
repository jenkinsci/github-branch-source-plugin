package org.jenkinsci.plugins.github_branch_source;

import org.junit.Test;

import java.net.MalformedURLException;
import java.net.URL;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

public class GitHubSCMSourceHelperTest {

    @Test
    public void github() throws MalformedURLException {
        GitHubSCMSource source = new GitHubSCMSource("jenkinsci", "jenkins", "");
        source.setApiUri("");
        GitHubSCMSourceHelper sut = GitHubSCMSourceHelper.build(source);

        assertThat(sut.uri, is("https://api.github.com"));
        assertThat(sut.repo, is("jenkinsci/jenkins"));
        assertThat(sut.owner, is("jenkinsci"));
        assertThat(sut.repoName, is("jenkins"));
        assertThat(sut.url, is(new URL("https://github.com/jenkinsci/jenkins")));
    }
    @Test
    public void githubEnterprise() throws MalformedURLException {
        GitHubSCMSource source = new GitHubSCMSource("jenkinsci", "jenkins", "");
        source.setApiUri("https://github.beescloud.com/api/v3");
        GitHubSCMSourceHelper sut = GitHubSCMSourceHelper.build(source);

        assertThat(sut.uri, is("https://github.beescloud.com/api/v3"));
        assertThat(sut.repo, is("jenkinsci/jenkins"));
        assertThat(sut.owner, is("jenkinsci"));
        assertThat(sut.repoName, is("jenkins"));
        assertThat(sut.url, is(new URL("https://github.beescloud.com/jenkinsci/jenkins")));
    }
    @Test
    public void rawUrl_non_github() throws MalformedURLException {
        GitHubSCMSource source = new GitHubSCMSource("", "", "https://mygithub.com/jenkinsci/jenkins");
        source.setApiUri("https://mygithub.com/api/v3");
        GitHubSCMSourceHelper sut = GitHubSCMSourceHelper.build(source);

        assertThat(sut.uri, is("https://mygithub.com/api/v3"));
        assertThat(sut.repo, is("jenkinsci/jenkins"));
        assertThat(sut.owner, is("jenkinsci"));
        assertThat(sut.repoName, is("jenkins"));
        assertThat(sut.url, is(new URL("https://mygithub.com/jenkinsci/jenkins")));
    }
    @Test
    public void rawUrl_non_github_dot_git() throws MalformedURLException {
        GitHubSCMSource source = new GitHubSCMSource("", "", "https://mygithub.com/jenkinsci/jenkins.git");
        source.setApiUri("https://mygithub.com/api/v3");
        GitHubSCMSourceHelper sut = GitHubSCMSourceHelper.build(source);

        assertThat(sut.uri, is("https://mygithub.com/api/v3"));
        assertThat(sut.repo, is("jenkinsci/jenkins"));
        assertThat(sut.owner, is("jenkinsci"));
        assertThat(sut.repoName, is("jenkins"));
        assertThat(sut.url, is(new URL("https://mygithub.com/jenkinsci/jenkins")));
    }

    @Test
    public void rawUrl_github() throws MalformedURLException {
        GitHubSCMSource source = new GitHubSCMSource("", "", "https://github.com/jenkinsci/jenkins");
        source.setApiUri("");
        GitHubSCMSourceHelper sut = GitHubSCMSourceHelper.build(source);

        assertThat(sut.uri, is("https://api.github.com"));
        assertThat(sut.repo, is("jenkinsci/jenkins"));
        assertThat(sut.owner, is("jenkinsci"));
        assertThat(sut.repoName, is("jenkins"));
        assertThat(sut.url, is(new URL("https://github.com/jenkinsci/jenkins")));
    }

    @Test
    public void rawUrl_github_dot_git() throws MalformedURLException {
        GitHubSCMSource source = new GitHubSCMSource("", "", "https://github.com/jenkinsci/jenkins.git");
        source.setApiUri("");
        GitHubSCMSourceHelper sut = GitHubSCMSourceHelper.build(source);

        assertThat(sut.uri, is("https://api.github.com"));
        assertThat(sut.repo, is("jenkinsci/jenkins"));
        assertThat(sut.owner, is("jenkinsci"));
        assertThat(sut.repoName, is("jenkins"));
        assertThat(sut.url, is(new URL("https://github.com/jenkinsci/jenkins")));
    }

}