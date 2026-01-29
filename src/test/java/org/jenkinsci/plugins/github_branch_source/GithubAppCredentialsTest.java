package org.jenkinsci.plugins.github_branch_source;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.equalTo;
import static org.jenkinsci.plugins.github_branch_source.Connector.createGitHubBuilder;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import hudson.logging.LogRecorder;
import hudson.logging.LogRecorderManager;
import hudson.model.Label;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Result;
import hudson.model.Slave;
import hudson.model.StringParameterDefinition;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;
import java.util.stream.Collectors;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.plugins.git.junit.jupiter.WithGitSampleRepo;
import org.jenkinsci.plugins.github_branch_source.app_credentials.AccessSpecifiedRepositories;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.junit.jupiter.BuildWatcherExtension;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.authorization.AuthorizationProvider;

@WithGitSampleRepo
class GithubAppCredentialsTest extends AbstractGitHubWireMockTest {

    private static Slave agent;
    private static final String MY_APP_CREDENTIALS_ID = "myAppCredentialsId";
    private static final String MY_APP_CREDENTIALS_NO_OWNER_ID = "myAppCredentialsNoOwnerId";
    private static CredentialsStore store;
    private static GitHubAppCredentials appCredentials, appCredentialsNoOwner;
    private static LogRecorder logRecorder;

    private GitSampleRepoRule sampleRepo;

    @SuppressWarnings("unused")
    @RegisterExtension
    private static final BuildWatcherExtension BUILD_WATCHER = new BuildWatcherExtension();

    // here to aid debugging - we can not use LoggerRule for the test assertion as it only captures
    // logs from the controller
    @SuppressWarnings("unused")
    private static final org.jvnet.hudson.test.LogRecorder LOG_RECORDER =
            new org.jvnet.hudson.test.LogRecorder().record(GitHubAppCredentials.class, Level.FINE);

    @BeforeAll
    static void beforeAll() throws Exception {
        // Add credential (Must have valid private key for Jwt to work, but App doesn't have to actually
        // exist)
        store = CredentialsProvider.lookupStores(r.jenkins).iterator().next();
        appCredentials = GitHubApp.createCredentials(MY_APP_CREDENTIALS_ID);
        appCredentials.setRepositoryAccessStrategy(
                new AccessSpecifiedRepositories("cloudBeers", Collections.emptyList()));

        store.addCredentials(Domain.global(), appCredentials);
        appCredentialsNoOwner = GitHubApp.createCredentials(MY_APP_CREDENTIALS_NO_OWNER_ID);
        appCredentialsNoOwner.setRepositoryAccessStrategy(
                new AccessSpecifiedRepositories(null, Collections.emptyList()));
        store.addCredentials(Domain.global(), appCredentialsNoOwner);

        // Add agent
        agent = r.createOnlineSlave(Label.get("my-agent"));

        // Would use LoggerRule, but need to get agent logs as well
        LogRecorderManager mgr = r.jenkins.getLog();
        logRecorder = new LogRecorder(GitHubAppCredentials.class.getName());
        mgr.getRecorders().add(logRecorder);
        LogRecorder.Target t = new LogRecorder.Target(GitHubAppCredentials.class.getName(), Level.FINE);
        logRecorder.getLoggers().add(t);
        logRecorder.save();
        t.enable();
        // but even though we can not capture the logs we want to echo them
        r.showAgentLogs(agent, LOG_RECORDER);
    }

