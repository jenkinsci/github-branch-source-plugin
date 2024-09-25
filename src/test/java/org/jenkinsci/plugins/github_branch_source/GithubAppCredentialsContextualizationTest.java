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
import hudson.model.Cause;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import jenkins.branch.BranchSource;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProjectTest;
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
                        .withBody("{\n"
                                + "  \"token\": \"super-secret-token\",\n"
                                +
                                // This token will go stale at the soonest allowed time but will not
                                // expire for the duration of the test
                                "  \"expires_at\": \""
                                + printDate(new Date(System.currentTimeMillis()
                                        + Duration.ofMinutes(10).toMillis()))
                                + "\""
                                + // 2019-08-10T05:54:58Z
                                "}")));
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
        multiBranchProject.setSourcesList(Collections.singletonList(new BranchSource(scmSource)));
        multiBranchProject.scheduleBuild(new Cause.UserIdCause());

        r.waitUntilNoActivity();

        final var branchProject =
                WorkflowMultiBranchProjectTest.scheduleAndFindBranchProject(multiBranchProject, "master");
        assertThat(branchProject, notNullValue());
    }

    static String printDate(Date dt) {
        return DateTimeFormatter.ISO_INSTANT.format(
                Instant.ofEpochMilli(dt.getTime()).truncatedTo(ChronoUnit.SECONDS));
    }
}
