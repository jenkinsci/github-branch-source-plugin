package org.jenkinsci.plugins.github_branch_source;

import hudson.model.TaskListener;
import org.kohsuke.github.GHCommitState;

import java.util.Collections;
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
        return GitHubNotificationContext.getDefaultContext(notificationContext, listener);
    }

    /**
     * @since TODO
     */
    protected String generateUrl(GitHubNotificationContext notificationContext, TaskListener listener) {
        return GitHubNotificationContext.getDefaultUrl(notificationContext, listener);
    }

    /**
     * @since TODO
     */
    protected String generateMessage(GitHubNotificationContext notificationContext, TaskListener listener) {
        return GitHubNotificationContext.getDefaultMessage(notificationContext, listener);
    }

    /**
     * @since TODO
     */
    protected GHCommitState generateState(GitHubNotificationContext notificationContext, TaskListener listener) {
        return GitHubNotificationContext.getDefaultState(notificationContext, listener);
    }

    /**
     * @since TODO
     */
    protected boolean ignoreError(GitHubNotificationContext notificationContext, TaskListener listener) {
        return GitHubNotificationContext.getDefaultIgnoreError(notificationContext, listener);
    }

    /**
     * @since TODO
     */
    public List<GitHubNotificationRequest> notifications(GitHubNotificationContext notificationContext, TaskListener listener) {
        return Collections.singletonList(GitHubNotificationRequest.build(generateContext(notificationContext, listener),
                generateUrl(notificationContext, listener),
                generateMessage(notificationContext, listener),
                generateState(notificationContext, listener),
                ignoreError(notificationContext, listener)));
    }
}
