package org.jenkinsci.plugins.github_branch_source;

import hudson.util.XStream2;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMHeadOrigin;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import jenkins.scm.api.trait.SCMHeadFilter;
import jenkins.scm.api.trait.SCMHeadPrefilter;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;

@RunWith(PowerMockRunner.class)
@PrepareForTest(GitHubSCMSourceRequest.class)
public class ForkPullRequestDiscoveryTraitTest {

    static final Set<String> WHITELISTED_USERS = new HashSet<String>(Arrays.asList("user1", "user2"));

    static final Set<String> COLLABORATORS = new HashSet<String>(Arrays.asList("user1", "user2"));

    static final String UNTRUSTED_USER = "user4";

    @Test
    public void xstream() throws Exception  {
        System.out.println(new XStream2().toXML(new ForkPullRequestDiscoveryTrait(3, new ForkPullRequestDiscoveryTrait.TrustContributors())));
    }

    @Test
    public void given__disoverHeadMerge__when__appliedToContext__then__strategiesCorrect() throws Exception {
        GitHubSCMSourceContext ctx = new GitHubSCMSourceContext(null, SCMHeadObserver.none());
        assumeThat(ctx.wantBranches(), is(false));
        assumeThat(ctx.wantPRs(), is(false));
        assumeThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assumeThat(ctx.filters(), is(Collections.<SCMHeadFilter>emptyList()));
        assumeThat(ctx.authorities(), not((Matcher) hasItem(
                instanceOf(ForkPullRequestDiscoveryTrait.TrustContributors.class)
        )));
        ForkPullRequestDiscoveryTrait instance = new ForkPullRequestDiscoveryTrait(
                EnumSet.allOf(ChangeRequestCheckoutStrategy.class),
                new ForkPullRequestDiscoveryTrait.TrustContributors()
        );
        instance.decorateContext(ctx);
        assertThat(ctx.wantBranches(), is(false));
        assertThat(ctx.wantPRs(), is(true));
        assertThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assertThat(ctx.filters(), is(Collections.<SCMHeadFilter>emptyList()));
        assertThat(ctx.forkPRStrategies(),
                Matchers.<Set<ChangeRequestCheckoutStrategy>>is(EnumSet.allOf(ChangeRequestCheckoutStrategy.class)));
        assertThat(ctx.authorities(), (Matcher) hasItem(
                instanceOf(ForkPullRequestDiscoveryTrait.TrustContributors.class)
        ));
    }

    @Test
    public void given__disoverHeadOnly__when__appliedToContext__then__strategiesCorrect() throws Exception {
        GitHubSCMSourceContext ctx = new GitHubSCMSourceContext(null, SCMHeadObserver.none());
        assumeThat(ctx.wantBranches(), is(false));
        assumeThat(ctx.wantPRs(), is(false));
        assumeThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assumeThat(ctx.filters(), is(Collections.<SCMHeadFilter>emptyList()));
        assumeThat(ctx.authorities(), not((Matcher) hasItem(
                instanceOf(ForkPullRequestDiscoveryTrait.TrustContributors.class)
        )));
        ForkPullRequestDiscoveryTrait instance = new ForkPullRequestDiscoveryTrait(
                EnumSet.of(ChangeRequestCheckoutStrategy.HEAD),
                new ForkPullRequestDiscoveryTrait.TrustContributors()
        );
        instance.decorateContext(ctx);
        assertThat(ctx.wantBranches(), is(false));
        assertThat(ctx.wantPRs(), is(true));
        assertThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assertThat(ctx.filters(), is(Collections.<SCMHeadFilter>emptyList()));
        assertThat(ctx.forkPRStrategies(),
                Matchers.<Set<ChangeRequestCheckoutStrategy>>is(EnumSet.of(ChangeRequestCheckoutStrategy.HEAD)));
        assertThat(ctx.authorities(), (Matcher) hasItem(
                instanceOf(ForkPullRequestDiscoveryTrait.TrustContributors.class)
        ));
    }

