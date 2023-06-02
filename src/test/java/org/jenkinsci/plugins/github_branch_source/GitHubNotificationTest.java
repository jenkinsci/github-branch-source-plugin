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

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeThat;

import hudson.model.TaskListener;
import java.util.Collections;
import java.util.List;
import jenkins.scm.api.SCMHeadObserver;
import org.hamcrest.Matchers;
import org.junit.Test;

public class GitHubNotificationTest {

    @Test
    public void given__notificationsDisabled__when__appliedToContext__then__notificationsDisabled() throws Exception {
        GitHubSCMSourceContext ctx = new GitHubSCMSourceContext(null, SCMHeadObserver.none());
        assumeThat(ctx.notificationsDisabled(), is(false));
        ctx.withNotificationsDisabled(true);
        assertThat(ctx.notificationsDisabled(), is(true));
        ctx.withNotificationsDisabled(false);
        assertThat(ctx.notificationsDisabled(), is(false));
    }

    @Test
    public void given__defaultNotificationStrategy__when__appliedToContext__then__duplicatesRemoved() throws Exception {
        GitHubSCMSourceContext ctx = new GitHubSCMSourceContext(null, SCMHeadObserver.none());
        assumeThat(ctx.notificationStrategies().size(), is(1));
        ctx.withNotificationStrategy(new DefaultGitHubNotificationStrategy());
        assertThat(ctx.notificationStrategies().size(), is(1));
    }

    @Test
    public void given__emptyStrategiesList__when__appliedToContext__then__defaultApplied() throws Exception {
        GitHubSCMSourceContext ctx = new GitHubSCMSourceContext(null, SCMHeadObserver.none());
        assumeThat(ctx.notificationStrategies().size(), is(1));
        ctx.withNotificationStrategies(Collections.emptyList());
        assertThat(ctx.notificationStrategies().size(), is(1));
    }

    @Test
    public void given__defaultStrategy__when__emptyStrategyList__then__strategyAdded() throws Exception {
        GitHubSCMSourceContext ctx = new GitHubSCMSourceContext(null, SCMHeadObserver.none());
        assumeThat(ctx.notificationStrategies().size(), is(1));
        ctx.withNotificationStrategies(Collections.emptyList());
        assertThat(ctx.notificationStrategies().size(), is(1));
        ctx.withNotificationStrategy(new DefaultGitHubNotificationStrategy());
        assertThat(ctx.notificationStrategies().size(), is(1));
    }

    @Test
    public void given__defaultStrategyList__when__emptyStrategyList__then__strategyAdded() throws Exception {
        GitHubSCMSourceContext ctx = new GitHubSCMSourceContext(null, SCMHeadObserver.none());
        assumeThat(ctx.notificationStrategies().size(), is(1));
        ctx.withNotificationStrategies(Collections.emptyList());
        assertThat(ctx.notificationStrategies().size(), is(1));
        ctx.withNotificationStrategies(Collections.singletonList(new DefaultGitHubNotificationStrategy()));
        assertThat(ctx.notificationStrategies().size(), is(1));
    }

    @Test
    public void given__customStrategy__when__emptyStrategyList__then__noDefaultStrategy() throws Exception {
        GitHubSCMSourceContext ctx = new GitHubSCMSourceContext(null, SCMHeadObserver.none());
        assumeThat(ctx.notificationStrategies().size(), is(1));
        ctx.withNotificationStrategy(new TestNotificationStrategy());
        List<AbstractGitHubNotificationStrategy> strategies = ctx.notificationStrategies();
        assertThat(strategies.size(), is(1));
        assertThat(strategies.get(0), Matchers.instanceOf(TestNotificationStrategy.class));
    }

    private final class TestNotificationStrategy extends AbstractGitHubNotificationStrategy {

        @Override
        public List<GitHubNotificationRequest> notifications(
                GitHubNotificationContext notificationContext, TaskListener listener) {
            return null;
        }

        @Override
        public boolean equals(Object o) {
            return false;
        }

        @Override
        public int hashCode() {
            return 0;
        }
    }
}
