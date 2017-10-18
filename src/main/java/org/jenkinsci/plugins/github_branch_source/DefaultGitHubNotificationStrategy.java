package org.jenkinsci.plugins.github_branch_source;

import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.scm.api.*;
import org.jenkinsci.plugins.displayurlapi.DisplayURLProvider;
import org.kohsuke.github.GHCommitState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @since TODO
 */
public class DefaultGitHubNotificationStrategy extends AbstractGitHubNotificationStrategy {
    public DefaultGitHubNotificationStrategy() {}

    /**
     * @since TODO
     */
    protected String generateContext(GitHubNotificationContext notificationContext, TaskListener listener) {
        SCMHead head = notificationContext.getHead();
        if (head instanceof PullRequestSCMHead) {
            if (((PullRequestSCMHead) head).isMerge()) {
                return "continuous-integration/jenkins/pr-merge";
            } else {
                return "continuous-integration/jenkins/pr-head";
            }
        } else {
            return "continuous-integration/jenkins/branch";
        }
    }

    /**
     * @since TODO
     */
    protected String generateUrl(GitHubNotificationContext notificationContext, TaskListener listener) {
        Run<?, ?> build = notificationContext.getBuild();
        Job<?, ?> job = notificationContext.getJob();
        String url = null;
        try {
            if (null != build) {
                url = DisplayURLProvider.get().getRunURL(build);
            }
            else if (null != job) {
                url = DisplayURLProvider.get().getJobURL(job);
            }
        } catch (IllegalStateException e) {
            listener.getLogger().println(
                    "Can not determine Jenkins root URL. Commit status notifications are disabled "
                            + "until a root URL is"
                            + " configured in Jenkins global configuration.");
        }
        return url;
    }

    /**
     * @since TODO
     */
    protected String generateMessage(GitHubNotificationContext notificationContext, TaskListener listener) {
        Run<?, ?> build = notificationContext.getBuild();
        if (null != build) {
            Result result = build.getResult();
            if (Result.SUCCESS.equals(result)) {
                return Messages.GitHubBuildStatusNotification_CommitStatus_Good();
            } else if (Result.UNSTABLE.equals(result)) {
                return Messages.GitHubBuildStatusNotification_CommitStatus_Unstable();
            } else if (Result.FAILURE.equals(result)) {
                return Messages.GitHubBuildStatusNotification_CommitStatus_Failure();
            } else if (Result.ABORTED.equals(result)) {
                return Messages.GitHubBuildStatusNotification_CommitStatus_Aborted();
            } else if (result != null) { // NOT_BUILT etc.
                return Messages.GitHubBuildStatusNotification_CommitStatus_Other();
            } else {
                return Messages.GitHubBuildStatusNotification_CommitStatus_Pending();
            }
        }
        return Messages.GitHubBuildStatusNotification_CommitStatus_Queued();
    }

    /**
     * @since TODO
     */
    protected GHCommitState generateState(GitHubNotificationContext notificationContext, TaskListener listener) {
        Run<?, ?> build = notificationContext.getBuild();
        if (null != build) {
            Result result = build.getResult();
            if (Result.SUCCESS.equals(result)) {
                return GHCommitState.SUCCESS;
            } else if (Result.UNSTABLE.equals(result)) {
                return GHCommitState.FAILURE;
            } else if (Result.FAILURE.equals(result)) {
                return GHCommitState.ERROR;
            } else if (Result.ABORTED.equals(result)) {
                return GHCommitState.ERROR;
            } else if (result != null) { // NOT_BUILT etc.
                return GHCommitState.ERROR;
            }
        }
        return GHCommitState.PENDING;
    }

    /**
     * @since TODO
     */
    protected boolean ignoreError(GitHubNotificationContext notificationContext, TaskListener listener) {
        Run<?, ?> build = notificationContext.getBuild();
        return null == build || null == build.getResult();
    }

    /**
     * @since TODO
     */
    public List<GitHubNotificationRequest> notifications(GitHubNotificationContext notificationContext, TaskListener listener) {
        return new ArrayList<>(Arrays.asList(new GitHubNotificationRequest(generateContext(notificationContext, listener),
                generateUrl(notificationContext, listener),
                generateMessage(notificationContext, listener),
                generateState(notificationContext, listener),
                ignoreError(notificationContext, listener))));
    }
}
