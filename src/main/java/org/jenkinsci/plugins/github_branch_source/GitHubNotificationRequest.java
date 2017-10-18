package org.jenkinsci.plugins.github_branch_source;

import org.kohsuke.github.GHCommitState;

/**
 * @since TODO
 */
public class GitHubNotificationRequest {

    private final String context;
    private final String url;
    private final String message;
    private final GHCommitState state;
    private final boolean ignoreError;

    /**
     * @since TODO
     */
    public GitHubNotificationRequest(String context, String url, String message, GHCommitState state, boolean ignoreError) {
        this.context = context;
        this.url = url;
        this.message = message;
        this.state = state;
        this.ignoreError = ignoreError;
    }

    /**
     * @since TODO
     */
    public String getContext() {
        return context;
    }

    /**
     * @since TODO
     */
    public String getUrl() {
        return url;
    }

    /**
     * @since TODO
     */
    public String getMessage() {
        return message;
    }

    /**
     * @since TODO
     */
    public GHCommitState getState() {
        return state;
    }

    /**
     * @since TODO
     */
    public boolean isIgnoreError() {
        return ignoreError;
    }

    /**
     * @since TODO
     */
    @Override
    public String toString() {
        return "GitHubNotificationRequest{" +
                "context='" + context + '\'' +
                ", url='" + url + '\'' +
                ", message='" + message + '\'' +
                ", state=" + state +
                ", ignoreError=" + ignoreError +
                '}';
    }

    /**
     * @since TODO
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GitHubNotificationRequest that = (GitHubNotificationRequest) o;

        if (ignoreError != that.ignoreError) return false;
        if (context != null ? !context.equals(that.context) : that.context != null) return false;
        if (url != null ? !url.equals(that.url) : that.url != null) return false;
        if (message != null ? !message.equals(that.message) : that.message != null) return false;
        return state == that.state;
    }

    /**
     * @since TODO
     */
    @Override
    public int hashCode() {
        int result = context != null ? context.hashCode() : 0;
        result = 31 * result + (url != null ? url.hashCode() : 0);
        result = 31 * result + (message != null ? message.hashCode() : 0);
        result = 31 * result + (state != null ? state.hashCode() : 0);
        result = 31 * result + (ignoreError ? 1 : 0);
        return result;
    }
}
