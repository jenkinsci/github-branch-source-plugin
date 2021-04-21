package org.jenkinsci.plugins.github_branch_source;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import hudson.model.TaskListener;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMHeadOrigin;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import jenkins.scm.api.trait.SCMHeadFilter;
import org.junit.Test;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHPullRequest;
import org.mockito.Mockito;

public class WildcardPullRequestLabelFilterTraitTest extends GitSCMSourceBase {

  public WildcardPullRequestLabelFilterTraitTest() {
    this.source = new GitHubSCMSource("cloudbeers", "yolo", null, false);
  }

  @Test
  public void testNoLabelsAndNoIncludesGivenNotExcluded() throws IOException, InterruptedException {
    GitHubSCMSourceContext probe = new GitHubSCMSourceContext(null, SCMHeadObserver.collect());
    WildcardPullRequestLabelFilterTrait instance =
        new WildcardPullRequestLabelFilterTrait(null, null);
    instance.decorateContext(probe);
    List<SCMHeadFilter> filters = probe.filters();
    assertThat(filters, hasSize(1));
    SCMHeadFilter filter = filters.get(0);
    SCMHead scmHead =
        new PullRequestSCMHead(
            "PR-5",
            "cloudbeers",
            "http://localhost:" + githubApi.port(),
            "feature/5",
            5,
            new BranchSCMHead("master"),
            SCMHeadOrigin.DEFAULT,
            ChangeRequestCheckoutStrategy.MERGE);
    GitHubSCMSourceRequest request =
        new GitHubSCMSourceRequest(source, probe, Mockito.mock(TaskListener.class));
    // Situation: Hitting the Github API for a PR and getting a PR with no labels
    GHPullRequest pullRequest = Mockito.mock(GHPullRequest.class);
    Mockito.when(pullRequest.getNumber()).thenReturn(5);
    Mockito.when(pullRequest.getLabels()).thenReturn(Collections.emptyList());
    request.setPullRequests(Collections.singleton(pullRequest));
    assertThat(filter.isExcluded(request, scmHead), equalTo(false));
  }

  @Test
  public void testNoLabelsAndWithIncludesGivenExcluded() throws IOException, InterruptedException {
    GitHubSCMSourceContext probe = new GitHubSCMSourceContext(null, SCMHeadObserver.collect());
    WildcardPullRequestLabelFilterTrait instance =
        new WildcardPullRequestLabelFilterTrait("include", null);
    instance.decorateContext(probe);
    List<SCMHeadFilter> filters = probe.filters();
    assertThat(filters, hasSize(1));
    SCMHeadFilter filter = filters.get(0);
    SCMHead scmHead =
        new PullRequestSCMHead(
            "PR-5",
            "cloudbeers",
            "http://localhost:" + githubApi.port(),
            "feature/5",
            5,
            new BranchSCMHead("master"),
            SCMHeadOrigin.DEFAULT,
            ChangeRequestCheckoutStrategy.MERGE);
    GitHubSCMSourceRequest request =
        new GitHubSCMSourceRequest(source, probe, Mockito.mock(TaskListener.class));
    // Situation: Hitting the Github API for a PR and getting a PR with no labels
    GHPullRequest pullRequest = Mockito.mock(GHPullRequest.class);
    Mockito.when(pullRequest.getNumber()).thenReturn(5);
    Mockito.when(pullRequest.getLabels()).thenReturn(Collections.emptyList());
    request.setPullRequests(Collections.singleton(pullRequest));
    assertThat(filter.isExcluded(request, scmHead), equalTo(true));
  }

