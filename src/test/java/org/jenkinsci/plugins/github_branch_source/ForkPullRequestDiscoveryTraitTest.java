package org.jenkinsci.plugins.github_branch_source;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeThat;

import hudson.util.XStream2;
import java.util.Collections;
import java.util.EnumSet;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import jenkins.scm.api.trait.SCMHeadFilter;
import jenkins.scm.api.trait.SCMHeadPrefilter;
import org.hamcrest.Matchers;
import org.junit.Test;

public class ForkPullRequestDiscoveryTraitTest {
    @Test
    public void xstream() throws Exception {
        System.out.println(new XStream2()
                .toXML(new ForkPullRequestDiscoveryTrait(3, new ForkPullRequestDiscoveryTrait.TrustContributors())));
    }

    @Test
    public void given__discoverHeadMerge__when__appliedToContext__then__strategiesCorrect() throws Exception {
        GitHubSCMSourceContext ctx = new GitHubSCMSourceContext(null, SCMHeadObserver.none());
        assumeThat(ctx.wantBranches(), is(false));
        assumeThat(ctx.wantPRs(), is(false));
        assumeThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assumeThat(ctx.filters(), is(Collections.<SCMHeadFilter>emptyList()));
        assumeThat(ctx.authorities(), not(hasItem(instanceOf(ForkPullRequestDiscoveryTrait.TrustContributors.class))));
        ForkPullRequestDiscoveryTrait instance = new ForkPullRequestDiscoveryTrait(
                EnumSet.allOf(ChangeRequestCheckoutStrategy.class),
                new ForkPullRequestDiscoveryTrait.TrustContributors());
        instance.decorateContext(ctx);
        assertThat(ctx.wantBranches(), is(false));
        assertThat(ctx.wantPRs(), is(true));
        assertThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assertThat(ctx.filters(), is(Collections.<SCMHeadFilter>emptyList()));
        assertThat(ctx.forkPRStrategies(), Matchers.is(EnumSet.allOf(ChangeRequestCheckoutStrategy.class)));
        assertThat(ctx.authorities(), hasItem(instanceOf(ForkPullRequestDiscoveryTrait.TrustContributors.class)));
    }

    @Test
    public void given__discoverHeadOnly__when__appliedToContext__then__strategiesCorrect() throws Exception {
        GitHubSCMSourceContext ctx = new GitHubSCMSourceContext(null, SCMHeadObserver.none());
        assumeThat(ctx.wantBranches(), is(false));
        assumeThat(ctx.wantPRs(), is(false));
        assumeThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assumeThat(ctx.filters(), is(Collections.<SCMHeadFilter>emptyList()));
        assumeThat(ctx.authorities(), not(hasItem(instanceOf(ForkPullRequestDiscoveryTrait.TrustContributors.class))));
        ForkPullRequestDiscoveryTrait instance = new ForkPullRequestDiscoveryTrait(
                EnumSet.of(ChangeRequestCheckoutStrategy.HEAD), new ForkPullRequestDiscoveryTrait.TrustContributors());
        instance.decorateContext(ctx);
        assertThat(ctx.wantBranches(), is(false));
        assertThat(ctx.wantPRs(), is(true));
        assertThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assertThat(ctx.filters(), is(Collections.<SCMHeadFilter>emptyList()));
        assertThat(ctx.forkPRStrategies(), Matchers.is(EnumSet.of(ChangeRequestCheckoutStrategy.HEAD)));
        assertThat(ctx.authorities(), hasItem(instanceOf(ForkPullRequestDiscoveryTrait.TrustContributors.class)));
    }

