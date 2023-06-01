/*
 * The MIT License
 *
 * Copyright 2017 Steven Foster
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.github_branch_source;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.*;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.util.LogTaskListener;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.scm.api.SCMHeadOrigin;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class DefaultGitHubNotificationStrategyTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void given_basicJob_then_singleNotification() throws Exception {
        List<GitHubSCMSource> srcs = Arrays.asList(
                new GitHubSCMSource("example", "test", null, false),
                new GitHubSCMSource("", "", "http://github.com/example/test", true));
        for (GitHubSCMSource src : srcs) {
            FreeStyleProject job = j.createFreeStyleProject();
            FreeStyleBuild run = j.buildAndAssertSuccess(job);
            DefaultGitHubNotificationStrategy instance = new DefaultGitHubNotificationStrategy();
            List<GitHubNotificationRequest> notifications = instance.notifications(
                    GitHubNotificationContext.build(job, run, src, new BranchSCMHead("master")),
                    new LogTaskListener(Logger.getLogger(getClass().getName()), Level.INFO));
            assertThat(notifications, hasSize(1));
        }
    }

    @Test
    public void given_differentSCMheads_then_distinctNotifications() throws Exception {
        List<GitHubSCMSource> srcs = Arrays.asList(
                new GitHubSCMSource("example", "test", "http://github.com/ignored/ignored", false),
                new GitHubSCMSource("", "", "http://github.com/example/test", true));
        for (GitHubSCMSource src : srcs) {
            FreeStyleProject job = j.createFreeStyleProject();
            FreeStyleBuild run = j.buildAndAssertSuccess(job);
            DefaultGitHubNotificationStrategy instance = new DefaultGitHubNotificationStrategy();
            BranchSCMHead testBranch = new BranchSCMHead("master");
            List<GitHubNotificationRequest> notificationsA = instance.notifications(
                    GitHubNotificationContext.build(job, run, src, testBranch),
                    new LogTaskListener(Logger.getLogger(getClass().getName()), Level.INFO));
            List<GitHubNotificationRequest> notificationsB = instance.notifications(
                    GitHubNotificationContext.build(
                            job,
                            run,
                            src,
                            new PullRequestSCMHead(
                                    "test-pr",
                                    "owner",
                                    "repo",
                                    "branch",
                                    1,
                                    testBranch,
                                    SCMHeadOrigin.DEFAULT,
                                    ChangeRequestCheckoutStrategy.MERGE)),
                    new LogTaskListener(Logger.getLogger(getClass().getName()), Level.INFO));
            List<GitHubNotificationRequest> notificationsC = instance.notifications(
                    GitHubNotificationContext.build(
                            job,
                            run,
                            src,
                            new PullRequestSCMHead(
                                    "test-pr",
                                    "owner",
                                    "repo",
                                    "branch",
                                    1,
                                    testBranch,
                                    SCMHeadOrigin.DEFAULT,
                                    ChangeRequestCheckoutStrategy.HEAD)),
                    new LogTaskListener(Logger.getLogger(getClass().getName()), Level.INFO));
            assertNotEquals(notificationsA, notificationsB);
            assertNotEquals(notificationsB, notificationsC);
            assertNotEquals(notificationsA, notificationsC);
        }
    }

    @Test
    public void given_jobOrRun_then_differentURLs() throws Exception {
        List<GitHubSCMSource> srcs = Arrays.asList(
                new GitHubSCMSource("example", "test", null, false),
                new GitHubSCMSource("", "", "http://github.com/example/test", true));
        for (GitHubSCMSource src : srcs) {
            FreeStyleProject job = j.createFreeStyleProject();
            FreeStyleBuild run = j.buildAndAssertSuccess(job);
            DefaultGitHubNotificationStrategy instance = new DefaultGitHubNotificationStrategy();
            String urlA = instance.notifications(
                            GitHubNotificationContext.build(null, run, src, new BranchSCMHead("master")),
                            new LogTaskListener(Logger.getLogger(getClass().getName()), Level.INFO))
                    .get(0)
                    .getUrl();
            String urlB = instance.notifications(
                            GitHubNotificationContext.build(job, null, src, new BranchSCMHead("master")),
                            new LogTaskListener(Logger.getLogger(getClass().getName()), Level.INFO))
                    .get(0)
                    .getUrl();
            assertNotEquals(urlA, urlB);
        }
    }
}
