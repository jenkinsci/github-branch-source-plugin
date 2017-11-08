package org.jenkinsci.plugins.github_branch_source;

import jenkins.scm.api.SCMHeadObserver;
import org.junit.Test;
import java.util.Collections;
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
    public void given__emptyStrategiesList__when__appliedToContext__then__notificationsDisabled() throws Exception {
        GitHubSCMSourceContext ctx = new GitHubSCMSourceContext(null, SCMHeadObserver.none());
        assumeThat(ctx.notificationStrategies().size(), is(1));
        assumeThat(ctx.notificationsDisabled(), is(false));
        ctx.withNotificationStrategies(Collections.<AbstractGitHubNotificationStrategy>emptyList());
        assertThat(ctx.notificationStrategies().size(), is(0));
        assertThat(ctx.notificationsDisabled(), is(true));
    }

    @Test
    public void given__defaultStrategy__when__emptyStrategyList__then__strategyAdded() throws Exception {
        GitHubSCMSourceContext ctx = new GitHubSCMSourceContext(null, SCMHeadObserver.none());
        assumeThat(ctx.notificationStrategies().size(), is(1));
        assumeThat(ctx.notificationsDisabled(), is(false));
        ctx.withNotificationStrategies(Collections.<AbstractGitHubNotificationStrategy>emptyList());
        assertThat(ctx.notificationStrategies().size(), is(0));
        ctx.withNotificationStrategy(new DefaultGitHubNotificationStrategy());
        assertThat(ctx.notificationStrategies().size(), is(1));
    }

    @Test
    public void given__defaultStrategyList__when__emptyStrategyList__then__strategyAdded() throws Exception {
        GitHubSCMSourceContext ctx = new GitHubSCMSourceContext(null, SCMHeadObserver.none());
        assumeThat(ctx.notificationStrategies().size(), is(1));
        assumeThat(ctx.notificationsDisabled(), is(false));
        ctx.withNotificationStrategies(Collections.<AbstractGitHubNotificationStrategy>emptyList());
        assertThat(ctx.notificationStrategies().size(), is(0));
        ctx.withNotificationStrategies(Collections.<AbstractGitHubNotificationStrategy>singletonList(new DefaultGitHubNotificationStrategy()));
        assertThat(ctx.notificationStrategies().size(), is(1));
    }
}