    @Test
    public void given__discoverMergeOnly__when__appliedToContext__then__strategiesCorrect() throws Exception {
        GitHubSCMSourceContext ctx = new GitHubSCMSourceContext(null, SCMHeadObserver.none());
        assumeThat(ctx.wantBranches(), is(false));
        assumeThat(ctx.wantPRs(), is(false));
        assumeThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assumeThat(ctx.filters(), is(Collections.<SCMHeadFilter>emptyList()));
        assumeThat(ctx.authorities(), not(hasItem(instanceOf(ForkPullRequestDiscoveryTrait.TrustContributors.class))));
        ForkPullRequestDiscoveryTrait instance = new ForkPullRequestDiscoveryTrait(
                EnumSet.of(ChangeRequestCheckoutStrategy.MERGE), new ForkPullRequestDiscoveryTrait.TrustContributors());
        instance.decorateContext(ctx);
        assertThat(ctx.wantBranches(), is(false));
        assertThat(ctx.wantPRs(), is(true));
        assertThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assertThat(ctx.filters(), is(Collections.<SCMHeadFilter>emptyList()));
        assertThat(ctx.forkPRStrategies(), Matchers.is(EnumSet.of(ChangeRequestCheckoutStrategy.MERGE)));
        assertThat(ctx.authorities(), hasItem(instanceOf(ForkPullRequestDiscoveryTrait.TrustContributors.class)));
    }

    @Test
    public void given__nonDefaultTrust__when__appliedToContext__then__authoritiesCorrect() throws Exception {
        GitHubSCMSourceContext ctx = new GitHubSCMSourceContext(null, SCMHeadObserver.none());
        assumeThat(ctx.wantBranches(), is(false));
        assumeThat(ctx.wantPRs(), is(false));
        assumeThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assumeThat(ctx.filters(), is(Collections.<SCMHeadFilter>emptyList()));
        assumeThat(ctx.authorities(), not(hasItem(instanceOf(ForkPullRequestDiscoveryTrait.TrustContributors.class))));
        ForkPullRequestDiscoveryTrait instance = new ForkPullRequestDiscoveryTrait(
                EnumSet.allOf(ChangeRequestCheckoutStrategy.class), new ForkPullRequestDiscoveryTrait.TrustEveryone());
        instance.decorateContext(ctx);
        assertThat(ctx.wantBranches(), is(false));
        assertThat(ctx.wantPRs(), is(true));
        assertThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assertThat(ctx.filters(), is(Collections.<SCMHeadFilter>emptyList()));
        assertThat(ctx.forkPRStrategies(), Matchers.is(EnumSet.allOf(ChangeRequestCheckoutStrategy.class)));
        assertThat(ctx.authorities(), hasItem(instanceOf(ForkPullRequestDiscoveryTrait.TrustEveryone.class)));
    }

    @Test
    public void given__externalApproval__when__appliedToContext__then__authoritiesCorrect() throws Exception {
        GitHubSCMSourceContext ctx = new GitHubSCMSourceContext(null, SCMHeadObserver.none());
        assumeThat(ctx.wantBranches(), is(false));
        assumeThat(ctx.wantPRs(), is(false));
        assumeThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assumeThat(ctx.filters(), is(Collections.<SCMHeadFilter>emptyList()));
        assumeThat(
                ctx.authorities(), not(hasItem(instanceOf(ForkPullRequestDiscoveryTrait.TrustExternalApproval.class))));
        ForkPullRequestDiscoveryTrait instance = new ForkPullRequestDiscoveryTrait(
                EnumSet.allOf(ChangeRequestCheckoutStrategy.class),
                new ForkPullRequestDiscoveryTrait.TrustExternalApproval());
        instance.decorateContext(ctx);
        assertThat(ctx.wantBranches(), is(false));
        assertThat(ctx.wantPRs(), is(true));
        assertThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assertThat(ctx.filters(), is(Collections.<SCMHeadFilter>emptyList()));
        assertThat(ctx.forkPRStrategies(), Matchers.is(EnumSet.allOf(ChangeRequestCheckoutStrategy.class)));
        assertThat(ctx.authorities(), hasItem(instanceOf(ForkPullRequestDiscoveryTrait.TrustExternalApproval.class)));
    }

    @Test
    public void given__externalApproval__when__checkTrusted__then__returnsFalse() throws Exception {
        ForkPullRequestDiscoveryTrait.TrustExternalApproval trust =
                new ForkPullRequestDiscoveryTrait.TrustExternalApproval();
        assertThat(trust.isRequireApprovalForNewCommits(), is(false));
        trust.setRequireApprovalForNewCommits(true);
        assertThat(trust.isRequireApprovalForNewCommits(), is(true));
    }

