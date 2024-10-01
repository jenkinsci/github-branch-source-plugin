package org.jenkinsci.plugins.github_branch_source;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import hudson.model.Action;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import jenkins.branch.BranchSource;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.Before;
import org.junit.Test;

public class GithubAppCredentialsContextualizationTest extends AbstractGitHubWireMockTest {

    @Before
    public void setUpWireMock() throws Exception {
        GitHubConfiguration.get().setApiRateLimitChecker(ApiRateLimitChecker.ThrottleOnOver);

        // Add wiremock responses for App, App Installation, and App Installation Token
        githubApi.stubFor(get(urlEqualTo("/app"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBodyFile("../AppCredentials/files/body-mapping-githubapp-app.json")));
        githubApi.stubFor(get(urlEqualTo("/app/installations"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBodyFile("../AppCredentials/files/body-mapping-githubapp-installations.json")));
        githubApi.stubFor(post(urlEqualTo("/app/installations/654321/access_tokens"))
                .withRequestBody(equalToJson(
                        "{\"permissions\":{\"pull_requests\":\"write\",\"metadata\":\"read\",\"checks\":\"write\",\"contents\":\"read\"}}",
                        true,
                        false))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBody("{\"token\":\"super-secret-token\",\"expires_at\":\"" + createTokenExpiration()
                                + "\"}")));

        // Add wiremock responses for Repository
        githubApi.stubFor(get(urlEqualTo("/repos/cloudbeers/multibranch-demo"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBodyFile("../contextualization/body-repos-cloudbeers-multibranch-demo.json")));
        githubApi.stubFor(get(urlEqualTo("/repos/cloudbeers/multibranch-demo/branches"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBodyFile("../contextualization/body-repos-cloudbeers-multibranch-demo-branches.json")));
        githubApi.stubFor(get(urlEqualTo("/repos/cloudbeers/multibranch-demo/contents/?ref=refs%2Fheads%2Fmaster"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBodyFile("../contextualization/body-repos-cloudbeers-multibranch-demo-contents.json")));
        githubApi.stubFor(get(urlEqualTo("/repos/cloudbeers/multibranch-demo/pulls?state=open"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBodyFile("../contextualization/body-repos-cloudbeers-multibranch-demo-pulls.json")));
    }

    @Test
    public void ownerMustBeInferedFromRepository() throws Exception {
        final var store = CredentialsProvider.lookupStores(r.jenkins).iterator().next();

        final var credentials = GitHubApp.createCredentials("myAppCredentialsWithoutOwner");
        store.addCredentials(Domain.global(), credentials);
        credentials.setApiUri(githubApi.baseUrl());

        final var scmSource = new GitHubSCMSource("cloudbeers", "multibranch-demo", null, false);
        scmSource.setTraits(Arrays.asList(new BranchDiscoveryTrait(true, true)));
        scmSource.setCredentialsId(credentials.getId());
        scmSource.setApiUri(githubApi.baseUrl());

        final var multiBranchProject = r.jenkins.createProject(WorkflowMultiBranchProject.class, "multibranch-demo");
        multiBranchProject.getSourcesList().add(new BranchSource(scmSource));
        multiBranchProject.scheduleBuild2(0, new Action[0]).getFuture().get();

        final var branchProject = multiBranchProject.getItem("master");
        assertThat(branchProject, notNullValue());
    }

    private String createTokenExpiration() {
        // This token will go stale at the soonest allowed time but will not
        // expire for the duration of the test
        // Format: 2019-08-10T05:54:58Z
        return DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(new Date(System.currentTimeMillis()
                                + Duration.ofMinutes(10).toMillis())
                        .getTime())
                .truncatedTo(ChronoUnit.SECONDS));
    }
}
