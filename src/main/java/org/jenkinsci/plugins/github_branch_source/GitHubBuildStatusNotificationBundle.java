package org.jenkinsci.plugins.github_branch_source;

import org.kohsuke.github.GHCommitState;

public class GitHubBuildStatusNotificationBundle {

    private final String context;
    private final String url;
    private final String message;
    private final GHCommitState state;

    public GitHubBuildStatusNotificationBundle(String context, String url, String message, GHCommitState state) {
        this.context = context;
        this.url = url;
        this.message = message;
        this.state = state;
    }

    public String getContext() {
        return context;
    }

    public String getUrl() {
        return url;
    }

    public String getMessage() {
        return message;
    }

    public GHCommitState getState() {
        return state;
    }

    @Override
    public String toString() {
        return "Notification bundle [ context=" + context + ", url=" + url + ", message=" + message + ", state=" +
                state + " ]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GitHubBuildStatusNotificationBundle that = (GitHubBuildStatusNotificationBundle) o;

        if (context != null ? !context.equals(that.context) : that.context != null) return false;
        if (url != null ? !url.equals(that.url) : that.url != null) return false;
        if (message != null ? !message.equals(that.message) : that.message != null) return false;
        return state == that.state;
    }

    @Override
    public int hashCode() {
        int result = context != null ? context.hashCode() : 0;
        result = 31 * result + (url != null ? url.hashCode() : 0);
        result = 31 * result + (message != null ? message.hashCode() : 0);
        result = 31 * result + (state != null ? state.hashCode() : 0);
        return result;
    }
}