    @Test
    public void given__externalApproval__when__autoApprovalUsers__then__configuredCorrectly() throws Exception {
        ForkPullRequestDiscoveryTrait.TrustExternalApproval trust =
                new ForkPullRequestDiscoveryTrait.TrustExternalApproval();
        assertThat(trust.getAutoApprovalUsers(), org.hamcrest.Matchers.nullValue());

        trust.setAutoApprovalUsersList(java.util.Arrays.asList("user1", "user2"));
        assertThat(trust.getAutoApprovalUsers(), is(java.util.Arrays.asList("user1", "user2")));
        assertThat(trust.getAutoApprovalUsersString(), is("user1, user2"));

        trust.setAutoApprovalUsersList(java.util.Collections.emptyList());
        assertThat(trust.getAutoApprovalUsers(), org.hamcrest.Matchers.nullValue());

        trust.setAutoApprovalUsersList(null);
        assertThat(trust.getAutoApprovalUsers(), org.hamcrest.Matchers.nullValue());
    }

    @Test
    public void given__externalApproval__when__autoApprovalUsersString__then__parsedCorrectly() throws Exception {
        ForkPullRequestDiscoveryTrait.TrustExternalApproval trust =
                new ForkPullRequestDiscoveryTrait.TrustExternalApproval();

        trust.setAutoApprovalUsers("user1, user2, user3");
        assertThat(trust.getAutoApprovalUsers(), is(java.util.Arrays.asList("user1", "user2", "user3")));

        trust.setAutoApprovalUsers("  user1 ,  user2  ");
        assertThat(trust.getAutoApprovalUsers(), is(java.util.Arrays.asList("user1", "user2")));

        trust.setAutoApprovalUsers("");
        assertThat(trust.getAutoApprovalUsers(), org.hamcrest.Matchers.nullValue());

        trust.setAutoApprovalUsers((String) null);
        assertThat(trust.getAutoApprovalUsers(), org.hamcrest.Matchers.nullValue());
    }

    @Test
    public void given__externalApproval__when__autoApprovalLabels__then__configuredCorrectly() throws Exception {
        ForkPullRequestDiscoveryTrait.TrustExternalApproval trust =
                new ForkPullRequestDiscoveryTrait.TrustExternalApproval();
        assertThat(trust.getAutoApprovalLabels(), org.hamcrest.Matchers.nullValue());

        trust.setAutoApprovalLabelsList(java.util.Arrays.asList("safe-to-build", "approved"));
        assertThat(trust.getAutoApprovalLabels(), is(java.util.Arrays.asList("safe-to-build", "approved")));
        assertThat(trust.getAutoApprovalLabelsString(), is("safe-to-build, approved"));

        trust.setAutoApprovalLabelsList(java.util.Collections.emptyList());
        assertThat(trust.getAutoApprovalLabels(), org.hamcrest.Matchers.nullValue());

        trust.setAutoApprovalLabels("label1, label2");
        assertThat(trust.getAutoApprovalLabels(), is(java.util.Arrays.asList("label1", "label2")));

        trust.setAutoApprovalLabels((String) null);
        assertThat(trust.getAutoApprovalLabels(), org.hamcrest.Matchers.nullValue());
    }

    @Test
    public void xstreamExternalApproval() throws Exception {
        ForkPullRequestDiscoveryTrait.TrustExternalApproval trust =
                new ForkPullRequestDiscoveryTrait.TrustExternalApproval();
        trust.setRequireApprovalForNewCommits(true);
        trust.setAutoApprovalLabelsList(java.util.Arrays.asList("safe-to-build", "ci-approved"));
        trust.setAutoApprovalUsersList(java.util.Arrays.asList("octocat", "dependabot"));
        String xml = new XStream2().toXML(new ForkPullRequestDiscoveryTrait(3, trust));
        assertThat(xml, org.hamcrest.Matchers.containsString("TrustExternalApproval"));
        assertThat(xml, org.hamcrest.Matchers.containsString("requireApprovalForNewCommits"));
        assertThat(xml, org.hamcrest.Matchers.containsString("autoApprovalLabels"));
        assertThat(xml, org.hamcrest.Matchers.containsString("safe-to-build"));
        assertThat(xml, org.hamcrest.Matchers.containsString("ci-approved"));
        assertThat(xml, org.hamcrest.Matchers.containsString("autoApprovalUsers"));
        assertThat(xml, org.hamcrest.Matchers.containsString("octocat"));
        assertThat(xml, org.hamcrest.Matchers.containsString("dependabot"));
    }
}
