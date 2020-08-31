package org.jenkinsci.plugins.github_branch_source;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.Domain;
import hudson.logging.LogRecorder;
import hudson.logging.LogRecorderManager;
import hudson.model.Slave;
import hudson.util.Secret;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.plugins.git.GitStep;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
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
import java.util.Comparator;
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

        LogRecorderManager mgr = r.jenkins.getLog();
        logRecorder = new LogRecorder(GitHubAppCredentials.class.getName());
        mgr.logRecorders.put(GitHubAppCredentials.class.getName(), logRecorder);
        LogRecorder.Target t = new LogRecorder.Target(GitHubAppCredentials.class.getName(), Level.FINER);
        logRecorder.targets.add(t);
        logRecorder.save();
        t.enable();

        // Add agent
        agent = r.createOnlineSlave();
        agent.setLabelString("my-agent");
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
                            // This token will go stale at the soonest allowed time but will no be expired for the duration of the test
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

        // Force an error to test fallback to returning unexpired token
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

        // Force an error to test fallback refreshing from agent
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
                        .withBodyFile("../AppCredentials/files/body-githubapp-create-installation-accesstokens.json")));

    }

    @Test
    public void testAgentRefresh() throws Exception {
        long notStaleSeconds = GitHubAppCredentials.AppInstallationToken.NOT_STALE_MINIMUM_SECONDS;
        try {
            appCredentials.setApiUri(githubApi.baseUrl());

            // We want to demonstrate successful caching without waiting for a the default 1 minute
            // Must set this to a large enough number to avoid flaky test
            GitHubAppCredentials.AppInstallationToken.NOT_STALE_MINIMUM_SECONDS = 15;

            final String jenkinsfile = String.join(
                "\n",
                "// run checkout several times",
                "node ('my-agent') {",
                // First Checkout on agent should use cached token passed via remoting
                "    checkout scm",
                // Multiple checkouts in quick succession should cached token
                "    checkout scm",
                "    sleep " + (GitHubAppCredentials.AppInstallationToken.NOT_STALE_MINIMUM_SECONDS + 2),
                // Checkout after token expires refreshes via remoting - error on controller is not catastrophic
                "    checkout scm",
                // Checkout after error will refresh again on controller
                "    checkout scm",
                // Multiple checkouts in quick succession should use cached token
                "    checkout scm",
                "}");


            sampleRepo.init();
            sampleRepo.write("Jenkinsfile", jenkinsfile);
            sampleRepo.git("add", "Jenkinsfile");
            sampleRepo.git("commit", "--message=init");

            WorkflowJob job = r.createProject(WorkflowJob.class, "repo-master");
            GitStep gitStep = new GitStep(sampleRepo.toString());
            gitStep.setCredentialsId(myAppCredentialsId);
            gitStep.setPoll(false);
            job.setDefinition(new CpsScmFlowDefinition(gitStep.createSCM(), "Jenkinsfile"));
            WorkflowRun run = job.scheduleBuild2(0).waitForStart();
            r.waitForCompletion(run);

            System.out.println(JenkinsRule.getLog(run));

            List<String> credentialsLog = getOutputLines();

            //Verify correct messages from GitHubAppCredential logger indicating token was retrieved on agent
            assertThat("Creds should cache on master, pass to agent, and refresh agent from master once",
                credentialsLog, contains(
                    // node ('my-agent') {
                    "Generating App Installation Token for app ID 54321",
                    "Token will become stale after 15 seconds",
                    "Generated App Installation Token for app ID 54321",
                    "Retrieved GitHub App Installation Token for app ID 54321",
                    // checkout scm
                    // checkout scm
                    // sleep
                    // (^^^ No token generation for these three steps)
                    // checkout scm
                    "Generating App Installation Token for app ID 54321",
                    // (error forced by wiremock)
                    "Failed to retrieve GitHub App installation token for app ID 54321",
                    "Keeping cached GitHub App Installation Token for app ID 54321: token is stale but has not expired",
                    // (error forced by wiremock - failed refresh on the agent)
                    "Generating App Installation Token for app ID 54321 for agent",
                    "Failed to retrieve GitHub App installation token for app ID 54321",
                    "Keeping cached GitHub App Installation Token for app ID 54321 on agent: token is stale but has not expired",
                    // checkout scm - refresh on controller
                    "Generating App Installation Token for app ID 54321",
                    "Token will become stale after 15 seconds",
                    "Generated App Installation Token for app ID 54321",
                    "Retrieved GitHub App Installation Token for app ID 54321"
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
        result.addAll(logRecorder.getSlaveLogRecords().get(agent.toComputer()));
        return result.stream()
            // order by millis maintaining sequence within milli
            .sorted(Comparator.comparingLong(record -> record.getMillis() * 100L + record.getSequenceNumber()))
            .map(formatter::formatMessage)
            .collect(Collectors.toList());
    }

    static String printDate(Date dt) {
        return DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(dt.getTime()).truncatedTo(
            ChronoUnit.SECONDS));
    }

}