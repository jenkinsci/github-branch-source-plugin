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
import java.util.Collections;
import java.util.List;

/**
 * Default implementation of {@link AbstractGitHubNotificationStrategy}
 *
 * @since 2.3.2
 */
public final class DefaultGitHubNotificationStrategy extends AbstractGitHubNotificationStrategy {

    /** {@inheritDoc} */
    public List<GitHubNotificationRequest> notifications(
            GitHubNotificationContext notificationContext, TaskListener listener) {
        return Collections.singletonList(GitHubNotificationRequest.build(
                notificationContext.getDefaultContext(listener),
                notificationContext.getDefaultUrl(listener),
                notificationContext.getDefaultMessage(listener),
                notificationContext.getDefaultState(listener),
                notificationContext.getDefaultIgnoreError(listener)));
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object o) {
        return this == o || (o != null && getClass() == o.getClass());
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return 42;
    }
}