    @BeforeEach
    void beforeEach(GitSampleRepoRule repo) {
        sampleRepo = repo;

        GitHubConfiguration.get().setApiRateLimitChecker(ApiRateLimitChecker.ThrottleOnOver);

        // During credential refreshes we should never check rate_limit
        githubApi.stubFor(get(urlEqualTo("/rate_limit")).willReturn(aResponse().withStatus(500)));

        // Add wiremock responses for App, App Installation, and App Installation Token
        githubApi.stubFor(get(urlEqualTo("/app"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBodyFile("../AppCredentials/files/body-mapping-githubapp-app.json")));
        githubApi.stubFor(get(urlEqualTo("/app/installations"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBodyFile("../AppCredentials/files/body-mapping-githubapp-installations-multiple.json")));

        final String scenarioName = "credentials-accesstoken";

        githubApi.stubFor(post(urlEqualTo("/app/installations/654321/access_tokens"))
                .inScenario(scenarioName)
                .whenScenarioStateIs("Started")
                .willSetStateTo("1")
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

        // Force an error to test fallback refreshing from agent
        githubApi.stubFor(post(urlEqualTo("/app/installations/654321/access_tokens"))
                .inScenario(scenarioName)
                .whenScenarioStateIs("1")
                .willSetStateTo("2")
                .withRequestBody(equalToJson(
                        "{\"permissions\":{\"pull_requests\":\"write\",\"metadata\":\"read\",\"checks\":\"write\",\"contents\":\"read\"}}",
                        true,
                        false))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withStatusMessage("404 Not Found")
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBody("{\"message\": \"File not found\"}")));

        // Force an error to test fallback to returning unexpired token on agent
        githubApi.stubFor(post(urlEqualTo("/app/installations/654321/access_tokens"))
                .inScenario(scenarioName)
                .whenScenarioStateIs("2")
                .willSetStateTo("3")
                .withRequestBody(equalToJson(
                        "{\"permissions\":{\"pull_requests\":\"write\",\"metadata\":\"read\",\"checks\":\"write\",\"contents\":\"read\"}}",
                        true,
                        false))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withStatusMessage("404 Not Found")
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBody("{\"message\": \"File not found\"}")));

        // return an expired token on controller
        githubApi.stubFor(post(urlEqualTo("/app/installations/654321/access_tokens"))
                .inScenario(scenarioName)
                .whenScenarioStateIs("3")
                .willSetStateTo("4")
                .withRequestBody(equalToJson(
                        "{\"permissions\":{\"pull_requests\":\"write\",\"metadata\":\"read\",\"checks\":\"write\",\"contents\":\"read\"}}",
                        true,
                        false))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBody("{\n"
                                + "  \"token\": \"super-secret-token\",\n"
                                +
                                // token is already expired, but will not go stale for at least the
                                // minimum time
                                // This is a valid scenario - clocks are not always properly
                                // synchronized.
                                "  \"expires_at\": \""
                                + printDate(new Date())
                                + "\""
                                + // 2019-08-10T05:54:58Z
                                "}")));

        // Force an error to test non-fallback scenario and refreshing on agent
        githubApi.stubFor(post(urlEqualTo("/app/installations/654321/access_tokens"))
                .inScenario(scenarioName)
                .whenScenarioStateIs("4")
                .willSetStateTo("5")
                .withRequestBody(equalToJson(
                        "{\"permissions\":{\"pull_requests\":\"write\",\"metadata\":\"read\",\"checks\":\"write\",\"contents\":\"read\"}}",
                        true,
                        false))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withStatusMessage("404 Not Found")
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBody("{\"message\": \"File not found\"}")));

