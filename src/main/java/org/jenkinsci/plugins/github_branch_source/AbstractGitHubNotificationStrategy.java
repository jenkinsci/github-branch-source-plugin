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

import hudson.model.TaskListener;
import java.util.List;

/**
 * Represents a strategy for constructing GitHub status notifications
 *
 * @since 2.3.2
 */
public abstract class AbstractGitHubNotificationStrategy {

    /**
     * Creates the list of {@link GitHubNotificationRequest} for the given context.
     *
     * @param notificationContext {@link GitHubNotificationContext} the context details
     * @param listener the listener
     * @return a list of notification requests
     * @since 2.3.2
     */
    public abstract List<GitHubNotificationRequest> notifications(
            GitHubNotificationContext notificationContext, TaskListener listener);

    /** {@inheritDoc} */
    public abstract boolean equals(Object o);

    /** {@inheritDoc} */
    public abstract int hashCode();
}
