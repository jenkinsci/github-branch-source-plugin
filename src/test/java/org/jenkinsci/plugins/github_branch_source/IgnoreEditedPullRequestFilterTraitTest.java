package org.jenkinsci.plugins.github_branch_source;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import jenkins.scm.api.SCMHeadObserver;
import org.junit.Test;

public class IgnoreEditedPullRequestFilterTraitTest extends GitSCMSourceBase {

    public IgnoreEditedPullRequestFilterTraitTest() {
        this.source = new GitHubSCMSource("cloudbeers", "yolo", null, false);
    }

    @Test
    public void testFlagDefaultsToFalse() {
        GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, SCMHeadObserver.collect());
        assertThat(context.ignoreEditedPullRequests(), is(false));
    }

    @Test
    public void testTraitEnablesFlag() {
        GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, SCMHeadObserver.collect());
        new IgnoreEditedPullRequestFilterTrait().decorateContext(context);
        assertThat(context.ignoreEditedPullRequests(), is(true));
    }

    @Test
    public void testTraitIsIdempotent() {
        GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, SCMHeadObserver.collect());
        new IgnoreEditedPullRequestFilterTrait().decorateContext(context);
        new IgnoreEditedPullRequestFilterTrait().decorateContext(context);
        assertThat(context.ignoreEditedPullRequests(), is(true));
    }
}
