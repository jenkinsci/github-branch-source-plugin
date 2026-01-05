package org.jenkinsci.plugins.github_branch_source;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Collections;
import java.util.EnumSet;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import jenkins.scm.api.trait.SCMHeadFilter;
import jenkins.scm.api.trait.SCMHeadPrefilter;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

class OriginPullRequestDiscoveryTraitTest {

    @Test
    void given__discoverHeadMerge__when__appliedToContext__then__strategiesCorrect() {
        GitHubSCMSourceContext ctx = new GitHubSCMSourceContext(null, SCMHeadObserver.none());
        assumeFalse(ctx.wantBranches());
        assumeFalse(ctx.wantPRs());
        assumeTrue(ctx.prefilters().isEmpty());
        assumeTrue(ctx.filters().isEmpty());
        assumeTrue(ctx.authorities().stream()
                .noneMatch(
                        item -> item instanceof OriginPullRequestDiscoveryTrait.OriginChangeRequestSCMHeadAuthority));
        OriginPullRequestDiscoveryTrait instance =
                new OriginPullRequestDiscoveryTrait(EnumSet.allOf(ChangeRequestCheckoutStrategy.class));
        instance.decorateContext(ctx);
        assertThat(ctx.wantBranches(), is(false));
        assertThat(ctx.wantPRs(), is(true));
        assertThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assertThat(ctx.filters(), is(Collections.<SCMHeadFilter>emptyList()));
        assertThat(ctx.originPRStrategies(), Matchers.is(EnumSet.allOf(ChangeRequestCheckoutStrategy.class)));
        assertThat(
                ctx.authorities(),
                hasItem(instanceOf(OriginPullRequestDiscoveryTrait.OriginChangeRequestSCMHeadAuthority.class)));
    }

    @Test
    void given__discoverHeadOnly__when__appliedToContext__then__strategiesCorrect() {
        GitHubSCMSourceContext ctx = new GitHubSCMSourceContext(null, SCMHeadObserver.none());
        assumeFalse(ctx.wantBranches());
        assumeFalse(ctx.wantPRs());
        assumeTrue(ctx.prefilters().isEmpty());
        assumeTrue(ctx.filters().isEmpty());
        assumeTrue(ctx.authorities().stream()
                .noneMatch(
                        item -> item instanceof OriginPullRequestDiscoveryTrait.OriginChangeRequestSCMHeadAuthority));
        OriginPullRequestDiscoveryTrait instance =
                new OriginPullRequestDiscoveryTrait(EnumSet.of(ChangeRequestCheckoutStrategy.HEAD));
        instance.decorateContext(ctx);
        assertThat(ctx.wantBranches(), is(false));
        assertThat(ctx.wantPRs(), is(true));
        assertThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assertThat(ctx.filters(), is(Collections.<SCMHeadFilter>emptyList()));
        assertThat(ctx.originPRStrategies(), Matchers.is(EnumSet.of(ChangeRequestCheckoutStrategy.HEAD)));
        assertThat(
                ctx.authorities(),
                hasItem(instanceOf(OriginPullRequestDiscoveryTrait.OriginChangeRequestSCMHeadAuthority.class)));
    }

    @Test
    void given__discoverMergeOnly__when__appliedToContext__then__strategiesCorrect() {
        GitHubSCMSourceContext ctx = new GitHubSCMSourceContext(null, SCMHeadObserver.none());
        assumeFalse(ctx.wantBranches());
        assumeFalse(ctx.wantPRs());
        assumeTrue(ctx.prefilters().isEmpty());
        assumeTrue(ctx.filters().isEmpty());
        assumeTrue(ctx.authorities().stream()
                .noneMatch(
                        item -> item instanceof OriginPullRequestDiscoveryTrait.OriginChangeRequestSCMHeadAuthority));
        OriginPullRequestDiscoveryTrait instance =
                new OriginPullRequestDiscoveryTrait(EnumSet.of(ChangeRequestCheckoutStrategy.MERGE));
        instance.decorateContext(ctx);
        assertThat(ctx.wantBranches(), is(false));
        assertThat(ctx.wantPRs(), is(true));
        assertThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assertThat(ctx.filters(), is(Collections.<SCMHeadFilter>emptyList()));
        assertThat(ctx.originPRStrategies(), Matchers.is(EnumSet.of(ChangeRequestCheckoutStrategy.MERGE)));
        assertThat(
                ctx.authorities(),
                hasItem(instanceOf(OriginPullRequestDiscoveryTrait.OriginChangeRequestSCMHeadAuthority.class)));
    }

    @Test
    void given__programmaticConstructor__when__appliedToContext__then__strategiesCorrect() {
        GitHubSCMSourceContext ctx = new GitHubSCMSourceContext(null, SCMHeadObserver.none());
        assumeFalse(ctx.wantBranches());
        assumeFalse(ctx.wantPRs());
        assumeTrue(ctx.prefilters().isEmpty());
        assumeTrue(ctx.filters().isEmpty());
        assumeTrue(ctx.authorities().stream()
                .noneMatch(
                        item -> item instanceof OriginPullRequestDiscoveryTrait.OriginChangeRequestSCMHeadAuthority));
        OriginPullRequestDiscoveryTrait instance =
                new OriginPullRequestDiscoveryTrait(EnumSet.allOf(ChangeRequestCheckoutStrategy.class));
        instance.decorateContext(ctx);
        assertThat(ctx.wantBranches(), is(false));
        assertThat(ctx.wantPRs(), is(true));
        assertThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assertThat(ctx.filters(), is(Collections.<SCMHeadFilter>emptyList()));
        assertThat(ctx.originPRStrategies(), Matchers.is(EnumSet.allOf(ChangeRequestCheckoutStrategy.class)));
        assertThat(
                ctx.authorities(),
                hasItem(instanceOf(OriginPullRequestDiscoveryTrait.OriginChangeRequestSCMHeadAuthority.class)));
    }
}
