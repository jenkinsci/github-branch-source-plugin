package org.jenkinsci.plugins.github_branch_source.app_credentials;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.coravy.hudson.plugins.github.GithubProjectProperty;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import hudson.ProxyConfiguration;
import hudson.model.InvisibleAction;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.net.URI;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.github_branch_source.AbstractGitHubWireMockTest;
import org.jenkinsci.plugins.github_branch_source.ApiRateLimitChecker;
import org.jenkinsci.plugins.github_branch_source.GitHubApp;
import org.jenkinsci.plugins.github_branch_source.GitHubAppCredentials;
import org.jenkinsci.plugins.github_branch_source.GitHubConfiguration;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.StepExecutions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.BuildWatcherExtension;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * This creates a pipeline job that logs the repositories accessible from the contextualized credentials.
 * It asserts that the correct repositories are accessible based on the repository access strategy set on the credentials.
 */
class RunWithCredentialsTest extends AbstractGitHubWireMockTest {

    @SuppressWarnings("unused")
    @RegisterExtension
    private static final BuildWatcherExtension WATCHER = new BuildWatcherExtension();

    private boolean resetAllowUnsafeRepoInference;

    private enum InstallationAccessToken {
        TOKEN,
        TOKEN_A,
        TOKEN_B,
        TOKEN_AB;

        public String json() {
            return "{\"token\":\"" + name() + "\",\"expires_at\": \"" + createTokenExpiration() + "\"}";
        }

        public String bearer() {
            return String.format("Bearer %s", name());
        }

        private String createTokenExpiration() {
            // This token will go stale at the soonest allowed time but will not
            // expire for the duration of the test
            // Format: 2019-08-10T05:54:58Z
            return DateTimeFormatter.ISO_INSTANT.format(
                    Instant.now().plus(Duration.ofMinutes(10)).truncatedTo(ChronoUnit.SECONDS));
        }
    }

    private static final HttpHeaders HEADERS =
            new HttpHeaders(new HttpHeader("Content-Type", "application/json; charset=utf-8"));

    private GitHubAppCredentials credentials;
    private CredentialsStore credentialsStore;
    private WorkflowJob project;