    @Test
    public void given__disoverMergeOnly__when__appliedToContext__then__strategiesCorrect() throws Exception {
        GitHubSCMSourceContext ctx = new GitHubSCMSourceContext(null, SCMHeadObserver.none());
        assumeThat(ctx.wantBranches(), is(false));
        assumeThat(ctx.wantPRs(), is(false));
        assumeThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assumeThat(ctx.filters(), is(Collections.<SCMHeadFilter>emptyList()));
        assumeThat(ctx.authorities(), not((Matcher) hasItem(
                instanceOf(ForkPullRequestDiscoveryTrait.TrustContributors.class)
        )));
        ForkPullRequestDiscoveryTrait instance = new ForkPullRequestDiscoveryTrait(
                EnumSet.of(ChangeRequestCheckoutStrategy.MERGE),
                new ForkPullRequestDiscoveryTrait.TrustContributors()
        );
        instance.decorateContext(ctx);
        assertThat(ctx.wantBranches(), is(false));
        assertThat(ctx.wantPRs(), is(true));
        assertThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assertThat(ctx.filters(), is(Collections.<SCMHeadFilter>emptyList()));
        assertThat(ctx.forkPRStrategies(),
                Matchers.<Set<ChangeRequestCheckoutStrategy>>is(EnumSet.of(ChangeRequestCheckoutStrategy.MERGE)));
        assertThat(ctx.authorities(), (Matcher) hasItem(
                instanceOf(ForkPullRequestDiscoveryTrait.TrustContributors.class)
        ));
    }

    @Test
    public void given__nonDefaultTrust__when__appliedToContext__then__authoritiesCorrect() throws Exception {
        GitHubSCMSourceContext ctx = new GitHubSCMSourceContext(null, SCMHeadObserver.none());
        assumeThat(ctx.wantBranches(), is(false));
        assumeThat(ctx.wantPRs(), is(false));
        assumeThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assumeThat(ctx.filters(), is(Collections.<SCMHeadFilter>emptyList()));
        assumeThat(ctx.authorities(), not((Matcher) hasItem(
                instanceOf(ForkPullRequestDiscoveryTrait.TrustContributors.class)
        )));
        ForkPullRequestDiscoveryTrait instance = new ForkPullRequestDiscoveryTrait(
                EnumSet.allOf(ChangeRequestCheckoutStrategy.class),
                new ForkPullRequestDiscoveryTrait.TrustEveryone()
        );
        instance.decorateContext(ctx);
        assertThat(ctx.wantBranches(), is(false));
        assertThat(ctx.wantPRs(), is(true));
        assertThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assertThat(ctx.filters(), is(Collections.<SCMHeadFilter>emptyList()));
        assertThat(ctx.forkPRStrategies(),
                Matchers.<Set<ChangeRequestCheckoutStrategy>>is(EnumSet.allOf(ChangeRequestCheckoutStrategy.class)));
        assertThat(ctx.authorities(), (Matcher) hasItem(
                instanceOf(ForkPullRequestDiscoveryTrait.TrustEveryone.class)
        ));
    }

    @Test
    public void given__whitelistTrust__when__appliedToContext__then__authoritiesCorrect() throws Exception {
        GitHubSCMSourceContext ctx = new GitHubSCMSourceContext(null, SCMHeadObserver.none());
        assumeThat(ctx.wantBranches(), is(false));
        assumeThat(ctx.wantPRs(), is(false));
        assumeThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assumeThat(ctx.filters(), is(Collections.<SCMHeadFilter>emptyList()));
        assumeThat(ctx.authorities(), not((Matcher) hasItem(
                instanceOf(ForkPullRequestDiscoveryTrait.TrustWhitelist.class)
        )));

        WhitelistSource whitelist = mock(WhitelistGlobalConfiguration.class);
        when(whitelist.getUserIds()).thenReturn(WHITELISTED_USERS);
        when(whitelist.contains(any(String.class))).thenReturn(false);

        ForkPullRequestDiscoveryTrait instance = new ForkPullRequestDiscoveryTrait(
                EnumSet.allOf(ChangeRequestCheckoutStrategy.class),
                new ForkPullRequestDiscoveryTrait.TrustWhitelist(whitelist)
        );
        instance.decorateContext(ctx);
        assertThat(ctx.wantBranches(), is(false));
        assertThat(ctx.wantPRs(), is(true));
        assertThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assertThat(ctx.filters(), is(Collections.<SCMHeadFilter>emptyList()));
        assertThat(ctx.forkPRStrategies(),
                Matchers.<Set<ChangeRequestCheckoutStrategy>>is(EnumSet.allOf(ChangeRequestCheckoutStrategy.class)));
        assertThat(ctx.authorities(), (Matcher) hasItem(
                instanceOf(ForkPullRequestDiscoveryTrait.TrustWhitelist.class)
        ));
    }

