package org.jenkinsci.plugins.github_branch_source;

import hudson.model.TaskListener;
import jenkins.scm.api.SCMHeadObserver;
import org.hamcrest.Matchers;
import org.junit.Test;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeThat;

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
        ctx.withNotificationStrategies(Collections.<AbstractGitHubNotificationStrategy>emptyList());
        assertThat(ctx.notificationStrategies().size(), is(1));
    }

    @Test
    public void given__defaultStrategy__when__emptyStrategyList__then__strategyAdded() throws Exception {
        GitHubSCMSourceContext ctx = new GitHubSCMSourceContext(null, SCMHeadObserver.none());
        assumeThat(ctx.notificationStrategies().size(), is(1));
        ctx.withNotificationStrategies(Collections.<AbstractGitHubNotificationStrategy>emptyList());
        assertThat(ctx.notificationStrategies().size(), is(1));
        ctx.withNotificationStrategy(new DefaultGitHubNotificationStrategy());
        assertThat(ctx.notificationStrategies().size(), is(1));
    }

    @Test
    public void given__defaultStrategyList__when__emptyStrategyList__then__strategyAdded() throws Exception {
        GitHubSCMSourceContext ctx = new GitHubSCMSourceContext(null, SCMHeadObserver.none());
        assumeThat(ctx.notificationStrategies().size(), is(1));
        ctx.withNotificationStrategies(Collections.<AbstractGitHubNotificationStrategy>emptyList());
        assertThat(ctx.notificationStrategies().size(), is(1));
        ctx.withNotificationStrategies(Collections.<AbstractGitHubNotificationStrategy>singletonList(new DefaultGitHubNotificationStrategy()));
        assertThat(ctx.notificationStrategies().size(), is(1));
    }

    @Test
    public void given__customStrategy__when__emptyStrategyList__then__noDefaultStrategy() throws Exception {
        GitHubSCMSourceContext ctx = new GitHubSCMSourceContext(null, SCMHeadObserver.none());
        assumeThat(ctx.notificationStrategies().size(), is(1));
        ctx.withNotificationStrategy(new TestNotificationStrategy());
        List<AbstractGitHubNotificationStrategy> strategies = ctx.notificationStrategies();
        assertThat(strategies.size(), is(1));
        assertThat(strategies.get(0), Matchers.<AbstractGitHubNotificationStrategy>instanceOf(TestNotificationStrategy.class));
    }

    private final class TestNotificationStrategy extends AbstractGitHubNotificationStrategy {

        @Override
        public List<GitHubNotificationRequest> notifications(GitHubNotificationContext notificationContext, TaskListener listener) {
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