    @BeforeEach
    void beforeEach() throws Exception {
        resetAllowUnsafeRepoInference = GitHubAppCredentials.ALLOW_UNSAFE_REPOSITORY_INFERENCE;

        GitHubConfiguration.get().setApiRateLimitChecker(ApiRateLimitChecker.ThrottleOnOver);
        // Tests here use WorkflowJob+GithubProjectProperty for simplicity. We could switch to Multibranch Projects
        // instead to avoid this flag.
        GitHubAppCredentials.ALLOW_UNSAFE_REPOSITORY_INFERENCE = true;

        credentials = GitHubApp.createCredentials("theCredentials");
        credentials.setApiUri(githubApi.baseUrl());

        credentialsStore =
                CredentialsProvider.lookupStores(r.jenkins).iterator().next();
        credentialsStore.addCredentials(Domain.global(), credentials);

        project = r.createProject(WorkflowJob.class);
        project.addProperty(new GithubProjectProperty("https://github.com/cloudbeers/repository-a/"));
        project.setDefinition(new CpsFlowDefinition(
                "getAccessibleRepositories(credentialsId: '" + credentials.getId()
                        + "', githubApiUri: 'http://localhost:" + githubApi.getPort() + "/installation/repositories')",
                true));

        // Sub app
        githubApi.stubFor(get(urlEqualTo("/app"))
                .willReturn(aResponse()
                        .withHeaders(HEADERS)
                        .withBodyFile("../AppCredentials/files/body-mapping-githubapp-app.json")));

        // Stub app installation access token
        githubApi.stubFor(post(urlEqualTo("/app/installations/654321/access_tokens"))
                .withRequestBody(equalToJson(
                        "{\"permissions\":{\"pull_requests\":\"write\",\"metadata\":\"read\",\"checks\":\"write\",\"contents\":\"read\"}}",
                        true,
                        false))
                .willReturn(aResponse().withHeaders(HEADERS).withBody(InstallationAccessToken.TOKEN.json())));
        githubApi.stubFor(post(urlEqualTo("/app/installations/654321/access_tokens"))
                .withRequestBody(equalToJson(
                        "{\"repositories\":[\"repository-a\"],\"permissions\":{\"pull_requests\":\"write\",\"metadata\":\"read\",\"checks\":\"write\",\"contents\":\"read\"}}",
                        true,
                        false))
                .willReturn(aResponse().withHeaders(HEADERS).withBody(InstallationAccessToken.TOKEN_A.json())));
        githubApi.stubFor(post(urlEqualTo("/app/installations/654321/access_tokens"))
                .withRequestBody(equalToJson(
                        "{\"repositories\":[\"repository-b\"],\"permissions\":{\"pull_requests\":\"write\",\"metadata\":\"read\",\"checks\":\"write\",\"contents\":\"read\"}}",
                        true,
                        false))
                .willReturn(aResponse().withHeaders(HEADERS).withBody(InstallationAccessToken.TOKEN_B.json())));
        githubApi.stubFor(post(urlEqualTo("/app/installations/654321/access_tokens"))
                .withRequestBody(equalToJson(
                        "{\"repositories\":[\"repository-a\",\"repository-b\"],\"permissions\":{\"pull_requests\":\"write\",\"metadata\":\"read\",\"checks\":\"write\",\"contents\":\"read\"}}",
                        true,
                        false))
                .willReturn(aResponse().withHeaders(HEADERS).withBody(InstallationAccessToken.TOKEN_AB.json())));
        githubApi.stubFor(
                post(urlEqualTo("/app/installations/654321/access_tokens"))
                        .withRequestBody(equalToJson(
                                "{\"repositories\":[\"repository-a\",\"repository-b\",\"repository-c\"],\"permissions\":{\"pull_requests\":\"write\",\"metadata\":\"read\",\"checks\":\"write\",\"contents\":\"read\"}}",
                                true,
                                false))
                        .willReturn(
                                aResponse()
                                        .withHeaders(HEADERS)
                                        .withStatus(422)
                                        .withBody(
                                                "{\"message\":\"There is at least one repository that does not exist or is not accessible to the parent installation.\",\"documentation_url\":\"https://docs.github.com/rest/reference/apps#create-an-installation-access-token-for-an-app\",\"status\":\"422\"}")));

        // Stub installation repositories
        githubApi.stubFor(
                get(urlEqualTo("/installation/repositories"))
                        .withHeader("Authorization", equalTo(InstallationAccessToken.TOKEN.bearer()))
                        .willReturn(
                                aResponse()
                                        .withHeaders(HEADERS)
                                        .withBody(
                                                "{\"repositories\":[{\"id\":1,\"name\":\"repository-a\",\"full_name\":\"cloudbeers/repository-a\"},{\"id\":2,\"name\":\"repository-b\",\"full_name\":\"cloudbeers/repository-b\"}]}")));
        githubApi.stubFor(
                get(urlEqualTo("/installation/repositories"))
                        .withHeader("Authorization", equalTo(InstallationAccessToken.TOKEN_A.bearer()))
                        .willReturn(
                                aResponse()
                                        .withHeaders(HEADERS)
                                        .withBody(
                                                "{\"repositories\":[{\"id\":1,\"name\":\"repository-a\",\"full_name\":\"cloudbeers/repository-a\"}]}")));
        githubApi.stubFor(
                get(urlEqualTo("/installation/repositories"))
                        .withHeader("Authorization", equalTo(InstallationAccessToken.TOKEN_B.bearer()))
                        .willReturn(
                                aResponse()
                                        .withHeaders(HEADERS)
                                        .withBody(
                                                "{\"repositories\":[{\"id\":2,\"name\":\"repository-b\",\"full_name\":\"cloudbeers/repository-b\"}]}")));
        githubApi.stubFor(
                get(urlEqualTo("/installation/repositories"))
                        .withHeader("Authorization", equalTo(InstallationAccessToken.TOKEN_AB.bearer()))
                        .willReturn(
                                aResponse()
                                        .withHeaders(HEADERS)
                                        .withBody(
                                                "{\"repositories\":[{\"id\":1,\"name\":\"repository-a\",\"full_name\":\"cloudbeers/repository-a\"},{\"id\":2,\"name\":\"repository-b\",\"full_name\":\"cloudbeers/repository-b\"}]}")));
    }