    @Test
    public void given__contributorsAndWhitelistTrust__when__appliedToContext__then__authoritiesCorrect() throws Exception {
        GitHubSCMSourceContext ctx = new GitHubSCMSourceContext(null, SCMHeadObserver.none());
        assumeThat(ctx.wantBranches(), is(false));
        assumeThat(ctx.wantPRs(), is(false));
        assumeThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assumeThat(ctx.filters(), is(Collections.<SCMHeadFilter>emptyList()));
        assumeThat(ctx.authorities(), not((Matcher) hasItem(
                instanceOf(ForkPullRequestDiscoveryTrait.TrustContributorsAndWhitelist.class)
        )));

        WhitelistSource whitelist = mock(WhitelistGlobalConfiguration.class);
        when(whitelist.getUserIds()).thenReturn(WHITELISTED_USERS);
        when(whitelist.contains(any(String.class))).thenReturn(false);

        ForkPullRequestDiscoveryTrait instance = new ForkPullRequestDiscoveryTrait(
                EnumSet.allOf(ChangeRequestCheckoutStrategy.class),
                new ForkPullRequestDiscoveryTrait.TrustContributorsAndWhitelist(whitelist)
        );
        instance.decorateContext(ctx);
        assertThat(ctx.wantBranches(), is(false));
        assertThat(ctx.wantPRs(), is(true));
        assertThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assertThat(ctx.filters(), is(Collections.<SCMHeadFilter>emptyList()));
        assertThat(ctx.forkPRStrategies(),
                Matchers.<Set<ChangeRequestCheckoutStrategy>>is(EnumSet.allOf(ChangeRequestCheckoutStrategy.class)));
        assertThat(ctx.authorities(), (Matcher) hasItem(
                instanceOf(ForkPullRequestDiscoveryTrait.TrustContributorsAndWhitelist.class)
        ));
    }

    @Test
    public void given__whitelistTrust__when__prFromWhitelistedAuthor__then__prIsTrusted() throws Exception {
        GitHubSCMSourceRequest request = mock(GitHubSCMSourceRequest.class);
        PullRequestSCMHead head = mock(PullRequestSCMHead.class);
        SCMHeadOrigin origin = mock(SCMHeadOrigin.class);
        when(head.getOrigin()).thenReturn(origin);
        when(head.getSourceOwner()).thenReturn(WHITELISTED_USERS.iterator().next());

        WhitelistSource whitelist = mock(WhitelistGlobalConfiguration.class);
        when(whitelist.getUserIds()).thenReturn(WHITELISTED_USERS);
        when(whitelist.contains(any(String.class))).thenReturn(false);
        when(whitelist.contains(WHITELISTED_USERS.iterator().next())).thenReturn(true);

        ForkPullRequestDiscoveryTrait.TrustWhitelist authority = new ForkPullRequestDiscoveryTrait.TrustWhitelist(whitelist);
        assertThat(authority.checkTrusted(request, head), is(true));
    }

    @Test
    public void given__whitelistTrust__when__prFromNonWhitelistedAuthor__then__prIsNotTrusted() throws Exception {
        GitHubSCMSourceRequest request = mock(GitHubSCMSourceRequest.class);
        PullRequestSCMHead head = mock(PullRequestSCMHead.class);
        SCMHeadOrigin origin = mock(SCMHeadOrigin.class);
        when(head.getOrigin()).thenReturn(origin);
        when(head.getSourceOwner()).thenReturn(UNTRUSTED_USER);

        WhitelistSource whitelist = mock(WhitelistGlobalConfiguration.class);
        when(whitelist.getUserIds()).thenReturn(WHITELISTED_USERS);
        when(whitelist.contains(any(String.class))).thenReturn(false);
        when(whitelist.contains(WHITELISTED_USERS.iterator().next())).thenReturn(true);

        ForkPullRequestDiscoveryTrait.TrustWhitelist authority = new ForkPullRequestDiscoveryTrait.TrustWhitelist(whitelist);
        assertThat(authority.checkTrusted(request, head), is(false));
    }

