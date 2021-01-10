package org.jenkinsci.plugins.github_branch_source;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.Domain;
import hudson.logging.LogRecorder;
import hudson.logging.LogRecorderManager;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Result;
import hudson.model.Slave;
import hudson.model.StringParameterDefinition;
import hudson.util.Secret;
import jenkins.plugins.git.GitSampleRepoRule;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.Matchers.*;

public class GithubAppCredentialsTest extends AbstractGitHubWireMockTest {

    private static Slave agent;
    private static final String myAppCredentialsId = "myAppCredentialsId";
    private static CredentialsStore store;
    private static GitHubAppCredentials appCredentials;
    private static LogRecorder logRecorder;

    @Rule
    public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    @BeforeClass
    public static void setUpJenkins() throws Exception {
        //Add credential (Must have valid private key for Jwt to work, but App doesn't have to actually exist)
        store = CredentialsProvider.lookupStores(r.jenkins).iterator().next();
        appCredentials = new GitHubAppCredentials(
            CredentialsScope.GLOBAL, myAppCredentialsId, "sample", "54321", Secret.fromString(JwtHelperTest.PKCS8_PRIVATE_KEY));
        appCredentials.setOwner("cloudbeers");
        store.addCredentials(Domain.global(), appCredentials);

        // Add agent
        agent = r.createOnlineSlave();
        agent.setLabelString("my-agent");

        // Would use LoggerRule, but need to get agent logs as well
        LogRecorderManager mgr = r.jenkins.getLog();
        logRecorder = new LogRecorder(GitHubAppCredentials.class.getName());
        mgr.logRecorders.put(GitHubAppCredentials.class.getName(), logRecorder);
        LogRecorder.Target t = new LogRecorder.Target(GitHubAppCredentials.class.getName(), Level.FINE);
        logRecorder.targets.add(t);
        logRecorder.save();
        t.enable();
    }

    @Before
    public void setUpWireMock() throws Exception {
        //Add wiremock responses for App, App Installation, and App Installation Token
        githubApi.stubFor(
            get(urlEqualTo("/app"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBodyFile("../AppCredentials/files/body-mapping-githubapp-app.json")));
        githubApi.stubFor(
            get(urlEqualTo("/app/installations"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBodyFile("../AppCredentials/files/body-mapping-githubapp-installations.json")));

        final String scenarioName = "credentials-accesstoken";

        githubApi.stubFor(
            post(urlEqualTo("/app/installations/654321/access_tokens"))
                .inScenario(scenarioName)
                .whenScenarioStateIs("Started")
                .willSetStateTo("1")
                .withRequestBody(
                    equalToJson(
                        "{\"permissions\":{\"pull_requests\":\"write\",\"metadata\":\"read\",\"checks\":\"write\",\"contents\":\"read\"}}", true, false))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBody("{\n" +
                            "  \"token\": \"super-secret-token\",\n" +
                            // This token will go stale at the soonest allowed time but will not expire for the duration of the test
                            "  \"expires_at\": \"" + printDate(new Date(System.currentTimeMillis() + Duration.ofMinutes(10).toMillis())) + "\"" + // 2019-08-10T05:54:58Z
                            "}")));

        // Force an error to test fallback refreshing from agent
        githubApi.stubFor(
            post(urlEqualTo("/app/installations/654321/access_tokens"))
                .inScenario(scenarioName)
                .whenScenarioStateIs("1")
                .willSetStateTo("2")
                .withRequestBody(
                    equalToJson(
                        "{\"permissions\":{\"pull_requests\":\"write\",\"metadata\":\"read\",\"checks\":\"write\",\"contents\":\"read\"}}", true, false))
                .willReturn(
                    aResponse()
                        .withStatus(404)
                        .withStatusMessage("404 Not Found")
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBody("{\"message\": \"File not found\"}")));

        // Force an error to test fallback to returning unexpired token on agent
        githubApi.stubFor(
            post(urlEqualTo("/app/installations/654321/access_tokens"))
                .inScenario(scenarioName)
                .whenScenarioStateIs("2")
                .willSetStateTo("3")
                .withRequestBody(
                    equalToJson(
                        "{\"permissions\":{\"pull_requests\":\"write\",\"metadata\":\"read\",\"checks\":\"write\",\"contents\":\"read\"}}", true, false))
                .willReturn(
                    aResponse()
                        .withStatus(404)
                        .withStatusMessage("404 Not Found")
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBody("{\"message\": \"File not found\"}")));

        // return an expired token on controller
        githubApi.stubFor(
            post(urlEqualTo("/app/installations/654321/access_tokens"))
                .inScenario(scenarioName)
                .whenScenarioStateIs("3")
                .willSetStateTo("4")
                .withRequestBody(
                    equalToJson(
                        "{\"permissions\":{\"pull_requests\":\"write\",\"metadata\":\"read\",\"checks\":\"write\",\"contents\":\"read\"}}", true, false))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBody("{\n" +
                            "  \"token\": \"super-secret-token\",\n" +
                            // token is already expired, but will not go stale for at least the minimum time
                            // This is a valid scenario - clocks are not always properly synchronized.
                            "  \"expires_at\": \"" + printDate(new Date()) + "\"" + // 2019-08-10T05:54:58Z
                            "}")));

        // Force an error to test non-fallback scenario and refreshing on agent
        githubApi.stubFor(
            post(urlEqualTo("/app/installations/654321/access_tokens"))
                .inScenario(scenarioName)
                .whenScenarioStateIs("4")
                .willSetStateTo("5")
                .withRequestBody(
                    equalToJson(
                        "{\"permissions\":{\"pull_requests\":\"write\",\"metadata\":\"read\",\"checks\":\"write\",\"contents\":\"read\"}}", true, false))
                .willReturn(
                    aResponse()
                        .withStatus(404)
                        .withStatusMessage("404 Not Found")
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBody("{\"message\": \"File not found\"}")));

        // Valid token retirieved on agent
        githubApi.stubFor(
            post(urlEqualTo("/app/installations/654321/access_tokens"))
                .inScenario(scenarioName)
                .whenScenarioStateIs("5")
                .willSetStateTo("6")
                .withRequestBody(
                    equalToJson(
                        "{\"permissions\":{\"pull_requests\":\"write\",\"metadata\":\"read\",\"checks\":\"write\",\"contents\":\"read\"}}", true, false))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBody("{\n" +
                            "  \"token\": \"super-secret-token\",\n" +
                            "  \"expires_at\": \"" + printDate(new Date()) + "\"" + // 2019-08-10T05:54:58Z
                            "}")));

