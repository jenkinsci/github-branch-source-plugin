package org.jenkinsci.plugins.github_branch_source;

import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.scm.api.*;
import org.jenkinsci.plugins.displayurlapi.DisplayURLProvider;
import org.kohsuke.github.GHCommitState;

public class GitHubBuildStatusNotificationStrategy {
    protected Job<?, ?> job;
    protected Run<?, ?> build;
    protected TaskListener listener;
    protected SCMSource source;
    protected SCMHead head;

    public GitHubBuildStatusNotificationStrategy() {}

    protected String generateContext() {
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

    protected String generateUrl() {
        String url = null;
        try {
            if (null != build) {
                url = DisplayURLProvider.get().getRunURL(build);
            }
            else {
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

    protected String generateMessage() {
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

    protected GHCommitState generateState() {
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

    public final GitHubBuildStatusNotificationBundle createNotification(Job<?, ?> job, Run<?, ?> build, TaskListener listener,
                                                                        SCMSource source, SCMHead head) {
        this.job = job;
        this.build = build;
        this.listener = listener;
        this.source = source;
        this.head = head;
        return new GitHubBuildStatusNotificationBundle(generateContext(), generateUrl(), generateMessage(), generateState());
    }
}
