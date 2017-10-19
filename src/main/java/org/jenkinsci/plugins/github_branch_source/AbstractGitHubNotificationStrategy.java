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
    public abstract List<GitHubNotificationRequest> notifications(GitHubNotificationContext notificationContext, TaskListener listener);

    public abstract boolean equals(Object o);

    public abstract int hashCode();
}
