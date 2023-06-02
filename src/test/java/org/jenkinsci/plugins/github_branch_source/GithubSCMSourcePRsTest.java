package org.jenkinsci.plugins.github_branch_source;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.*;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMHeadOrigin;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import org.junit.Test;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.mockito.Mockito;

public class GithubSCMSourcePRsTest extends GitSCMSourceBase {

    public GithubSCMSourcePRsTest() {
        this.source = new GitHubSCMSource("cloudbeers", "yolo", null, false);
    }

    @Test
    public void testClosedSinglePR() throws IOException {
        // Situation: Hitting the Github API for a PR and getting a closed PR
        githubApi.stubFor(get(urlEqualTo("/repos/cloudbeers/yolo/pulls/1"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBodyFile("../PRs/_files/body-yolo-pulls-closed-pr.json")));
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);
        Mockito.when(mockSCMHeadObserver.getIncludes())
                .thenReturn(Collections.singleton(new PullRequestSCMHead(
                        "PR-1",
                        "*",
                        "http://localhost:" + githubApi.port(),
                        "master",
                        1,
                        new BranchSCMHead("master"),
                        SCMHeadOrigin.DEFAULT,
                        ChangeRequestCheckoutStrategy.MERGE)));
        GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, mockSCMHeadObserver);
        context.wantPRs();
        GitHubSCMSourceRequest request =
                context.newRequest(new GitHubSCMSource("cloudbeers", "yolo", null, false), null);
        Iterator<GHPullRequest> pullRequest =
                new GitHubSCMSource("cloudbeers", "yolo", null, false).new LazyPullRequests(request, repo).iterator();
        // Expected: In the iterator will be empty
        assertFalse(pullRequest.hasNext());
    }

    // Single PR that is open: returns singleton
    @Test
    public void testOpenSinglePR() throws IOException {
        // Situation: Hitting the Github API for a PR and getting a open PR
        githubApi.stubFor(get(urlEqualTo("/repos/cloudbeers/yolo/pulls/1"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBodyFile("../PRs/_files/body-yolo-pulls-open-pr.json")));
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);
        Mockito.when(mockSCMHeadObserver.getIncludes())
                .thenReturn(Collections.singleton(new PullRequestSCMHead(
                        "PR-1",
                        "ataylor",
                        "http://localhost:" + githubApi.port(),
                        "master",
                        1,
                        new BranchSCMHead("master"),
                        SCMHeadOrigin.DEFAULT,
                        ChangeRequestCheckoutStrategy.MERGE)));
        GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, mockSCMHeadObserver);
        context.wantPRs();
        GitHubSCMSourceRequest request =
                context.newRequest(new GitHubSCMSource("cloudbeers", "yolo", null, false), null);
        Iterator<GHPullRequest> pullRequest =
                new GitHubSCMSource("cloudbeers", "yolo", null, false).new LazyPullRequests(request, repo).iterator();
        // Expected: In the iterator will have one item in it
        assertTrue(pullRequest.hasNext());
        assertEquals(1, pullRequest.next().getId());

        assertFalse(pullRequest.hasNext());
    }

    @Test
    public void testSinglePRThrowingExceptionOnGettingNumbers() throws Exception {
        // Situation: Hitting the Github API for a PR and an IO exception during the building of the
        // iterator
        githubApi.stubFor(get(urlEqualTo("/repos/cloudbeers/yolo/pulls/1"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBodyFile("../PRs/_files/body-yolo-pulls-open-pr.json")));
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);
        Mockito.when(mockSCMHeadObserver.getIncludes())
                .thenReturn(Collections.singleton(new PullRequestSCMHead(
                        "PR-1",
                        "ataylor",
                        "http://localhost:" + githubApi.port(),
                        "master",
                        1,
                        new BranchSCMHead("master"),
                        SCMHeadOrigin.DEFAULT,
                        ChangeRequestCheckoutStrategy.MERGE)));
        GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, mockSCMHeadObserver);
        context.wantPRs();
        GitHubSCMSourceRequest request =
                context.newRequest(new GitHubSCMSource("cloudbeers", "yolo", null, false), null);

        GHRepository mockRequest = Mockito.spy(repo);
        Mockito.when(mockRequest.getPullRequest(1)).thenThrow(new IOException("Number does not exist"));

        // Expected: This will fail when trying to generate the iterator
        try {
            Iterator<GHPullRequest> pullRequest = new GitHubSCMSource("cloudbeers", "yolo", null, false)
                    .new LazyPullRequests(request, mockRequest)
                    .iterator();
            fail();
        } catch (Exception e) {
            assertEquals("java.io.IOException: Number does not exist", e.getMessage());
        }
    }

    @Test
    public void testOpenSinglePRThrowsFileNotFoundOnObserve() throws Exception {
        // Situation: Hitting the Github API for a PR and an FileNotFound exception during the
        // getPullRequest
        githubApi.stubFor(get(urlEqualTo("/repos/cloudbeers/yolo/pulls/1"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBodyFile("../PRs/_files/body-yolo-pulls-open-pr.json")));
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);
        Mockito.when(mockSCMHeadObserver.getIncludes())
                .thenReturn(Collections.singleton(new PullRequestSCMHead(
                        "PR-1",
                        "ataylor",
                        "http://localhost:" + githubApi.port(),
                        "master",
                        1,
                        new BranchSCMHead("master"),
                        SCMHeadOrigin.DEFAULT,
                        ChangeRequestCheckoutStrategy.MERGE)));
        GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, mockSCMHeadObserver);
        context.wantPRs();

        // Spy on repo
        GHRepository repoSpy = Mockito.spy(repo);
        GHPullRequest pullRequestSpy = Mockito.spy(repoSpy.getPullRequest(1));
        Mockito.when(repoSpy.getPullRequest(1)).thenReturn(pullRequestSpy);
        // then throw on the PR during observe
        Mockito.when(pullRequestSpy.getUser()).thenThrow(new FileNotFoundException("User not found"));
        GitHubSCMSourceRequest request =
                context.newRequest(new GitHubSCMSource("cloudbeers", "yolo", null, false), null);
        Iterator<GHPullRequest> pullRequestIterator = new GitHubSCMSource("cloudbeers", "yolo", null, false)
                .new LazyPullRequests(request, repoSpy)
                .iterator();

        // Expected: In the iterator will have one item in it but when getting that item you receive an
        // FileNotFound exception
        assertTrue(pullRequestIterator.hasNext());
        try {
            pullRequestIterator.next();
            fail();
        } catch (Exception e) {
            assertEquals("java.io.FileNotFoundException: User not found", e.getMessage());
        }
    }

    @Test
    public void testOpenSinglePRThrowsIOOnObserve() throws Exception {
        // Situation: Hitting the Github API for a PR and an IO exception during the getPullRequest
        githubApi.stubFor(get(urlEqualTo("/repos/cloudbeers/yolo/pulls/1"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBodyFile("../PRs/_files/body-yolo-pulls-open-pr.json")));
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);
        Mockito.when(mockSCMHeadObserver.getIncludes())
                .thenReturn(Collections.singleton(new PullRequestSCMHead(
                        "PR-1",
                        "ataylor",
                        "http://localhost:" + githubApi.port(),
                        "master",
                        1,
                        new BranchSCMHead("master"),
                        SCMHeadOrigin.DEFAULT,
                        ChangeRequestCheckoutStrategy.MERGE)));
        GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, mockSCMHeadObserver);
        context.wantPRs();

        // Spy on repo
        GHRepository repoSpy = Mockito.spy(repo);
        GHPullRequest pullRequestSpy = Mockito.spy(repoSpy.getPullRequest(1));
        Mockito.when(repoSpy.getPullRequest(1)).thenReturn(pullRequestSpy);
        // then throw on the PR during observe
        Mockito.when(pullRequestSpy.getUser()).thenThrow(new IOException("Failed to get user"));
        GitHubSCMSourceRequest request =
                context.newRequest(new GitHubSCMSource("cloudbeers", "yolo", null, false), null);
        Iterator<GHPullRequest> pullRequestIterator = new GitHubSCMSource("cloudbeers", "yolo", null, false)
                .new LazyPullRequests(request, repoSpy)
                .iterator();

        // Expected: In the iterator will have one item in it but when getting that item you receive an
        // IO exception
        assertTrue(pullRequestIterator.hasNext());
        try {
            pullRequestIterator.next();
            fail();
        } catch (Exception e) {
            assertEquals("java.io.IOException: Failed to get user", e.getMessage());
        }
    }

    // Multiple PRs
    @Test
    public void testOpenMultiplePRs() throws IOException {
        // Situation: Hitting the Github API all the PRs and they are all Open. Then we close the
        // request at the end
        githubApi.stubFor(get(urlEqualTo("/repos/cloudbeers/yolo/pulls?state=open"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBodyFile("../PRs/_files/body-yolo-pulls-open-multiple-PRs.json")));
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);
        GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, mockSCMHeadObserver);
        context.wantOriginPRs(true);
        GitHubSCMSourceRequest request =
                context.newRequest(new GitHubSCMSource("cloudbeers", "yolo", null, false), null);
        Iterator<GHPullRequest> pullRequest =
                new GitHubSCMSource("cloudbeers", "yolo", null, false).new LazyPullRequests(request, repo).iterator();
        // Expected: In the iterator will have 2 items in it
        assertTrue(pullRequest.hasNext());
        assertEquals(1, pullRequest.next().getId());
        assertTrue(pullRequest.hasNext());
        assertEquals(2, pullRequest.next().getId());
        assertFalse(pullRequest.hasNext());
        request.close();
    }

    // Multiple PRs
    @Test
    public void testOpenMultiplePRsWithMasterAsOrigin() throws IOException {
        // Situation: Hitting the Github API all the PRs and they are all Open but the master is the
        // head branch
        githubApi.stubFor(get(urlEqualTo("/repos/cloudbeers/yolo/pulls?state=open&head=cloudbeers%3Amaster"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBodyFile("../PRs/_files/body-yolo-pulls-open-multiple-PRs.json")));
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);
        GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, mockSCMHeadObserver);
        context.wantOriginPRs(true);
        Set<SCMHead> masterSet = new HashSet<>();
        SCMHead masterHead = new BranchSCMHead("master");
        masterSet.add(masterHead);
        GitHubSCMSourceContext contextSpy = Mockito.spy(context);
        Mockito.when(contextSpy.observer().getIncludes()).thenReturn(masterSet);
        GitHubSCMSourceRequest request =
                contextSpy.newRequest(new GitHubSCMSource("cloudbeers", "yolo", null, false), null);
        Iterator<GHPullRequest> pullRequest =
                new GitHubSCMSource("cloudbeers", "yolo", null, false).new LazyPullRequests(request, repo).iterator();
        // Expected: In the iterator will have 2 items in it
        assertTrue(pullRequest.hasNext());
        assertEquals(1, pullRequest.next().getId());
        assertTrue(pullRequest.hasNext());
        assertEquals(2, pullRequest.next().getId());
        assertFalse(pullRequest.hasNext());
    }
}