  @Test
  public void testLabelsMatchesIncludesNotExcludesNotExcluded()
      throws IOException, InterruptedException {
    GitHubSCMSourceContext probe = new GitHubSCMSourceContext(null, SCMHeadObserver.collect());
    WildcardPullRequestLabelFilterTrait instance =
        new WildcardPullRequestLabelFilterTrait("pull-*", "no-match");
    instance.decorateContext(probe);
    List<SCMHeadFilter> filters = probe.filters();
    assertThat(filters, hasSize(1));
    SCMHeadFilter filter = filters.get(0);
    SCMHead scmHead =
        new PullRequestSCMHead(
            "PR-5",
            "cloudbeers",
            "http://localhost:" + githubApi.port(),
            "feature/5",
            5,
            new BranchSCMHead("master"),
            SCMHeadOrigin.DEFAULT,
            ChangeRequestCheckoutStrategy.MERGE);
    GitHubSCMSourceRequest request =
        new GitHubSCMSourceRequest(source, probe, Mockito.mock(TaskListener.class));
    // Situation: Hitting the Github API for a PR and getting a PR with a label that matches the includes only
    GHPullRequest pullRequest = Mockito.mock(GHPullRequest.class);
    Mockito.when(pullRequest.getNumber()).thenReturn(5);
    GHLabel label = Mockito.mock(GHLabel.class);
    Mockito.when(label.getName()).thenReturn("pull-request-label");
    Mockito.when(pullRequest.getLabels()).thenReturn(Collections.singletonList(label));
    request.setPullRequests(Collections.singleton(pullRequest));
    assertThat(filter.isExcluded(request, scmHead), equalTo(false));
  }

  @Test
  public void testLabelsMatchesIncludesAndExcludesExcluded()
      throws IOException, InterruptedException {
    GitHubSCMSourceContext probe = new GitHubSCMSourceContext(null, SCMHeadObserver.collect());
    WildcardPullRequestLabelFilterTrait instance =
        new WildcardPullRequestLabelFilterTrait("pull-*", "*-label");
    instance.decorateContext(probe);
    List<SCMHeadFilter> filters = probe.filters();
    assertThat(filters, hasSize(1));
    SCMHeadFilter filter = filters.get(0);
    SCMHead scmHead =
        new PullRequestSCMHead(
            "PR-5",
            "cloudbeers",
            "http://localhost:" + githubApi.port(),
            "feature/5",
            5,
            new BranchSCMHead("master"),
            SCMHeadOrigin.DEFAULT,
            ChangeRequestCheckoutStrategy.MERGE);
    GitHubSCMSourceRequest request =
        new GitHubSCMSourceRequest(source, probe, Mockito.mock(TaskListener.class));
    // Situation: Hitting the Github API for a PR and getting a PR with a label that matches the includes and excludes
    GHPullRequest pullRequest = Mockito.mock(GHPullRequest.class);
    Mockito.when(pullRequest.getNumber()).thenReturn(5);
    GHLabel label = Mockito.mock(GHLabel.class);
    Mockito.when(label.getName()).thenReturn("pull-request-label");
    Mockito.when(pullRequest.getLabels()).thenReturn(Collections.singletonList(label));
    request.setPullRequests(Collections.singleton(pullRequest));
    assertThat(filter.isExcluded(request, scmHead), equalTo(true));
  }

  @Test
  public void testLabelsMatchesExcludesNotIncludesExcluded()
      throws IOException, InterruptedException {
    GitHubSCMSourceContext probe = new GitHubSCMSourceContext(null, SCMHeadObserver.collect());
    WildcardPullRequestLabelFilterTrait instance =
        new WildcardPullRequestLabelFilterTrait("no-match", "*-label");
    instance.decorateContext(probe);
    List<SCMHeadFilter> filters = probe.filters();
    assertThat(filters, hasSize(1));
    SCMHeadFilter filter = filters.get(0);
    SCMHead scmHead =
        new PullRequestSCMHead(
            "PR-5",
            "cloudbeers",
            "http://localhost:" + githubApi.port(),
            "feature/5",
            5,
            new BranchSCMHead("master"),
            SCMHeadOrigin.DEFAULT,
            ChangeRequestCheckoutStrategy.MERGE);
    GitHubSCMSourceRequest request =
        new GitHubSCMSourceRequest(source, probe, Mockito.mock(TaskListener.class));
    // Situation: Hitting the Github API for a PR and getting a PR with a label that matches the excludes only
    GHPullRequest pullRequest = Mockito.mock(GHPullRequest.class);
    Mockito.when(pullRequest.getNumber()).thenReturn(5);
    GHLabel label = Mockito.mock(GHLabel.class);
    Mockito.when(label.getName()).thenReturn("pull-request-label");
    Mockito.when(pullRequest.getLabels()).thenReturn(Collections.singletonList(label));
    request.setPullRequests(Collections.singleton(pullRequest));
    assertThat(filter.isExcluded(request, scmHead), equalTo(true));
  }
}
