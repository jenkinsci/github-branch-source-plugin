package org.jenkinsci.plugins.github_branch_source;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.util.LogTaskListener;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.*;

public class DefaultGitHubNotificationStrategyTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void example() throws Exception {
        FreeStyleProject job = j.createFreeStyleProject();
        GitHubSCMSource src = new GitHubSCMSource("exmaple", "test");
        FreeStyleBuild run = j.buildAndAssertSuccess(job);
        DefaultGitHubNotificationStrategy instance = new DefaultGitHubNotificationStrategy();
        List<GitHubNotificationRequest> notifications =
                instance.notifications(GitHubNotificationContext.build(job, run, src, new BranchSCMHead("master")),
                        new LogTaskListener(
                                Logger.getLogger(getClass().getName()), Level.INFO));
        assertThat(notifications, hasSize(1));
        // TODO write some more comprehensive tests
    }

}
