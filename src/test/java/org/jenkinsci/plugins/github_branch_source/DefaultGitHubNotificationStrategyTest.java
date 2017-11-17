package org.jenkinsci.plugins.github_branch_source;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.util.LogTaskListener;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import jenkins.scm.api.SCMHeadOrigin;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.*;

public class DefaultGitHubNotificationStrategyTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void given_basicJob_then_singleNotification() throws Exception {
        FreeStyleProject job = j.createFreeStyleProject();
        GitHubSCMSource src = new GitHubSCMSource("exmaple", "test");
        FreeStyleBuild run = j.buildAndAssertSuccess(job);
        DefaultGitHubNotificationStrategy instance = new DefaultGitHubNotificationStrategy();
        List<GitHubNotificationRequest> notifications =
                instance.notifications(GitHubNotificationContext.build(job, run, src, new BranchSCMHead("master")),
                        new LogTaskListener(
                                Logger.getLogger(getClass().getName()), Level.INFO));
        assertThat(notifications, hasSize(1));
    }

    @Test
    public void given_differentSCMheads_then_distinctNotifications() throws Exception {
        FreeStyleProject job = j.createFreeStyleProject();
        GitHubSCMSource src = new GitHubSCMSource("example", "test");
        FreeStyleBuild run = j.buildAndAssertSuccess(job);
        DefaultGitHubNotificationStrategy instance = new DefaultGitHubNotificationStrategy();
        BranchSCMHead testBranch = new BranchSCMHead("master");
        List<GitHubNotificationRequest> notificationsA =
                instance.notifications(GitHubNotificationContext.build(job, run, src, testBranch),
                        new LogTaskListener(Logger.getLogger(getClass().getName()), Level.INFO));
        List<GitHubNotificationRequest> notificationsB =
                instance.notifications(GitHubNotificationContext.build(job, run, src,
                        new PullRequestSCMHead("test-pr", "owner", "repo", "branch",
                                1, testBranch, SCMHeadOrigin.DEFAULT, ChangeRequestCheckoutStrategy.MERGE)),
                        new LogTaskListener(Logger.getLogger(getClass().getName()), Level.INFO));
        List<GitHubNotificationRequest> notificationsC =
                instance.notifications(GitHubNotificationContext.build(job, run, src,
                        new PullRequestSCMHead("test-pr", "owner", "repo", "branch",
                                1, testBranch, SCMHeadOrigin.DEFAULT, ChangeRequestCheckoutStrategy.HEAD)),
                        new LogTaskListener(Logger.getLogger(getClass().getName()), Level.INFO));
        assertNotEquals(notificationsA, notificationsB);
        assertNotEquals(notificationsB, notificationsC);
        assertNotEquals(notificationsA, notificationsC);
    }

    @Test
    public void given_jobOrRun_then_differentURLs() throws Exception {
        FreeStyleProject job = j.createFreeStyleProject();
        GitHubSCMSource src = new GitHubSCMSource("example", "test");
        FreeStyleBuild run = j.buildAndAssertSuccess(job);
        DefaultGitHubNotificationStrategy instance = new DefaultGitHubNotificationStrategy();
        String urlA = instance.notifications(GitHubNotificationContext.build(null, run, src, new BranchSCMHead("master")),
                        new LogTaskListener(Logger.getLogger(getClass().getName()), Level.INFO)).get(0).getUrl();
        String urlB = instance.notifications(GitHubNotificationContext.build(job, null, src, new BranchSCMHead("master")),
                new LogTaskListener(Logger.getLogger(getClass().getName()), Level.INFO)).get(0).getUrl();
        assertNotEquals(urlA, urlB);
    }
}
