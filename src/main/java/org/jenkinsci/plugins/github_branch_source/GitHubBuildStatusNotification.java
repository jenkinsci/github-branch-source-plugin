/*
 * The MIT License
 *
 * Copyright 2016-2017 CloudBees, Inc., Steven Foster
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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.Job;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.model.listeners.SCMListener;
import hudson.model.queue.QueueListener;
import hudson.scm.SCM;
import hudson.scm.SCMRevisionState;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.plugins.git.AbstractGitSCMSource.SCMRevisionImpl;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMRevisionAction;
import jenkins.scm.api.SCMSource;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

/**
 * Manages GitHub Statuses.
 *
 * <ul>
 *   <li>Job (associated to a PR) scheduled: PENDING
 *   <li>Build doing a checkout: PENDING
 *   <li>Build done: SUCCESS, FAILURE or ERROR
 * </ul>
 */
public class GitHubBuildStatusNotification {

    private static final Logger LOGGER = Logger.getLogger(GitHubBuildStatusNotification.class.getName());

    private static void createBuildCommitStatus(Run<?, ?> build, TaskListener listener) {
        SCMSource src = SCMSource.SourceByItem.findSource(build.getParent());
        SCMRevision revision = src != null ? SCMRevisionAction.getRevision(src, build) : null;
        if (revision != null) { // only notify if we have a revision to notify
            try {
                GitHub gitHub = lookUpGitHub(build.getParent());
                try {
                    GHRepository repo = lookUpRepo(gitHub, build.getParent());
                    if (repo != null) {
                        Result result = build.getResult();
                        String revisionToNotify = resolveHeadCommit(revision);
                        SCMHead head = revision.getHead();
                        List<AbstractGitHubNotificationStrategy> strategies = new GitHubSCMSourceContext(
                                        null, SCMHeadObserver.none())
                                .withTraits(((GitHubSCMSource) src).getTraits())
                                .notificationStrategies();
                        for (AbstractGitHubNotificationStrategy strategy : strategies) {
                            // TODO allow strategies to combine/cooperate on a notification
                            GitHubNotificationContext notificationContext =
                                    GitHubNotificationContext.build(null, build, src, head);
                            List<GitHubNotificationRequest> details =
                                    strategy.notifications(notificationContext, listener);
                            for (GitHubNotificationRequest request : details) {
                                boolean ignoreError = request.isIgnoreError();
                                try {
                                    repo.createCommitStatus(
                                            revisionToNotify,
                                            request.getState(),
                                            request.getUrl(),
                                            request.getMessage(),
                                            request.getContext());
                                } catch (FileNotFoundException fnfe) {
                                    if (!ignoreError) {
                                        listener.getLogger()
                                                .format("%nCould not update commit status, please check if your scan "
                                                        + "credentials belong to a member of the organization or a collaborator of the "
                                                        + "repository and repo:status scope is selected%n%n");
                                        if (LOGGER.isLoggable(Level.FINE)) {
                                            LOGGER.log(
                                                    Level.FINE,
                                                    "Could not update commit status, for run "
                                                            + build.getFullDisplayName()
                                                            + " please check if your scan "
                                                            + "credentials belong to a member of the organization or a "
                                                            + "collaborator of the repository and repo:status scope is selected",
                                                    fnfe);
                                        }
                                    }
                                }
                            }
                        }
                        if (result != null) {
                            listener.getLogger()
                                    .format("%n" + Messages.GitHubBuildStatusNotification_CommitStatusSet() + "%n%n");
                        }
                    }
                } finally {
                    Connector.release(gitHub);
                }
            } catch (IOException ioe) {
                listener.getLogger()
                        .format("%n" + "Could not update commit status. Message: %s%n" + "%n", ioe.getMessage());
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.log(Level.FINE, "Could not update commit status of run " + build.getFullDisplayName(), ioe);
                }
            }
        }
    }

    /**
     * Returns the GitHub Repository associated to a Job.
     *
     * @param job A {@link Job}
     * @return A {@link GHRepository} or null, either if a scan credentials was not provided, or a
     *     GitHubSCMSource was not defined.
     * @throws IOException
     */
    @CheckForNull
    private static GHRepository lookUpRepo(GitHub github, @NonNull Job<?, ?> job) throws IOException {
        if (github == null) {
            return null;
        }
        SCMSource src = SCMSource.SourceByItem.findSource(job);
        if (src instanceof GitHubSCMSource) {
            GitHubSCMSource source = (GitHubSCMSource) src;
            if (source.getScanCredentialsId() != null) {
                return github.getRepository(source.getRepoOwner() + "/" + source.getRepository());
            }
        }
        return null;
    }

    /**
     * Returns the GitHub Repository associated to a Job.
     *
     * @param job A {@link Job}
     * @return A {@link GHRepository} or {@code null}, if any of: a credentials was not provided;
     *     notifications were disabled, or the job is not from a {@link GitHubSCMSource}.
     * @throws IOException
     */
    @CheckForNull
    private static GitHub lookUpGitHub(@NonNull Job<?, ?> job) throws IOException {
        SCMSource src = SCMSource.SourceByItem.findSource(job);
        if (src instanceof GitHubSCMSource) {
            GitHubSCMSource source = (GitHubSCMSource) src;
            if (new GitHubSCMSourceContext(null, SCMHeadObserver.none())
                    .withTraits(source.getTraits())
                    .notificationsDisabled()) {
                return null;
            }
            if (source.getScanCredentialsId() != null) {
                return Connector.connect(
                        source.getApiUri(),
                        Connector.lookupScanCredentials(
                                job, source.getApiUri(), source.getScanCredentialsId(), source.getRepoOwner()));
            }
        }
        return null;
    }

    /**
     * With this listener one notifies to GitHub when a Job has been scheduled.
     *
     * <p>Sends: GHCommitState.PENDING
     */
    @Extension
    public static class JobScheduledListener extends QueueListener {

        /** Manages the GitHub Commit Pending Status. */
        @Override
        public void onEnterWaiting(Queue.WaitingItem wi) {
            if (!(wi.task instanceof Job)) {
                return;
            }
            final long taskId = wi.getId();
            final Job<?, ?> job = (Job) wi.task;
            final SCMSource source = SCMSource.SourceByItem.findSource(job);
            if (!(source instanceof GitHubSCMSource)) {
                return;
            }
            final SCMHead head = SCMHead.HeadByItem.findHead(job);
            if (!(head instanceof PullRequestSCMHead)) {
                return;
            }
            final GitHubSCMSourceContext sourceContext = new GitHubSCMSourceContext(null, SCMHeadObserver.none())
                    .withTraits(((GitHubSCMSource) source).getTraits());
            if (sourceContext.notificationsDisabled()) {
                return;
            }
            // prevent delays in the queue when updating github
            Computer.threadPoolForRemoting.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        GitHub gitHub = lookUpGitHub(job);
                        try {
                            if (gitHub == null || gitHub.rateLimit().remaining < 8) {
                                // we are an optimization to signal commit status early, no point waiting for
                                // the rate limit to refresh as the checkout will ensure the status is set
                                return;
                            }
                            String hash = resolveHeadCommit(source.fetch(head, null));
                            if (gitHub.rateLimit().remaining < 8) { // should only need 2 but may be concurrent threads
                                // we are an optimization to signal commit status early, no point waiting for
                                // the rate limit to refresh as the checkout will ensure the status is set
                                return;
                            }
                            GHRepository repo = lookUpRepo(gitHub, job);
                            if (repo != null) {
                                // The submitter might push another commit before this build even starts.
                                if (Jenkins.get().getQueue().getItem(taskId) instanceof Queue.LeftItem) {
                                    // we took too long and the item has left the queue, no longer valid to apply
                                    // pending

                                    // status. JobCheckOutListener is now responsible for setting the pending
                                    // status.
                                    return;
                                }
                                List<AbstractGitHubNotificationStrategy> strategies =
                                        sourceContext.notificationStrategies();
                                for (AbstractGitHubNotificationStrategy strategy : strategies) {
                                    // TODO allow strategies to combine/cooperate on a notification
                                    GitHubNotificationContext notificationContext =
                                            GitHubNotificationContext.build(job, null, source, head);
                                    List<GitHubNotificationRequest> details =
                                            strategy.notifications(notificationContext, null);
                                    for (GitHubNotificationRequest request : details) {
                                        boolean ignoreErrors = request.isIgnoreError();
                                        try {
                                            repo.createCommitStatus(
                                                    hash,
                                                    request.getState(),
                                                    request.getUrl(),
                                                    request.getMessage(),
                                                    request.getContext());
                                        } catch (FileNotFoundException e) {
                                            if (!ignoreErrors) {
                                                LOGGER.log(
                                                        Level.WARNING,
                                                        "Could not update commit status to PENDING. Valid scan credentials? Valid scopes?",
                                                        LOGGER.isLoggable(Level.FINE) ? e : null);
                                            }
                                        }
                                    }
                                }
                            }
                        } finally {
                            Connector.release(gitHub);
                        }
                    } catch (FileNotFoundException e) {
                        LOGGER.log(
                                Level.WARNING,
                                "Could not update commit status to PENDING. Valid scan credentials? Valid scopes?",
                                LOGGER.isLoggable(Level.FINE) ? e : null);
                    } catch (IOException e) {
                        LOGGER.log(
                                Level.WARNING,
                                "Could not update commit status to PENDING. Message: " + e.getMessage(),
                                LOGGER.isLoggable(Level.FINE) ? e : null);
                    } catch (InterruptedException e) {
                        LOGGER.log(
                                Level.WARNING,
                                "Could not update commit status to PENDING. Rate limit exhausted",
                                LOGGER.isLoggable(Level.FINE) ? e : null);
                        LOGGER.log(Level.FINE, null, e);
                    }
                }
            });
        }
    }

    /**
     * With this listener one notifies to GitHub when the SCM checkout process has started.
     *
     * <p>Possible option: GHCommitState.PENDING
     */
    @Extension
    public static class JobCheckOutListener extends SCMListener {

        @Override
        public void onCheckout(
                Run<?, ?> build,
                SCM scm,
                FilePath workspace,
                TaskListener listener,
                File changelogFile,
                SCMRevisionState pollingBaseline)
                throws Exception {
            createBuildCommitStatus(build, listener);
        }
    }

    /**
     * With this listener one notifies to GitHub the build result.
     *
     * <p>Possible options: GHCommitState.SUCCESS, GHCommitState.ERROR or GHCommitState.FAILURE
     */
    @Extension
    public static class JobCompletedListener extends RunListener<Run<?, ?>> {

        @Override
        public void onCompleted(Run<?, ?> build, TaskListener listener) {
            createBuildCommitStatus(build, listener);
        }
    }

    private static String resolveHeadCommit(SCMRevision revision) throws IllegalArgumentException {
        if (revision instanceof SCMRevisionImpl) {
            return ((SCMRevisionImpl) revision).getHash();
        } else if (revision instanceof PullRequestSCMRevision) {
            return ((PullRequestSCMRevision) revision).getPullHash();
        } else {
            throw new IllegalArgumentException("did not recognize " + revision);
        }
    }

    private GitHubBuildStatusNotification() {}
}
