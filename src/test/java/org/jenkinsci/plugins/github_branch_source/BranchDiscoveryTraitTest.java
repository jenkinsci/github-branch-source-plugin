package org.jenkinsci.plugins.github_branch_source;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import hudson.util.ListBoxModel;
import java.util.Collections;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.trait.SCMHeadFilter;
import jenkins.scm.api.trait.SCMHeadPrefilter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class BranchDiscoveryTraitTest {

    private static JenkinsRule j;

    @BeforeAll
    static void beforeAll(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void given__discoverAll__when__appliedToContext__then__noFilter() {
        GitHubSCMSourceContext ctx = new GitHubSCMSourceContext(null, SCMHeadObserver.none());
        assumeFalse(ctx.wantBranches());
        assumeFalse(ctx.wantPRs());
        assumeTrue(ctx.prefilters().isEmpty());
        assumeTrue(ctx.filters().isEmpty());
        assumeTrue(ctx.authorities().stream()
                .noneMatch(item -> item instanceof BranchDiscoveryTrait.BranchSCMHeadAuthority));
        BranchDiscoveryTrait instance = new BranchDiscoveryTrait(true, true);
        instance.decorateContext(ctx);
        assertThat(ctx.wantBranches(), is(true));
        assertThat(ctx.wantPRs(), is(false));
        assertThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assertThat(ctx.filters(), is(Collections.<SCMHeadFilter>emptyList()));
        assertThat(ctx.authorities(), hasItem(instanceOf(BranchDiscoveryTrait.BranchSCMHeadAuthority.class)));
    }

    @Test
    void given__excludingPRs__when__appliedToContext__then__filter() {
        GitHubSCMSourceContext ctx = new GitHubSCMSourceContext(null, SCMHeadObserver.none());
        assumeFalse(ctx.wantBranches());
        assumeFalse(ctx.wantPRs());
        assumeTrue(ctx.prefilters().isEmpty());
        assumeTrue(ctx.filters().isEmpty());
        assumeTrue(ctx.authorities().stream()
                .noneMatch(item -> item instanceof BranchDiscoveryTrait.BranchSCMHeadAuthority));
        BranchDiscoveryTrait instance = new BranchDiscoveryTrait(true, false);
        instance.decorateContext(ctx);
        assertThat(ctx.wantBranches(), is(true));
        assertThat(ctx.wantPRs(), is(true));
        assertThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assertThat(
                ctx.filters(), contains(instanceOf(BranchDiscoveryTrait.ExcludeOriginPRBranchesSCMHeadFilter.class)));
        assertThat(ctx.authorities(), hasItem(instanceOf(BranchDiscoveryTrait.BranchSCMHeadAuthority.class)));
    }

    @Test
    void given__onlyPRs__when__appliedToContext__then__filter() {
        GitHubSCMSourceContext ctx = new GitHubSCMSourceContext(null, SCMHeadObserver.none());
        assumeFalse(ctx.wantBranches());
        assumeFalse(ctx.wantPRs());
        assumeTrue(ctx.prefilters().isEmpty());
        assumeTrue(ctx.filters().isEmpty());
        assumeTrue(ctx.authorities().stream()
                .noneMatch(item -> item instanceof BranchDiscoveryTrait.BranchSCMHeadAuthority));
        BranchDiscoveryTrait instance = new BranchDiscoveryTrait(false, true);
        instance.decorateContext(ctx);
        assertThat(ctx.wantBranches(), is(true));
        assertThat(ctx.wantPRs(), is(true));
        assertThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assertThat(ctx.filters(), contains(instanceOf(BranchDiscoveryTrait.OnlyOriginPRBranchesSCMHeadFilter.class)));
        assertThat(ctx.authorities(), hasItem(instanceOf(BranchDiscoveryTrait.BranchSCMHeadAuthority.class)));
    }

    @Test
    void given__descriptor__when__displayingOptions__then__allThreePresent() {
        ListBoxModel options = j.jenkins
                .getDescriptorByType(BranchDiscoveryTrait.DescriptorImpl.class)
                .doFillStrategyIdItems();
        assertThat(options.size(), is(3));
        assertThat(options.get(0).value, is("1"));
        assertThat(options.get(1).value, is("2"));
        assertThat(options.get(2).value, is("3"));
    }
}
