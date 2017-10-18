package org.jenkinsci.plugins.github_branch_source;

import hudson.ExtensionPoint;
import hudson.model.TaskListener;

import java.util.List;

/**
 * @since TODO
 */
public abstract class AbstractGitHubNotificationStrategy implements ExtensionPoint {

    /**
     * @since TODO
     */
    abstract List<GitHubNotificationRequest> notifications(GitHubNotificationContext notificationContext, TaskListener listener);
}
