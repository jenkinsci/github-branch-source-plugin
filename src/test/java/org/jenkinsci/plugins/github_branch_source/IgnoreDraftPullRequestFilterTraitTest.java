package org.jenkinsci.plugins.github_branch_source;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMHeadOrigin;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import jenkins.scm.api.trait.SCMHeadFilter;
import org.junit.Test;
import org.kohsuke.github.GHPullRequest;
import org.mockito.Mockito;

public class IgnoreDraftPullRequestFilterTraitTest extends GitSCMSourceBase {

    public IgnoreDraftPullRequestFilterTraitTest() {
        this.source = new GitHubSCMSource("cloudbeers", "yolo", null, false);
    }

    @Test
    public void testTraitFiltersDraft() throws IOException, InterruptedException {
        GitHubSCMSourceContext probe = new GitHubSCMSourceContext(null, SCMHeadObserver.collect());
        IgnoreDraftPullRequestFilterTrait instance = new IgnoreDraftPullRequestFilterTrait();
        instance.decorateContext(probe);
        List<SCMHeadFilter> filters = probe.filters();
        assertThat(filters, hasSize(1));
        SCMHeadFilter filter = filters.get(0);
        SCMHead scmHead = new PullRequestSCMHead(
                "PR-5",
                "cloudbeers",
                "http://localhost:" + githubApi.port(),
                "feature/5",
                5,
                new BranchSCMHead("master"),
                SCMHeadOrigin.DEFAULT,
                ChangeRequestCheckoutStrategy.MERGE);
        GitHubSCMSourceRequest request = new GitHubSCMSourceRequest(source, probe, null);
        // Situation: Hitting the Github API for a PR and getting a PR that is a draft
        GHPullRequest pullRequest = Mockito.mock(GHPullRequest.class);
        Mockito.when(pullRequest.getNumber()).thenReturn(5);
        Mockito.when(pullRequest.isDraft()).thenReturn(true);
        request.setPullRequests(Collections.singleton(pullRequest));
        assertThat(filter.isExcluded(request, scmHead), equalTo(true));
    }

    @Test
    public void testTraitDoesNotFilterNonDraft() throws IOException, InterruptedException {
        GitHubSCMSourceContext probe = new GitHubSCMSourceContext(null, SCMHeadObserver.collect());
        IgnoreDraftPullRequestFilterTrait instance = new IgnoreDraftPullRequestFilterTrait();
        instance.decorateContext(probe);
        List<SCMHeadFilter> filters = probe.filters();
        assertThat(filters, hasSize(1));
        SCMHeadFilter filter = filters.get(0);
        SCMHead scmHead = new PullRequestSCMHead(
                "PR-5",
                "cloudbeers",
                "http://localhost:" + githubApi.port(),
                "feature/5",
                5,
                new BranchSCMHead("master"),
                SCMHeadOrigin.DEFAULT,
                ChangeRequestCheckoutStrategy.MERGE);
        GitHubSCMSourceRequest request = new GitHubSCMSourceRequest(source, probe, null);
        // Situation: Hitting the Github API for a PR and getting a PR that is not a draft
        GHPullRequest pullRequest = Mockito.mock(GHPullRequest.class);
        Mockito.when(pullRequest.getNumber()).thenReturn(5);
        Mockito.when(pullRequest.isDraft()).thenReturn(false);
        request.setPullRequests(Collections.singleton(pullRequest));
        assertThat(filter.isExcluded(request, scmHead), equalTo(false));
    }
}