    private void simpleInstallations() {
        // Stub app installation
        githubApi.stubFor(get(urlEqualTo("/app/installations"))
                .willReturn(aResponse()
                        .withHeaders(HEADERS)
                        .withBodyFile("../AppCredentials/files/body-mapping-githubapp-installations-single.json")));
    }

    private void multipleInstallations() {
        // Stub app installation
        githubApi.stubFor(get(urlEqualTo("/app/installations"))
                .willReturn(aResponse()
                        .withHeaders(HEADERS)
                        .withBodyFile("../AppCredentials/files/body-mapping-githubapp-installations-multiple.json")));
    }

    @AfterEach
    void afterEach() throws Exception {
        GitHubAppCredentials.ALLOW_UNSAFE_REPOSITORY_INFERENCE = resetAllowUnsafeRepoInference;

        if (project != null) {
            project.delete();
        }
        if (credentials != null && credentialsStore != null) {
            credentialsStore.removeCredentials(Domain.global(), credentials);
        }
    }

    @Test
    void inferredRepository() throws Exception {
        multipleInstallations();

        credentials.setRepositoryAccessStrategy(new AccessInferredRepository());

        final var build = r.buildAndAssertSuccess(project);

        // Only the inferred repository should be accessible from the contextualized credentials
        assertAccessibleRepositories(build, "cloudbeers/repository-a");
    }

    @Test
    void inferredOwner() throws Exception {
        multipleInstallations();

        credentials.setRepositoryAccessStrategy(new AccessInferredOwner());

        final var build = r.buildAndAssertSuccess(project);

        // All repositories owned by inferred owner should be accessible from the contextualized credentials (TOKEN)
        assertAccessibleRepositories(build, "cloudbeers/repository-a", "cloudbeers/repository-b");
    }

    @Test
    void specifiedRepositoriesA() throws Exception {
        multipleInstallations();

        credentials.setRepositoryAccessStrategy(
                new AccessSpecifiedRepositories("cloudbeers", Arrays.asList("repository-a")));

        final var build = r.buildAndAssertSuccess(project);

        // Only specified repositories should be accessible from the contextualized credentials (TOKEN_A)
        assertAccessibleRepositories(build, "cloudbeers/repository-a");
    }

    @Test
    void specifiedRepositoriesB() throws Exception {
        multipleInstallations();

        credentials.setRepositoryAccessStrategy(
                new AccessSpecifiedRepositories("cloudbeers", Arrays.asList("repository-b")));

        final var build = r.buildAndAssertSuccess(project);

        // Only specified repositories should be accessible from the contextualized credentials (TOKEN_B)
        assertAccessibleRepositories(build, "cloudbeers/repository-b");
    }

    @Test
    void specifiedRepositoriesAB() throws Exception {
        multipleInstallations();

        credentials.setRepositoryAccessStrategy(
                new AccessSpecifiedRepositories("cloudbeers", Arrays.asList("repository-a", "repository-b")));

        final var build = r.buildAndAssertSuccess(project);

        // Only specified repositories should be accessible from the contextualized credentials (TOKEN_AB)
        assertAccessibleRepositories(build, "cloudbeers/repository-a", "cloudbeers/repository-b");
    }

    @Test
    void specifiedRepositoriesABC() throws Exception {
        multipleInstallations();

        credentials.setRepositoryAccessStrategy(new AccessSpecifiedRepositories(
                "cloudbeers", Arrays.asList("repository-a", "repository-b", "repository-c")));

        final var build = r.buildAndAssertStatus(Result.FAILURE, project);

        // Should fail as one specified repository does not exist
        r.waitForMessage(
                "There is at least one repository that does not exist or is not accessible to the parent installation.",
                build);
    }

