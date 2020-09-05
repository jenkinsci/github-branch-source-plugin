package org.jenkinsci.plugins.github_branch_source;

import com.cloudbees.plugins.credentials.*;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.model.Item;
import jenkins.model.Jenkins;
import jenkins.plugins.git.GitToolChooser;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mockito;

import java.util.Collections;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@RunWith(Parameterized.class)
public class GitHubRepoSizeEstimatorTest extends GitSCMSourceBase {

    public GitHubRepoSizeEstimatorTest(GitHubSCMSource source) {
        this.source = source;
    }

    @Parameterized.Parameters(name = "{index}: revision={0}")
    public static GitHubSCMSource[] revisions() {
        return new GitHubSCMSource[]{
                new GitHubSCMSource("cloudbeers", "yolo", null, false),
                new GitHubSCMSource("", "", "https://github.com/cloudbeers/yolo", true)
        };
    }

    @Rule
    public final JenkinsRule j = new JenkinsRule();

    private CredentialsStore store = null;

    @Before
    public void enableSystemCredentialsProvider() {
        SystemCredentialsProvider.getInstance().setDomainCredentialsMap(
                Collections.singletonMap(Domain.global(), Collections.<Credentials>emptyList()));
        for (CredentialsStore s : CredentialsProvider.lookupStores(Jenkins.get())) {
            if (s.getProvider() instanceof SystemCredentialsProvider.ProviderImpl) {
                store = s;
                break;
            }
        }
        assertThat("The system credentials provider is enabled", store, notNullValue());
    }

    @Test
    public void isApplicableToTest() throws Exception {
        GitHubRepoSizeEstimator.RepositorySizeGithubAPI api =  j.jenkins.getExtensionList(GitToolChooser.RepositorySizeAPI.class).get(GitHubRepoSizeEstimator.RepositorySizeGithubAPI.class);
        Item context = Mockito.mock(Item.class);
        store.addCredentials(Domain.global(), createCredential(CredentialsScope.GLOBAL, "github"));
        store.save();

        githubApi.stubFor(
                get(urlEqualTo("/"))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json; charset=utf-8")
                                        .withBodyFile("../__files/body-(root)-XwEI7.json")));

        assertThat(api.isApplicableTo(githubApi.baseUrl(), context, "github"), is(false));
    }

    private StandardCredentials createCredential(CredentialsScope scope, String id) {
        return new UsernamePasswordCredentialsImpl(scope, id, "desc: " + id, "username", "password");
    }
}
