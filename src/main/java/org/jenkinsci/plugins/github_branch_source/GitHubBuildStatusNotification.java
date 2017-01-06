/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
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

import hudson.Extension;
import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.Item;
import hudson.model.ItemGroup;
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
import jenkins.model.Jenkins;
import jenkins.plugins.git.AbstractGitSCMSource.SCMRevisionImpl;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMRevisionAction;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceOwner;
import org.jenkinsci.plugins.displayurlapi.DisplayURLProvider;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Manages GitHub Statuses.
 *
 * Job (associated to a PR) scheduled: PENDING
 * Build doing a checkout: PENDING
 * Build done: SUCCESS, FAILURE or ERROR
 *
 */
public class GitHubBuildStatusNotification {

    private static final Logger LOGGER = Logger.getLogger(GitHubBuildStatusNotification.class.getName());

    private static void createCommitStatus(@Nonnull GHRepository repo, @Nonnull String revision, @Nonnull GHCommitState state, @Nonnull String url, @Nonnull String message, @Nonnull SCMHead head) throws IOException {
        LOGGER.log(Level.FINE, "{0}/commit/{1} {2} from {3}", new Object[] {repo.getHtmlUrl(), revision, state, url});
        String context;
        if (head instanceof PullRequestSCMHead) {
            if (((PullRequestSCMHead) head).isMerge()) {
                context = "continuous-integration/jenkins/pr-merge";
            } else {
                context = "continuous-integration/jenkins/pr-head";
            }
        } else {
            context = "continuous-integration/jenkins/branch";
        }
        repo.createCommitStatus(revision, state, url, message, context);
    }

    private static void createBuildCommitStatus(Run<?,?> build, TaskListener listener) {
        try {
            GHRepository repo = lookUpRepo(build.getParent());
            if (repo != null) {
                SCMRevisionAction action = build.getAction(SCMRevisionAction.class);
                if (action != null) {
                    SCMRevision revision = action.getRevision();
                    String url = DisplayURLProvider.get().getRunURL(build);
                    boolean ignoreError = false;
                    try {
                        Result result = build.getResult();
                        String revisionToNotify = resolveHeadCommit(repo, revision);
                        SCMHead head = revision.getHead();
                        if (Result.SUCCESS.equals(result)) {
                            createCommitStatus(repo, revisionToNotify, GHCommitState.SUCCESS, url, Messages.GitHubBuildStatusNotification_CommitStatus_Good(), head);
                        } else if (Result.UNSTABLE.equals(result)) {
                            createCommitStatus(repo, revisionToNotify, GHCommitState.FAILURE, url, Messages.GitHubBuildStatusNotification_CommitStatus_Unstable(), head);
                        } else if (Result.FAILURE.equals(result)) {
                            createCommitStatus(repo, revisionToNotify, GHCommitState.FAILURE, url, Messages.GitHubBuildStatusNotification_CommitStatus_Failure(), head);
                        } else if (result != null) { // ABORTED etc.
                            createCommitStatus(repo, revisionToNotify, GHCommitState.ERROR, url, Messages.GitHubBuildStatusNotification_CommitStatus_Other(), head);
                        } else {
                            ignoreError = true;
                            createCommitStatus(repo, revisionToNotify, GHCommitState.PENDING, url, Messages.GitHubBuildStatusNotification_CommitStatus_Pending(), head);
                        }
                        if (result != null) {
                            listener.getLogger().format("%n" + Messages.GitHubBuildStatusNotification_CommitStatusSet() + "%n%n");
                        }
                    } catch (FileNotFoundException fnfe) {
                        if (!ignoreError) {
                            listener.getLogger().format("%nCould not update commit status, please check if your scan " +
                                    "credentials belong to a member of the organization or a collaborator of the " +
                                    "repository and repo:status scope is selected%n%n");
                            LOGGER.log(Level.FINE, null, fnfe);
                        }
                    }
                }
            }
        } catch (IOException ioe) {
            listener.getLogger().format("%nCould not update commit status. Message: %s%n%n", ioe.getMessage());
            LOGGER.log(Level.FINE, "Could not update commit status", ioe);
        }

}