    @Test
    void specifiedRepositoryWithOwner() throws Exception {
        simpleInstallations();

        credentials.setRepositoryAccessStrategy(new AccessSpecifiedRepositories("cloudbeers", List.of()));

        final var build = r.buildAndAssertSuccess(project);

        // Only specified repositories should be accessible from the contextualized credentials (TOKEN_AB)
        assertAccessibleRepositories(build, "cloudbeers/repository-a", "cloudbeers/repository-b");
    }

    @Test
    void specifiedRepositoryWithoutOwner() throws Exception {
        simpleInstallations();

        // Owner is inferred from the context in this case
        credentials.setRepositoryAccessStrategy(new AccessSpecifiedRepositories(null, List.of()));

        final var build = r.buildAndAssertSuccess(project);

        // Only specified repositories should be accessible from the contextualized credentials (TOKEN_AB)
        assertAccessibleRepositories(build, "cloudbeers/repository-a", "cloudbeers/repository-b");
    }

    /**
     * Demonstrates how a user who only has access to edit a Jenkinsfile can abuse GithubProjectProperty in combination
     * with the properties step to access any repository accessible to the app installation, as long as the Pipeline is
     * not part of a MultiBranchProject.
     */
    @Test
    void propertiesStepAllowsAccessBypass() throws Exception {
        multipleInstallations();

        credentials.setRepositoryAccessStrategy(new AccessInferredRepository());

        project.setDefinition(new CpsFlowDefinition(
                "properties([githubProjectProperty('https://github.com/cloudbeers/repository-b/')])\n"
                        + "getAccessibleRepositories(credentialsId: '" + credentials.getId()
                        + "', githubApiUri: 'http://localhost:" + githubApi.getPort() + "/installation/repositories')",
                true));

        // First build uses the property configured in RunWithCredentialsTest#before
        var build = r.buildAndAssertSuccess(project);
        assertAccessibleRepositories(build, "cloudbeers/repository-a");

        // But the second build uses the new property configured by the properties step.
        build = r.buildAndAssertSuccess(project);
        assertAccessibleRepositories(build, "cloudbeers/repository-b");
    }

    private static void assertAccessibleRepositories(Run<?, ?> build, String... repositoryNames) {
        var action = build.getAction(GetAccessibleRepositories.AccessibleRepositoriesAction.class);
        assertThat(action.repositories, contains(repositoryNames));
    }

    public static class GetAccessibleRepositories extends Step {
        private final String credentialsId;
        private final String githubApiUri;

        @DataBoundConstructor
        public GetAccessibleRepositories(String credentialsId, String githubApiUri) {
            this.credentialsId = credentialsId;
            this.githubApiUri = githubApiUri;
        }

        @Override
        public StepExecution start(StepContext context) {
            return StepExecutions.synchronous(context, c -> {
                var run = c.get(Run.class);
                var credentials =
                        CredentialsProvider.findCredentialById(credentialsId, GitHubAppCredentials.class, run);
                var req = ProxyConfiguration.newHttpRequestBuilder(URI.create(githubApiUri))
                        .header(
                                "Authorization",
                                "Bearer " + credentials.getPassword().getPlainText())
                        .header("Accept", "application/vnd.github+json")
                        .GET()
                        .build();
                var rsp = ProxyConfiguration.newHttpClient().send(req, BodyHandlers.ofString());
                var body = rsp.body();
                c.get(TaskListener.class).getLogger().println(body);
                List<String> repositoryNames = new ArrayList<>();
                var json = JSONObject.fromObject(body);
                for (var repository : json.getJSONArray("repositories")) {
                    var repoName = ((JSONObject) repository).getString("full_name");
                    repositoryNames.add(repoName);
                }
                run.addAction(new AccessibleRepositoriesAction(repositoryNames));
                return null;
            });
        }

        public static class AccessibleRepositoriesAction extends InvisibleAction {
            private final List<String> repositories;

            public AccessibleRepositoriesAction(List<String> repositories) {
                this.repositories = new ArrayList<>(repositories);
            }
        }

        @TestExtension
        public static class DescriptorImpl extends StepDescriptor {
            @Override
            public Set<? extends Class<?>> getRequiredContext() {
                return Set.of(TaskListener.class);
            }

            @Override
            public String getFunctionName() {
                return "getAccessibleRepositories";
            }
        }
    }
}
