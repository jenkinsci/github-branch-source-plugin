/*
 * The MIT License
 *
 * Copyright 2017 Steven Foster
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

import java.util.Objects;
import org.kohsuke.github.GHCommitState;

/**
 * Details of a GitHub status notification to be sent.
 *
 * <p>One GitHubNotificationRequest represents one notification. A strategy supplies a list of these
 * to request one or more notifications. Notifications are differentiated by their Context label. If
 * two notification requests with the same Context label are provided, one will override the other.
 *
 * @see <a href="https://developer.github.com/v3/repos/statuses/">GitHub API for details of the
 *     purpose of each notification field.</a>
 * @since 2.3.2
 */
public class GitHubNotificationRequest {

    private final String context;
    private final String url;
    private final String message;
    private final GHCommitState state;
    private final boolean ignoreError;

    /** @since 2.3.2 */
    private GitHubNotificationRequest(
            String context, String url, String message, GHCommitState state, boolean ignoreError) {
        this.context = context;
        this.url = url;
        this.message = message;
        this.state = state;
        this.ignoreError = ignoreError;
    }

    public static GitHubNotificationRequest build(
            String context, String url, String message, GHCommitState state, boolean ignoreError) {
        return new GitHubNotificationRequest(context, url, message, state, ignoreError);
    }

    /**
     * Returns the context label to be used for a notification
     *
     * @return context
     * @since 2.3.2
     */
    public String getContext() {
        return context;
    }

    /**
     * Returns the URL to be supplied with a notification
     *
     * @return url
     * @since 2.3.2
     */
    public String getUrl() {
        return url;
    }

    /**
     * Returns the message for a notification
     *
     * @return message
     * @since 2.3.2
     */
    public String getMessage() {
        return message;
    }

    /**
     * Returns the commit state of a notification
     *
     * @return state
     * @since 2.3.2
     */
    public GHCommitState getState() {
        return state;
    }

    /**
     * Returns whether the notification processor should ignore errors when interacting with GitHub
     *
     * @return ignoreError
     * @since 2.3.2
     */
    public boolean isIgnoreError() {
        return ignoreError;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "GitHubNotificationRequest{"
                + "context='"
                + context
                + '\''
                + ", url='"
                + url
                + '\''
                + ", message='"
                + message
                + '\''
                + ", state="
                + state
                + ", ignoreError="
                + ignoreError
                + '}';
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GitHubNotificationRequest that = (GitHubNotificationRequest) o;

        if (ignoreError != that.ignoreError) return false;
        if (!Objects.equals(context, that.context)) return false;
        if (!Objects.equals(url, that.url)) return false;
        if (!Objects.equals(message, that.message)) return false;
        return state == that.state;
    }

    /** {@inheritDoc} */
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
