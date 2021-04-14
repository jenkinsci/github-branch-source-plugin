package org.jenkinsci.plugins.github_branch_source;

import com.google.common.collect.Sets;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMHeadOrigin;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import jenkins.scm.api.trait.SCMHeadPrefilter;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.*;

public class WildcardPullRequestLabelFilterTraitTest {

    @ClassRule
    public static JenkinsRule r = new JenkinsRule();

    @Test
    public void testNoLabelsAndNoIncludesGivenNotExcluded() {
        GitHubSCMSourceContext probe = new GitHubSCMSourceContext(null, SCMHeadObserver.collect());
        WildcardPullRequestLabelFilterTrait instance = new WildcardPullRequestLabelFilterTrait(null, null);
        instance.decorateContext(probe);
        List<SCMHeadPrefilter> prefilters = probe.prefilters();
        assertThat(prefilters, hasSize(1));
        SCMHeadPrefilter prefilter = prefilters.get(0);
        SCMHead scmHead = new PullRequestSCMHead(
                "PR-1",
                "does-not-exists",
                "http://does-not-exist.test",
                "feature/1",
                1,
                new BranchSCMHead("master"),
                SCMHeadOrigin.DEFAULT,
                Collections.emptySet(),
                ChangeRequestCheckoutStrategy.MERGE);
        assertThat(
                prefilter.isExcluded(new GitHubSCMSource("does-not-exist", "http://does-not-exist.test"), scmHead),
                equalTo(false));
    }

    @Test
    public void testNoLabelsAndWithIncludesGivenExcluded() {
        GitHubSCMSourceContext probe = new GitHubSCMSourceContext(null, SCMHeadObserver.collect());
        WildcardPullRequestLabelFilterTrait instance = new WildcardPullRequestLabelFilterTrait("include", null);
        instance.decorateContext(probe);
        List<SCMHeadPrefilter> prefilters = probe.prefilters();
        assertThat(prefilters, hasSize(1));
        SCMHeadPrefilter prefilter = prefilters.get(0);
        SCMHead scmHead = new PullRequestSCMHead(
                "PR-1",
                "does-not-exists",
                "http://does-not-exist.test",
                "feature/1",
                1,
                new BranchSCMHead("master"),
                SCMHeadOrigin.DEFAULT,
                Collections.emptySet(),
                ChangeRequestCheckoutStrategy.MERGE);
        assertThat(
                prefilter.isExcluded(new GitHubSCMSource("does-not-exist", "http://does-not-exist.test"), scmHead),
                equalTo(true));
    }

    @Test
    public void testLabelsMatchesIncludesNotExcludesNotExcluded() {
        GitHubSCMSourceContext probe = new GitHubSCMSourceContext(null, SCMHeadObserver.collect());
        WildcardPullRequestLabelFilterTrait instance = new WildcardPullRequestLabelFilterTrait("include", "exclude");
        instance.decorateContext(probe);
        List<SCMHeadPrefilter> prefilters = probe.prefilters();
        assertThat(prefilters, hasSize(1));
        SCMHeadPrefilter prefilter = prefilters.get(0);
        SCMHead scmHead = new PullRequestSCMHead(
                "PR-1",
                "does-not-exists",
                "http://does-not-exist.test",
                "feature/1",
                1,
                new BranchSCMHead("master"),
                SCMHeadOrigin.DEFAULT,
                Collections.singleton("include"),
                ChangeRequestCheckoutStrategy.MERGE);
        assertThat(
                prefilter.isExcluded(new GitHubSCMSource("does-not-exist", "http://does-not-exist.test"), scmHead),
                equalTo(false));
    }

    @Test
    public void testLabelsMatchesIncludesAndExcludesExcluded() {
        GitHubSCMSourceContext probe = new GitHubSCMSourceContext(null, SCMHeadObserver.collect());
        WildcardPullRequestLabelFilterTrait instance = new WildcardPullRequestLabelFilterTrait("include", "exclude");
        instance.decorateContext(probe);
        List<SCMHeadPrefilter> prefilters = probe.prefilters();
        assertThat(prefilters, hasSize(1));
        SCMHeadPrefilter prefilter = prefilters.get(0);
        SCMHead scmHead = new PullRequestSCMHead(
                "PR-1",
                "does-not-exists",
                "http://does-not-exist.test",
                "feature/1",
                1,
                new BranchSCMHead("master"),
                SCMHeadOrigin.DEFAULT,
                Sets.newHashSet("include", "exclude"),
                ChangeRequestCheckoutStrategy.MERGE);
        assertThat(
                prefilter.isExcluded(new GitHubSCMSource("does-not-exist", "http://does-not-exist.test"), scmHead),
                equalTo(true));
    }

    @Test
    public void testLabelsMatchesExcludesNotIncludesExcluded() {
        GitHubSCMSourceContext probe = new GitHubSCMSourceContext(null, SCMHeadObserver.collect());
        WildcardPullRequestLabelFilterTrait instance = new WildcardPullRequestLabelFilterTrait("include", "exclude");
        instance.decorateContext(probe);
        List<SCMHeadPrefilter> prefilters = probe.prefilters();
        assertThat(prefilters, hasSize(1));
        SCMHeadPrefilter prefilter = prefilters.get(0);
        SCMHead scmHead = new PullRequestSCMHead(
                "PR-1",
                "does-not-exists",
                "http://does-not-exist.test",
                "feature/1",
                1,
                new BranchSCMHead("master"),
                SCMHeadOrigin.DEFAULT,
                Collections.singleton("exclude"),
                ChangeRequestCheckoutStrategy.MERGE);
        assertThat(
                prefilter.isExcluded(new GitHubSCMSource("does-not-exist", "http://does-not-exist.test"), scmHead),
                equalTo(true));
    }
}
