package org.jenkinsci.plugins.github_branch_source;

import hudson.model.TaskListener;

import java.util.Collections;
import java.util.List;

/**
 * @since TODO
 */
public final class DefaultGitHubNotificationStrategy extends AbstractGitHubNotificationStrategy {
    public DefaultGitHubNotificationStrategy() {}

    /**
     * @since TODO
     */
    public List<GitHubNotificationRequest> notifications(GitHubNotificationContext notificationContext, TaskListener listener) {
        return Collections.singletonList(GitHubNotificationRequest.build(notificationContext.getDefaultContext(listener),
                notificationContext.getDefaultUrl(listener),
                notificationContext.getDefaultMessage(listener),
                notificationContext.getDefaultState(listener),
                notificationContext.getDefaultIgnoreError(listener)));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return (o == null || getClass() != o.getClass());
    }

    @Override
    public int hashCode() {
        return 42;
    }
}
