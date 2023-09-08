package org.jenkinsci.plugins.github_branch_source;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.jenkinsci.plugins.github_branch_source.Connector.createGitHubBuilder;
import static org.junit.Assert.assertThrows;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
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
import hudson.util.Secret;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;
import java.util.stream.Collectors;
import jenkins.plugins.git.GitSampleRepoRule;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.LoggerRule;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.authorization.AuthorizationProvider;

public class GithubAppCredentialsTest extends AbstractGitHubWireMockTest {

    private static Slave agent;
    private static final String myAppCredentialsId = "myAppCredentialsId";
    private static final String myAppCredentialsNoOwnerId = "myAppCredentialsNoOwnerId";
    private static CredentialsStore store;
    private static GitHubAppCredentials appCredentials, appCredentialsNoOwner;
    private static LogRecorder logRecorder;

    // https://stackoverflow.com/a/22176759/4951015
    public static final String PKCS8_PRIVATE_KEY = "-----BEGIN PRIVATE KEY-----\n"
            +
            // Windows line ending
            "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQD7vHsVwyDV8cj7\r\n"
            +
            // This should also work
            "5yR4WWl6rlgf/e5zmeBgtm0PCgnitcSbD5FU33301DPY5a7AtqVBOwEnE14L9XS7\r"
            + "ov61U+x1m4aQmqR/dPQaA2ayh2cYPszWNQMp42ArDIfg7DhSrvsRJKHsbPXlPjqe\n"
            + "c0udLqhSLVIO9frNLf+dAsLsgYk8O39PKGb33akGG7tWTe0J+akNQjgbS7vOi8sS\n"
            + "NLwHIdYfz/Am+6Xmm+J4yVs6+Xt3kOeLdFBkz8H/HGsJq854MbIAK/HuId1MOPS0\n"
            + "cDWh37tzRsM+q/HZzYRkc5bhNKw/Mj9jN9jD5GH0Lfea0QFedjppf1KvWdcXn+/W\n"
            + "M7OmyfhvAgMBAAECggEAN96H7reExRbJRWbySCeH6mthMZB46H0hODWklK7krMUs\n"
            + "okFdPtnvKXQjIaMwGqMuoACJa/O3bq4GP1KYdwPuOdfPkK5RjdwWBOP2We8FKXNe\n"
            + "oLfZQOWuxT8dtQSYJ3mgTRi1OzSfikY6Wko6YOMnBj36tUlQZVMtJNqlCjphi9Uz\n"
            + "6EyvRURlDG8sBBbC7ods5B0789qk3iGH/97ia+1QIqXAUaVFg3/BA6wkxkbNG2sN\n"
            + "tqULgVYTw32Oj/Y/H1Y250RoocTyfsUS3I3aPIlnvcgp2bugWqDyYJ58nDIt3Pku\n"
            + "fjImWrNz/pNiEs+efnb0QEk7m5hYwxmyXN4KRSv0OQKBgQD+I3Y3iNKSVr6wXjur\n"
            + "OPp45fxS2sEf5FyFYOn3u760sdJOH9fGlmf9sDozJ8Y8KCaQCN5tSe3OM+XDrmiw\n"
            + "Cu/oaqJ1+G4RG+6w1RJF+5Nfg6PkUs7eJehUgZ2Tox8Tg1mfVIV8KbMwNi5tXpug\n"
            + "MVmA2k9xjc4uMd2jSnSj9NAqrQKBgQD9lIO1tY6YKF0Eb0Qi/iLN4UqBdJfnALBR\n"
            + "MjxYxqqI8G4wZEoZEJJvT1Lm6Q3o577N95SihZoj69tb10vvbEz1pb3df7c1HEku\n"
            + "LXcyVMvjR/CZ7dOSNgLGAkFfOoPhcF/OjSm4DrGPe3GiBxhwXTBjwJ5TIgEDkVIx\n"
            + "ZVo5r7gPCwKBgQCOvsZo/Q4hql2jXNqxGuj9PVkUBNFTI4agWEYyox7ECdlxjks5\n"
            + "vUOd5/1YvG+JXJgEcSbWRh8volDdL7qXnx0P881a6/aO35ybcKK58kvd62gEGEsf\n"
            + "1jUAOmmTAp2y7SVK7EOp8RY370b2oZxSR0XZrUXQJ3F22wV98ZVAfoLqZQKBgDIr\n"
            + "PdunbezAn5aPBOX/bZdZ6UmvbZYwVrHZxIKz2214U/STAu3uj2oiQX6ZwTzBDMjn\n"
            + "IKr+z74nnaCP+eAGhztabTPzXqXNUNUn/Zshl60BwKJToTYeJXJTY+eZRhpGB05w\n"
            + "Mz7M+Wgvvg2WZcllRnuV0j0UTysLhz1qle0vzLR9AoGBAOukkFFm2RLm9N1P3gI8\n"
            + "mUadeAlYRZ5o0MvumOHaB5pDOCKhrqAhop2gnM0f5uSlapCtlhj0Js7ZyS3Giezg\n"
            + "38oqAhAYxy2LMoLD7UtsHXNp0OnZ22djcDwh+Wp2YORm7h71yOM0NsYubGbp+CmT\n"
            + "Nw9bewRvqjySBlDJ9/aNSeEY\n"
            + "-----END PRIVATE KEY-----";

