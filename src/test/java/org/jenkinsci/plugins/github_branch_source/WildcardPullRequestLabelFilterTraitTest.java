package org.jenkinsci.plugins.github_branch_source;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.*;

import java.util.List;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMHeadOrigin;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import jenkins.scm.api.trait.SCMHeadPrefilter;
import org.junit.Test;

public class WildcardPullRequestLabelFilterTraitTest extends GitSCMSourceBase {

  public WildcardPullRequestLabelFilterTraitTest() {
    this.source = new GitHubSCMSource("cloudbeers", "yolo", null, false);
  }

  @Test
  public void testNoLabelsAndNoIncludesGivenNotExcluded() {
    // Situation: Hitting the Github API for a PR and getting a PR with no labels
    githubApi.stubFor(
        get(urlEqualTo("/repos/cloudbeers/yolo/pulls/5"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withBodyFile("body-yolo-pulls-5-no-labels.json")));
    GitHubSCMSourceContext probe = new GitHubSCMSourceContext(null, SCMHeadObserver.collect());
    WildcardPullRequestLabelFilterTrait instance =
        new WildcardPullRequestLabelFilterTrait(null, null);
    instance.decorateContext(probe);
    List<SCMHeadPrefilter> prefilters = probe.prefilters();
    assertThat(prefilters, hasSize(1));
    SCMHeadPrefilter prefilter = prefilters.get(0);
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
    assertThat(prefilter.isExcluded(source, scmHead), equalTo(false));
  }

  @Test
  public void testNoLabelsAndWithIncludesGivenExcluded() {
    // Situation: Hitting the Github API for a PR and getting a PR with no labels
    githubApi.stubFor(
        get(urlEqualTo("/repos/cloudbeers/yolo/pulls/5"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withBodyFile("body-yolo-pulls-5-no-labels.json")));
    GitHubSCMSourceContext probe = new GitHubSCMSourceContext(null, SCMHeadObserver.collect());
    WildcardPullRequestLabelFilterTrait instance =
        new WildcardPullRequestLabelFilterTrait("include", null);
    instance.decorateContext(probe);
    List<SCMHeadPrefilter> prefilters = probe.prefilters();
    assertThat(prefilters, hasSize(1));
    SCMHeadPrefilter prefilter = prefilters.get(0);
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
    assertThat(prefilter.isExcluded(source, scmHead), equalTo(true));
  }

  @Test
  public void testLabelsMatchesIncludesNotExcludesNotExcluded() {
    // Situation: Hitting the Github API for a PR and getting a PR with labels [include-me,
    // exclude-me]
    githubApi.stubFor(
        get(urlEqualTo("/repos/cloudbeers/yolo/pulls/5"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withBodyFile("body-yolo-pulls-5-with-labels.json")));
    GitHubSCMSourceContext probe = new GitHubSCMSourceContext(null, SCMHeadObserver.collect());
    WildcardPullRequestLabelFilterTrait instance =
        new WildcardPullRequestLabelFilterTrait("include*", "no-match");
    instance.decorateContext(probe);
    List<SCMHeadPrefilter> prefilters = probe.prefilters();
    assertThat(prefilters, hasSize(1));
    SCMHeadPrefilter prefilter = prefilters.get(0);
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
    assertThat(prefilter.isExcluded(source, scmHead), equalTo(false));
  }

  @Test
  public void testLabelsMatchesIncludesAndExcludesExcluded() {
    // Situation: Hitting the Github API for a PR and getting a PR with labels [include-me,
    // exclude-me]
    githubApi.stubFor(
        get(urlEqualTo("/repos/cloudbeers/yolo/pulls/5"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withBodyFile("body-yolo-pulls-5-with-labels.json")));
    GitHubSCMSourceContext probe = new GitHubSCMSourceContext(null, SCMHeadObserver.collect());
    WildcardPullRequestLabelFilterTrait instance =
        new WildcardPullRequestLabelFilterTrait("include*", "exclude*");
    instance.decorateContext(probe);
    List<SCMHeadPrefilter> prefilters = probe.prefilters();
    assertThat(prefilters, hasSize(1));
    SCMHeadPrefilter prefilter = prefilters.get(0);
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
    assertThat(prefilter.isExcluded(source, scmHead), equalTo(true));
  }

  @Test
  public void testLabelsMatchesExcludesNotIncludesExcluded() {
    // Situation: Hitting the Github API for a PR and getting a PR with labels [include-me,
    // exclude-me]
    githubApi.stubFor(
        get(urlEqualTo("/repos/cloudbeers/yolo/pulls/5"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withBodyFile("body-yolo-pulls-5-with-labels.json")));
    GitHubSCMSourceContext probe = new GitHubSCMSourceContext(null, SCMHeadObserver.collect());
    WildcardPullRequestLabelFilterTrait instance =
        new WildcardPullRequestLabelFilterTrait("no-match", "exclude*");
    instance.decorateContext(probe);
    List<SCMHeadPrefilter> prefilters = probe.prefilters();
    assertThat(prefilters, hasSize(1));
    SCMHeadPrefilter prefilter = prefilters.get(0);
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
    assertThat(prefilter.isExcluded(source, scmHead), equalTo(true));
  }
}