    /**
     * Returns the GitHub Repository associated to a Job.
     *
     * @param job A {@link Job}
     * @return A {@link GHRepository} or null, either if a scan credentials was not provided, or a GitHubSCMSource was not defined.
     * @throws IOException
     */
    private static @CheckForNull
    GHRepository lookUpRepo(@Nonnull Job<?,?> job) throws IOException {
        ItemGroup<?> multiBranchProject = job.getParent();
        if (multiBranchProject instanceof SCMSourceOwner) {
            SCMSourceOwner scmSourceOwner = (SCMSourceOwner) multiBranchProject;
            GitHubSCMSource source = getSCMSource(scmSourceOwner);
            if (source != null) {
                if (source.getScanCredentialsId() != null) {
                    GitHub github = Connector.connect(source.getApiUri(), Connector.lookupScanCredentials
                            (scmSourceOwner, null, source.getScanCredentialsId()));
                    return github.getRepository(source.getRepoOwner() + "/" + source.getRepository());
                }
            }
        }
        return null;
    }

    /**
     * It is possible having more than one SCMSource in our MultiBranchProject.
     * TODO: Does it make sense having more than one of the same type?
     *
     * @param scmSourceOwner An {@link Item} that owns {@link SCMSource} instances.
     * @return A {@link GitHubSCMSource} or null
     */
    @CheckForNull
    private static GitHubSCMSource getSCMSource(final SCMSourceOwner scmSourceOwner) {
        for (SCMSource scmSource : scmSourceOwner.getSCMSources()) {
            if (scmSource instanceof GitHubSCMSource) {
                return (GitHubSCMSource) scmSource;
            }
        }
        return null;
    }

    /**
     * With this listener one notifies to GitHub when a Job (associated to a PR) has been scheduled.
     * Sends: GHCommitState.PENDING
     */
    @Extension
    public static class PRJobScheduledListener extends QueueListener {

        /**
         * Manages the GitHub Commit Pending Status.
         */
        @Override
        public void onEnterWaiting(Queue.WaitingItem wi) {
            if (!(wi.task instanceof Job)) {
                return;
            }
            final long taskId = wi.getId();
            final Job<?,?> job = (Job) wi.task;
            // TODO would actually be better to use GitHubSCMSource.retrieve(SCMHead, TaskListener) (when properly implemented).
            // That would allow us to find the current head commit even for non-PR jobs.
            final SCMHead head = SCMHead.HeadByItem.findHead(job);
            if (!(head instanceof PullRequestSCMHead)) {
                return;
            }
            Computer.threadPoolForRemoting.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        GHRepository repo = lookUpRepo(job);
                        if (repo != null) {
                            int number = ((PullRequestSCMHead) head).getNumber();
                            GHPullRequest pr = repo.getPullRequest(number);
                            String url = DisplayURLProvider.get().getJobURL(job);
                            // Has not been built yet, so we can only guess that the current PR head is what will be built.
                            // In fact the submitter might push another commit before this build even starts.
                            if (Jenkins.getActiveInstance().getQueue().getItem(taskId) instanceof Queue.LeftItem) {
                                // we took too long and the item has left the queue, no longer valid to apply pending
                                // status. JobCheckOutListener is now responsible for setting the pending status.
                                return;
                            }
                            createCommitStatus(repo, pr.getHead().getSha(), GHCommitState.PENDING, url,
                                    Messages.GitHubBuildStatusNotification_CommitStatus_Queued(), head);
                        }
                    } catch (FileNotFoundException fnfe) {
                        LOGGER.log(Level.WARNING,
                                "Could not update commit status to PENDING. Valid scan credentials? Valid scopes?");
                        LOGGER.log(Level.FINE, null, fnfe);
                    } catch (IOException ioe) {
                        LOGGER.log(Level.WARNING,
                                "Could not update commit status to PENDING. Message: " + ioe.getMessage());
                        LOGGER.log(Level.FINE, null, ioe);
                    }
                }
            });
        }

    }

    /**
     * With this listener one notifies to GitHub when the SCM checkout process has started.
     * Possible option: GHCommitState.PENDING
     */
    @Extension
    public static class JobCheckOutListener extends SCMListener {

        @Override
        public void onCheckout(Run<?, ?> build, SCM scm, FilePath workspace, TaskListener listener, File changelogFile, SCMRevisionState pollingBaseline) throws Exception {
            createBuildCommitStatus(build, listener);
        }

    }

    /**
     * With this listener one notifies to GitHub the build result.
     * Possible options: GHCommitState.SUCCESS, GHCommitState.ERROR or GHCommitState.FAILURE
     */
    @Extension
    public static class JobCompletedListener extends RunListener<Run<?,?>> {

        @Override
        public void onCompleted(Run<?, ?> build, TaskListener listener) {
            createBuildCommitStatus(build, listener);
        }

    }

    private static String resolveHeadCommit(GHRepository repo, SCMRevision revision) throws IllegalArgumentException {
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