    @Rule
    public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    @Rule
    public BuildWatcher buildWatcher = new BuildWatcher();

    // here to aid debugging - we can not use LoggerRule for the test assertion as it only captures
    // logs from the controller
    @ClassRule
    public static LoggerRule loggerRule = new LoggerRule().record(GitHubAppCredentials.class, Level.FINE);

    @BeforeClass
    public static void setUpJenkins() throws Exception {
        // Add credential (Must have valid private key for Jwt to work, but App doesn't have to actually
        // exist)
        store = CredentialsProvider.lookupStores(r.jenkins).iterator().next();
        appCredentials = new GitHubAppCredentials(
                CredentialsScope.GLOBAL, myAppCredentialsId, "sample", "54321", Secret.fromString(PKCS8_PRIVATE_KEY));
        appCredentials.setOwner("cloudBeers");
        store.addCredentials(Domain.global(), appCredentials);
        appCredentialsNoOwner = new GitHubAppCredentials(
                CredentialsScope.GLOBAL,
                myAppCredentialsNoOwnerId,
                "sample",
                "54321",
                Secret.fromString(PKCS8_PRIVATE_KEY));
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
        r.showAgentLogs(agent, loggerRule);
    }

    @Before
    public void setUpWireMock() throws Exception {
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
                        .withBodyFile("../AppCredentials/files/body-mapping-githubapp-installations.json")));

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
    public void testProviderRefresh() throws Exception {
        final long notStaleSeconds = GitHubAppCredentials.AppInstallationToken.NOT_STALE_MINIMUM_SECONDS;
        try {
            appCredentials.setApiUri(githubApi.baseUrl());

            // We want to demonstrate successful caching without waiting for the default 1 minute
            // Must set this to a large enough number to avoid flaky test
            GitHubAppCredentials.AppInstallationToken.NOT_STALE_MINIMUM_SECONDS = 5;

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

            // Verify correct messages from GitHubAppCredential logger indicating token was retrieved on
            // agent
            assertThat(
                    "Creds should cache on master",
                    credentialsLog,
                    contains(
                            // refresh on controller
                            "Generating App Installation Token for app ID 54321",
                            // next call uses cached token
                            // sleep and then refresh stale token
                            "Generating App Installation Token for app ID 54321",
                            // next call (error forced by wiremock)
                            "Failed to generate new GitHub App Installation Token for app ID 54321: cached token is stale but has not expired",
                            // next call refreshes the still stale token
                            "Generating App Installation Token for app ID 54321",
                            // sleep and then refresh stale token hits another error forced by wiremock
                            "Failed to generate new GitHub App Installation Token for app ID 54321: cached token is stale but has not expired",
                            // next call refreshes the still stale token
                            "Generating App Installation Token for app ID 54321"
                            // next call uses cached token
                            ));

            // Getting the token for via AuthorizationProvider on controller should not check rate_limit
            githubApi.verify(
                    0, RequestPatternBuilder.newRequestPattern(RequestMethod.GET, urlPathEqualTo("/rate_limit")));

        } finally {
            GitHubAppCredentials.AppInstallationToken.NOT_STALE_MINIMUM_SECONDS = notStaleSeconds;
            logRecorder.doClear();
        }
    }

    @Test
    public void testAgentRefresh() throws Exception {
        final long notStaleSeconds = GitHubAppCredentials.AppInstallationToken.NOT_STALE_MINIMUM_SECONDS;
        try {
            appCredentials.setApiUri(githubApi.baseUrl());

            // We want to demonstrate successful caching without waiting for a the default 1 minute
            // Must set this to a large enough number to avoid flaky test
            GitHubAppCredentials.AppInstallationToken.NOT_STALE_MINIMUM_SECONDS = 5;

            // Ensure we are working from sufficiently clean cache state
            Thread.sleep(Duration.ofSeconds(GitHubAppCredentials.AppInstallationToken.NOT_STALE_MINIMUM_SECONDS + 2)
                    .toMillis());

            final String gitCheckoutStep = String.format("    git url: REPO, credentialsId: '%s'", myAppCredentialsId);

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
    public void testPassword() throws Exception {
        long notStaleSeconds = GitHubAppCredentials.AppInstallationToken.NOT_STALE_MINIMUM_SECONDS;
        try {
            appCredentials.setApiUri(githubApi.baseUrl());

            // We want to demonstrate successful caching without waiting for the default 1 minute
            // Must set this to a large enough number to avoid flaky test
            GitHubAppCredentials.AppInstallationToken.NOT_STALE_MINIMUM_SECONDS = 5;

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
                    is("Found multiple installations for GitHub app ID 54321 but none match credential owner \"\". "
                            + "Set the right owner in the credential advanced options"));
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