        // Valid token retirieved on controller
        githubApi.stubFor(
            post(urlEqualTo("/app/installations/654321/access_tokens"))
                .inScenario(scenarioName)
                .whenScenarioStateIs("6")
                .willSetStateTo("7") // setting this to non-existant state means any extra requests will fail
                .withRequestBody(
                    equalToJson(
                        "{\"permissions\":{\"pull_requests\":\"write\",\"metadata\":\"read\",\"checks\":\"write\",\"contents\":\"read\"}}", true, false))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBody("{\n" +
                            "  \"token\": \"super-secret-token\",\n" +
                            "  \"expires_at\": \"" + printDate(new Date()) + "\"" + // 2019-08-10T05:54:58Z
                            "}")));
    }

    @Test
    public void testAgentRefresh() throws Exception {
        long notStaleSeconds = GitHubAppCredentials.AppInstallationToken.NOT_STALE_MINIMUM_SECONDS;
        try {
            appCredentials.setApiUri(githubApi.baseUrl());

            // We want to demonstrate successful caching without waiting for a the default 1 minute
            // Must set this to a large enough number to avoid flaky test
            GitHubAppCredentials.AppInstallationToken.NOT_STALE_MINIMUM_SECONDS = 5;

            final String gitCheckoutStep = String.format(
                "    git url: REPO, credentialsId: '%s'",
                myAppCredentialsId);

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
            job.setDefinition(new CpsFlowDefinition(jenkinsfile, false));
            job.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("REPO", sampleRepo.toString())));
            WorkflowRun run = job.scheduleBuild2(0).waitForStart();
            r.waitUntilNoActivity();

            System.out.println(JenkinsRule.getLog(run));
            assertThat(run.getResult(), equalTo(Result.SUCCESS));

            List<String> credentialsLog = getOutputLines();

            //Verify correct messages from GitHubAppCredential logger indicating token was retrieved on agent
            assertThat("Creds should cache on master, pass to agent, and refresh agent from master once",
                credentialsLog, contains(
                    // (agent log added out of order, see below)
                    "Generating App Installation Token for app ID 54321 on agent", // 1
                    "Failed to generate new GitHub App Installation Token for app ID 54321 on agent: cached token is stale but has not expired", // 2
                    "Generating App Installation Token for app ID 54321 on agent", // 3
                    // node ('my-agent') {
                    // checkout scm
                    "Generating App Installation Token for app ID 54321",
                    // checkout scm
                    // (No token generation)
                    // sleep
                    // checkout scm
                    "Generating App Installation Token for app ID 54321",
                    // (error forced by wiremock)
                    "Failed to generate new GitHub App Installation Token for app ID 54321: cached token is stale but has not expired",
                    // (error forced by wiremock - failed refresh on the agent)
                    // "Generating App Installation Token for app ID 54321 on agent", // 1
                    "Generating App Installation Token for app ID 54321 for agent",
                    // (agent log added out of order) "Keeping cached GitHub App Installation Token for app ID 54321 on agent: token is stale but has not expired", // 2
                    // checkout scm - refresh on controller
                    "Generating App Installation Token for app ID 54321",
                    // sleep
                    // checkout scm
                    "Generating App Installation Token for app ID 54321",
                    // (error forced by wiremock)
                    "Failed to update stale GitHub App installation token for app ID 54321 before sending to agent",
                    // "Generating App Installation Token for app ID 54321 on agent", // 3
                    "Generating App Installation Token for app ID 54321 for agent",
                    // checkout scm - refresh on controller
                    "Generating App Installation Token for app ID 54321"
                    // checkout scm
                    // (No token generation)
                    ));
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
        Collections.reverse(result);
        return result.stream()
            .map(formatter::formatMessage)
            .collect(Collectors.toList());
    }

    static String printDate(Date dt) {
        return DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(dt.getTime()).truncatedTo(
            ChronoUnit.SECONDS));
    }

}