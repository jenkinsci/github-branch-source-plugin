package org.jenkinsci.plugins.github_branch_source;

import com.cloudbees.hudson.plugins.folder.computed.FolderComputation;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.Domain;
import hudson.model.Slave;
import hudson.util.LogTaskListener;
import hudson.util.RingBufferLogHandler;
import hudson.util.Secret;
import jenkins.branch.BranchSource;
import jenkins.plugins.git.GitSCMSource;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.plugins.git.GitStep;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.Matchers.*;

public class GithubAppCredentialsTest extends AbstractGitHubWireMockTest {

    private RingBufferLogHandler handler;

    private static Slave agent;
    private WorkflowMultiBranchProject owner;
    private static final String myAppCredentialsId = "myAppCredentialsId";
    private static CredentialsStore store;
    private static GitHubAppCredentials appCredentials;

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
    }

    @Before
    public void setUpLogging() throws Exception {
        handler = new RingBufferLogHandler(1000);
        handler.setFormatter(new SimpleFormatter());
        final Logger logger = Logger.getLogger(GitHubAppCredentials.class.getName());
        logger.setLevel(Level.FINE);
        logger.addHandler(handler);
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
        githubApi.stubFor(
            post(urlEqualTo("/app/installations/654321/access_tokens"))
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
        long notStaleSeconds = GitHubAppCredentials.AppInstallationToken.NOT_STALE_FOR_ATLEAST_SECONDS;
        try {
            appCredentials.setApiUri(githubApi.baseUrl());

            // We want to demonstrate successful caching without waiting for a the default 1 minute
            // Must set this to a large enough number to avoid flaky test
            GitHubAppCredentials.AppInstallationToken.NOT_STALE_FOR_ATLEAST_SECONDS = 15;

            final String jenkinsfile = String.join(
                "\n",
                "// run checkout several times",
                "node ('my-agent') {",
                // First Checkout on agent should use cached token passed via remoting
                "    checkout scm",
                // Multiple checkouts in quick succession should cached token
                "    checkout scm",
                "    sleep " + (GitHubAppCredentials.AppInstallationToken.NOT_STALE_FOR_ATLEAST_SECONDS + 2),
                // Checkout after token expires refreshes via remoting
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

            // System.out.println(JenkinsRule.getLog(run));
            List<String> credentialsLog = getOutputLines();


            //Verify correct messages from GitHubAppCredential logger indicating token was retrieved on agent
            assertThat("Creds should cache on master, pass to agent, and refresh agent from master once",
                credentialsLog, contains(
                "Generating App Installation Token for app ID 54321",
                "Generated App Installation Token for app ID 54321",
                "Generating App Installation Token for app ID 54321 for agent",
                "Generated App Installation Token for app ID 54321"));
        } finally {
            GitHubAppCredentials.AppInstallationToken.NOT_STALE_FOR_ATLEAST_SECONDS = notStaleSeconds;
        }
    }

    public static @Nonnull
    WorkflowJob findBranchProject(@Nonnull WorkflowMultiBranchProject mp, @Nonnull String name) throws Exception {
        WorkflowJob p = mp.getItem(name);
        showIndexing(mp);
        if (p == null) {
            fail(name + " project not found");
        }
        return p;
    }

    private List<String> getOutputLines() {
        final Formatter formatter = handler.getFormatter();
        List<LogRecord> result = new ArrayList<>(handler.getView());
        Collections.reverse(result);
        return result.stream().map(formatter::formatMessage).collect(Collectors.toList());
    }

    static void showIndexing(@Nonnull WorkflowMultiBranchProject mp) throws Exception {
        FolderComputation<?> indexing = mp.getIndexing();
        System.out.println("---%<--- " + indexing.getUrl());
        indexing.writeWholeLogTo(System.out);
        System.out.println("---%<--- ");
    }

}