        // Valid token retirieved on agent
        githubApi.stubFor(post(urlEqualTo("/app/installations/654321/access_tokens"))
                .inScenario(scenarioName)
                .whenScenarioStateIs("5")
                .willSetStateTo("6")
                .withRequestBody(equalToJson(
                        "{\"permissions\":{\"pull_requests\":\"write\",\"metadata\":\"read\",\"checks\":\"write\",\"contents\":\"read\"}}",
                        true,
                        false))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBody("{\n"
                                + "  \"token\": \"super-secret-token\",\n"
                                + "  \"expires_at\": \""
                                + printDate(new Date())
                                + "\""
                                + // 2019-08-10T05:54:58Z
                                "}")));

        // Valid token retirieved on controller
        githubApi.stubFor(post(urlEqualTo("/app/installations/654321/access_tokens"))
                .inScenario(scenarioName)
                .whenScenarioStateIs("6")
                .willSetStateTo("7") // setting this to non-existant state means any extra requests will fail
                .withRequestBody(equalToJson(
                        "{\"permissions\":{\"pull_requests\":\"write\",\"metadata\":\"read\",\"checks\":\"write\",\"contents\":\"read\"}}",
                        true,
                        false))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBody("{\n"
                                + "  \"token\": \"super-secret-token\",\n"
                                + "  \"expires_at\": \""
                                + printDate(new Date())
                                + "\""
                                + // 2019-08-10T05:54:58Z
                                "}")));
    }

    @Test
    void testProviderRefresh() throws Exception {
        final long notStaleSeconds = GitHubAppCredentials.AppInstallationToken.NOT_STALE_MINIMUM_SECONDS;
        try {
            appCredentials.setApiUri(githubApi.baseUrl());

            // We want to demonstrate successful caching without waiting for the default 1 minute
            // Must set this to a large enough number to avoid flaky test
            GitHubAppCredentials.AppInstallationToken.NOT_STALE_MINIMUM_SECONDS = 10;

            // Ensure we are working from sufficiently clean cache state
            Thread.sleep(Duration.ofSeconds(GitHubAppCredentials.AppInstallationToken.NOT_STALE_MINIMUM_SECONDS + 2)
                    .toMillis());

            AuthorizationProvider provider = appCredentials.getAuthorizationProvider();
            GitHub githubInstance = createGitHubBuilder(githubApi.baseUrl())
                    .withAuthorizationProvider(provider)
                    .build();

            // First Checkout on controller should use cached
            provider.getEncodedAuthorization();
            // Multiple checkouts in quick succession should use cached token
            provider.getEncodedAuthorization();
            Thread.sleep(Duration.ofSeconds(GitHubAppCredentials.AppInstallationToken.NOT_STALE_MINIMUM_SECONDS + 2)
                    .toMillis());
            // Checkout after token is stale refreshes - fallback due to unexpired token
            provider.getEncodedAuthorization();
            // Checkout after error will refresh again on controller - new token expired but not stale
            provider.getEncodedAuthorization();
            Thread.sleep(Duration.ofSeconds(GitHubAppCredentials.AppInstallationToken.NOT_STALE_MINIMUM_SECONDS + 2)
                    .toMillis());
            // Checkout after token is stale refreshes - error on controller is not catastrophic
            provider.getEncodedAuthorization();
            // Checkout after error will refresh again on controller - new token expired but not stale
            provider.getEncodedAuthorization();
            // Multiple checkouts in quick succession should use cached token
            provider.getEncodedAuthorization();

            List<String> credentialsLog = getOutputLines();

            // Assert that individual messages occur at least once in the credentials log
            String generatingMsg = "Generating App Installation Token for app ID 54321";
            String failedMsg =
                    "Failed to generate new GitHub App Installation Token for app ID 54321: cached token is stale but has not expired";

            // Be sure the expected messages are in the log
            assertThat(credentialsLog, hasItem(generatingMsg));
            assertThat(credentialsLog, hasItem(failedMsg));

            // Verify correct messages from GitHubAppCredential logger indicating token was retrieved on
            // agent
            if (credentialsLog.get(2).equals(failedMsg)) {
                assertThat(
                        "Creds should cache on master - typical order",
                        credentialsLog,
                        contains(
                                // refresh on controller
                                generatingMsg,
                                // next call uses cached token
                                // sleep and then refresh stale token
                                generatingMsg,
                                // next call (error forced by wiremock)
                                failedMsg,
                                // next call refreshes the still stale token
                                generatingMsg,
                                // sleep and then refresh stale token hits another error forced by wiremock
                                failedMsg,
                                // next call refreshes the still stale token
                                generatingMsg
                                // next call uses cached token
                                ));
            } else {
                assertThat(
                        "Creds should cache on master - alternate order",
                        credentialsLog,
                        contains(
                                // refresh on controller
                                generatingMsg,
                                // next call uses cached token
                                // sleep and then refresh stale token
                                generatingMsg,
                                // next call refreshes the still stale token
                                generatingMsg,
                                // next call (error forced by wiremock)
                                failedMsg,
                                // sleep and then refresh stale token hits another error forced by wiremock
                                failedMsg,
                                // next call refreshes the still stale token
                                generatingMsg
                                // next call uses cached token
                                ));
            }

            // Getting the token for via AuthorizationProvider on controller should not check rate_limit
            githubApi.verify(
                    0, RequestPatternBuilder.newRequestPattern(RequestMethod.GET, urlPathEqualTo("/rate_limit")));

        } finally {
            GitHubAppCredentials.AppInstallationToken.NOT_STALE_MINIMUM_SECONDS = notStaleSeconds;
            logRecorder.doClear();
        }
    }

    @Test
    void testAgentRefresh() throws Exception {
        final long notStaleSeconds = GitHubAppCredentials.AppInstallationToken.NOT_STALE_MINIMUM_SECONDS;
        try {
            appCredentials.setApiUri(githubApi.baseUrl());

            // We want to demonstrate successful caching without waiting for a the default 1 minute
            // Must set this to a large enough number to avoid flaky test
            GitHubAppCredentials.AppInstallationToken.NOT_STALE_MINIMUM_SECONDS = 10;

            // Ensure we are working from sufficiently clean cache state
            Thread.sleep(Duration.ofSeconds(GitHubAppCredentials.AppInstallationToken.NOT_STALE_MINIMUM_SECONDS + 2)
                    .toMillis());

            final String gitCheckoutStep =
                    String.format("    git url: REPO, credentialsId: '%s'", MY_APP_CREDENTIALS_ID);

            final String jenkinsfile = String.join(
                    "\n",
                    "// run checkout several times",
                    "node ('my-agent') {",
                    "    echo 'First Checkout on agent should use cached token passed via remoting'",
                    gitCheckoutStep,
                    "    echo 'Multiple checkouts in quick succession should use cached token'",
                    gitCheckoutStep,
                    "    sleep " + (GitHubAppCredentials.AppInstallationToken.NOT_STALE_MINIMUM_SECONDS + 2),
                    "    echo 'Checkout after token is stale refreshes via remoting - fallback due to unexpired token'",
                    gitCheckoutStep,
                    "    echo 'Checkout after error will refresh again on controller - new token expired but not stale'",
                    gitCheckoutStep,
                    "    sleep " + (GitHubAppCredentials.AppInstallationToken.NOT_STALE_MINIMUM_SECONDS + 2),
                    "    echo 'Checkout after token is stale refreshes via remoting - error on controller is not catastrophic'",
                    gitCheckoutStep,
                    "    echo 'Checkout after error will refresh again on controller - new token expired but not stale'",
                    gitCheckoutStep,
                    "    echo 'Multiple checkouts in quick succession should use cached token'",
                    gitCheckoutStep,
                    "}");

            // Create a repo with the above Jenkinsfile
            sampleRepo.init();
            sampleRepo.write("Jenkinsfile", jenkinsfile);
            sampleRepo.git("add", "Jenkinsfile");
            sampleRepo.git("commit", "--message=init");

            // Create a pipeline job that points the above repo
            WorkflowJob job = r.createProject(WorkflowJob.class, "test-creds");
            job.setDefinition(new CpsFlowDefinition(jenkinsfile, true));
            job.addProperty(
                    new ParametersDefinitionProperty(new StringParameterDefinition("REPO", sampleRepo.toString())));

            WorkflowRun run = job.scheduleBuild2(0).waitForStart();
            r.waitUntilNoActivity();

            List<String> credentialsLog = getOutputLines();

            // Verify correct messages from GitHubAppCredential logger indicating token was retrieved on
            // agent
            assertThat(
                    "Creds should cache on master, pass to agent, and refresh agent from master once",
                    credentialsLog,
                    contains(
                            // node ('my-agent') {
                            //   echo 'First Checkout on agent should use cached token passed via remoting'
                            //   git url: REPO, credentialsId: 'myAppCredentialsId'
                            "Generating App Installation Token for app ID 54321",
                            //   echo 'Multiple checkouts in quick succession should use cached token'
                            //   git ....
                            // (No token generation)
                            //   sleep
                            //   echo 'Checkout after token is stale refreshes via remoting - fallback due to
                            // unexpired token'
                            //   git ....
                            "Generating App Installation Token for app ID 54321",
                            // (error forced by wiremock)
                            "Failed to generate new GitHub App Installation Token for app ID 54321: cached token is stale but has not expired",
                            // (error forced by wiremock - failed refresh on the agent)
                            "Generating App Installation Token for app ID 54321 on agent",
                            "Generating App Installation Token for app ID 54321 for agent",
                            "Failed to generate new GitHub App Installation Token for app ID 54321 on agent: cached token is stale but has not expired",
                            //    echo 'Checkout after error will refresh again on controller - new token expired
                            // but not stale'
                            //    git ....
                            "Generating App Installation Token for app ID 54321",
                            //    sleep
                            //    echo 'Checkout after token is stale refreshes via remoting - error on controller
                            // is not catastrophic'
                            //    git ....
                            "Generating App Installation Token for app ID 54321",
                            // (error forced by wiremock)
                            "Failed to update stale GitHub App installation token for app ID 54321 before sending to agent",
                            "Generating App Installation Token for app ID 54321 on agent",
                            "Generating App Installation Token for app ID 54321 for agent",
                            //    echo 'Checkout after error will refresh again on controller - new token expired
                            // but not stale'
                            //    git ....
                            "Generating App Installation Token for app ID 54321"
                            //    echo 'Multiple checkouts in quick succession should use cached token'
                            //     git ....
                            // (No token generation)
                            ));

            // Check success after output.  Output will be more informative if something goes wrong.
            assertThat(
                    "Run should be success, log: " + run.getLog() + System.lineSeparator() + " end of log",
                    run.getResult(),
                    equalTo(Result.SUCCESS));

            // Getting the token for via AuthorizationProvider on controller should not check rate_limit
            // Getting the token for agents via remoting to the controller should not check rate_limit
            githubApi.verify(
                    0, RequestPatternBuilder.newRequestPattern(RequestMethod.GET, urlPathEqualTo("/rate_limit")));
        } finally {
            GitHubAppCredentials.AppInstallationToken.NOT_STALE_MINIMUM_SECONDS = notStaleSeconds;
            logRecorder.doClear();
        }
    }

    @Test
    void testPassword() throws Exception {
        long notStaleSeconds = GitHubAppCredentials.AppInstallationToken.NOT_STALE_MINIMUM_SECONDS;
        try {
            appCredentials.setApiUri(githubApi.baseUrl());

            // We want to demonstrate successful caching without waiting for the default 1 minute
            // Must set this to a large enough number to avoid flaky test
            GitHubAppCredentials.AppInstallationToken.NOT_STALE_MINIMUM_SECONDS = 10;

            // Ensure we are working from sufficiently clean cache state
            Thread.sleep(Duration.ofSeconds(GitHubAppCredentials.AppInstallationToken.NOT_STALE_MINIMUM_SECONDS + 2)
                    .toMillis());

            appCredentials.getPassword();

            // Getting the token for a credential via getPassword() not check rate_limit
            githubApi.verify(
                    0, RequestPatternBuilder.newRequestPattern(RequestMethod.GET, urlPathEqualTo("/rate_limit")));

            // Test credentials when owner is not set
            appCredentialsNoOwner.setApiUri(githubApi.baseUrl());
            IllegalArgumentException expected =
                    assertThrows(IllegalArgumentException.class, () -> appCredentialsNoOwner.getPassword());
            assertThat(
                    expected.getMessage(),
                    is(
                            "Found multiple installations for GitHub app ID 54321 but none match credential owner \"\". Configure the repository access strategy for the credential to use one of these owners: cloudbeers, bogus"));
        } finally {
            GitHubAppCredentials.AppInstallationToken.NOT_STALE_MINIMUM_SECONDS = notStaleSeconds;
            logRecorder.doClear();
        }
    }

    private List<String> getOutputLines() {
        final Formatter formatter = new SimpleFormatter();
        List<LogRecord> result = new ArrayList<>(logRecorder.getLogRecords());
        List<LogRecord> agentLogs = logRecorder.getSlaveLogRecords().get(agent.toComputer());
        if (agentLogs != null) {
            result.addAll(agentLogs);
        }

        // sort the logs into chronological order
        // then just format the message.
        return result.stream()
                .sorted(Comparator.comparingLong(LogRecord::getMillis))
                .map(formatter::formatMessage)
                .collect(Collectors.toList());
    }

    static String printDate(Date dt) {
        return DateTimeFormatter.ISO_INSTANT.format(
                Instant.ofEpochMilli(dt.getTime()).truncatedTo(ChronoUnit.SECONDS));
    }
}