    @Test
    public void given__contributorsAndWhitelistTrust__when__prFromWhitelistedAuthor__then__prIsTrusted() throws Exception {
        GitHubSCMSourceRequest request = mock(GitHubSCMSourceRequest.class);
        when(request.getCollaboratorNames()).thenReturn(COLLABORATORS);
        PullRequestSCMHead head = mock(PullRequestSCMHead.class);
        SCMHeadOrigin origin = mock(SCMHeadOrigin.class);
        when(head.getOrigin()).thenReturn(origin);
        when(head.getSourceOwner()).thenReturn(WHITELISTED_USERS.iterator().next());

        WhitelistSource whitelist = mock(WhitelistGlobalConfiguration.class);
        when(whitelist.getUserIds()).thenReturn(WHITELISTED_USERS);
        when(whitelist.contains(any(String.class))).thenReturn(false);
        when(whitelist.contains(WHITELISTED_USERS.iterator().next())).thenReturn(true);

        ForkPullRequestDiscoveryTrait.TrustContributorsAndWhitelist authority = new ForkPullRequestDiscoveryTrait.TrustContributorsAndWhitelist(whitelist);
        assertThat(authority.checkTrusted(request, head), is(true));
    }

    @Test
    public void given__contributorsAndWhitelistTrust__when__prFromCollaborator__then__prIsTrusted() throws Exception {
        GitHubSCMSourceRequest request = mock(GitHubSCMSourceRequest.class);
        when(request.getCollaboratorNames()).thenReturn(COLLABORATORS);
        PullRequestSCMHead head = mock(PullRequestSCMHead.class);
        SCMHeadOrigin origin = mock(SCMHeadOrigin.class);
        when(head.getOrigin()).thenReturn(origin);
        when(head.getSourceOwner()).thenReturn(COLLABORATORS.iterator().next());

        WhitelistSource whitelist = mock(WhitelistGlobalConfiguration.class);
        when(whitelist.getUserIds()).thenReturn(WHITELISTED_USERS);
        when(whitelist.contains(any(String.class))).thenReturn(false);
        when(whitelist.contains(WHITELISTED_USERS.iterator().next())).thenReturn(true);

        ForkPullRequestDiscoveryTrait.TrustContributorsAndWhitelist authority = new ForkPullRequestDiscoveryTrait.TrustContributorsAndWhitelist(whitelist);
        assertThat(authority.checkTrusted(request, head), is(true));
    }

    @Test
    public void given__contributorsAndWhitelistTrust__when__prFromUntrustedAuthor__then__prIsNotTrusted() throws Exception {
        GitHubSCMSourceRequest request = mock(GitHubSCMSourceRequest.class);
        when(request.getCollaboratorNames()).thenReturn(COLLABORATORS);
        PullRequestSCMHead head = mock(PullRequestSCMHead.class);
        SCMHeadOrigin origin = mock(SCMHeadOrigin.class);
        when(head.getOrigin()).thenReturn(origin);
        when(head.getSourceOwner()).thenReturn(UNTRUSTED_USER);

        WhitelistSource whitelist = mock(WhitelistGlobalConfiguration.class);
        when(whitelist.getUserIds()).thenReturn(WHITELISTED_USERS);
        when(whitelist.contains(any(String.class))).thenReturn(false);
        when(whitelist.contains(WHITELISTED_USERS.iterator().next())).thenReturn(true);

        ForkPullRequestDiscoveryTrait.TrustContributorsAndWhitelist authority = new ForkPullRequestDiscoveryTrait.TrustContributorsAndWhitelist(whitelist);
        assertThat(authority.checkTrusted(request, head), is(false));
    }

}
