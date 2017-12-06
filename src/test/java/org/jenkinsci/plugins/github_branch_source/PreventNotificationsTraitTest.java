package org.jenkinsci.plugins.github_branch_source;

import jenkins.scm.api.SCMHeadObserver;
import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeThat;

public class PreventNotificationsTraitTest {

    @Test
    public void settings_initial() throws Exception {
        GitHubSCMSourceContext ctx = new GitHubSCMSourceContext(null, SCMHeadObserver.none());
        assumeThat(ctx.notificationsDisabled(), is(false));
        assumeThat(ctx.hideUrlInNotifications(), is(false));
    }

    @Test
    public void notification_disabled() throws Exception {
        GitHubSCMSourceContext ctx = new GitHubSCMSourceContext(null, SCMHeadObserver.none());
        PreventNotificationsTrait instance = new PreventNotificationsTrait(true, false);
        instance.decorateContext(ctx);
        assertThat(ctx.notificationsDisabled(), is(true));
        assertThat(ctx.hideUrlInNotifications(), is(false));
    }

    @Test
    public void hideUrl_enabled() throws Exception {
        GitHubSCMSourceContext ctx = new GitHubSCMSourceContext(null, SCMHeadObserver.none());
        PreventNotificationsTrait instance = new PreventNotificationsTrait(false, true);
        instance.decorateContext(ctx);
        assertThat(ctx.notificationsDisabled(), is(false));
        assertThat(ctx.hideUrlInNotifications(), is(true));
    }
}
