/*
 * The MIT License
 *
 * Copyright 2025 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.github_branch_source;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import jenkins.scm.api.SCMEvent;
import jenkins.scm.api.SCMEvents;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import jenkins.scm.api.trait.SCMSourceTrait;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.github.extension.GHSubscriberEvent;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.github.GHEvent;

/**
 * Integration tests for {@link PushGHEventSubscriber} that verify the actual behavior
 * of the {@code heads()} method in the SCMHeadEvent, particularly for the new feature
 * that includes PRs with MERGE strategy when their target branch is updated.
 */
public class PushGHEventSubscriberIntegrationTest extends AbstractGitHubWireMockTest {

    private static final int defaultFireDelayInSeconds = GitHubSCMSource.getEventDelaySeconds();
    private static SCMHeadEvent<?> capturedEvent;

    @BeforeClass
    public static void setupDelay() {
        GitHubSCMSource.setEventDelaySeconds(0); // fire immediately without delay
    }

    @Before
    public void resetCapturedEvent() {
        capturedEvent = null;
        TestEventListener.reset();
    }

    @AfterClass
    public static void resetDelay() {
        GitHubSCMSource.setEventDelaySeconds(defaultFireDelayInSeconds);
    }

    /**
     * Test the KEY new functionality: When a branch (e.g., "main") is updated,
     * the heads() method should return both:
     * 1. The updated branch itself
     * 2. All open PRs targeting that branch that have MERGE checkout strategy
     */
    @Test
    public void testBranchUpdateIncludesPRsWithMergeStrategy() throws Exception {
        // Setup WireMock stubs for repository and PR queries
        setupRepositoryStub("test-owner", "test-repo");
        setupPullRequestListStub("test-owner", "test-repo", "main", "prs-targeting-main-with-merge.json");
        setupPullRequestStub("test-owner", "test-repo", 1, "pr-1-origin-merge.json");
        setupPullRequestStub("test-owner", "test-repo", 2, "pr-2-fork-merge.json");

        // Create GitHubSCMSource with branch and PR discovery (MERGE strategy)
        GitHubSCMSource source = createSource("test-owner", "test-repo", true, true, true);

        // Fire push event for branch update to "main"
        PushGHEventSubscriber subscriber = new PushGHEventSubscriber();
        String payload = loadResource("pushEventMainBranchUpdated.json");
        subscriber.onEvent(new GHSubscriberEvent("test-origin", GHEvent.PUSH, payload));

        // Wait for event to be fired and captured
        waitForEvent();
        assertNotNull("Event should have been captured", capturedEvent);
        assertEquals("Event type should be UPDATED", SCMEvent.Type.UPDATED, capturedEvent.getType());

        // Call heads() and collect results
        Map<SCMHead, SCMRevision> heads = collectHeads(capturedEvent, source);

        // Verify the results
        assertThat("Should have 3 heads: main branch + 2 PRs", heads.size(), is(3));

        // Verify main branch is included
        BranchSCMHead mainBranch = findBranchHead(heads, "main");
        assertNotNull("Main branch should be included", mainBranch);

        // Verify PR #1 (origin PR with MERGE) is included
        PullRequestSCMHead pr1 = findPRHead(heads, 1);
        assertNotNull("PR #1 with MERGE strategy should be included", pr1);
        assertEquals("PR #1 should target main", "main", pr1.getTarget().getName());

        // Verify PR #2 (fork PR with MERGE) is included
        PullRequestSCMHead pr2 = findPRHead(heads, 2);
        assertNotNull("PR #2 with MERGE strategy should be included", pr2);
        assertEquals("PR #2 should target main", "main", pr2.getTarget().getName());

        // Verify GitHub API was called to query PRs
        githubApi.verify(getRequestedFor(urlPathEqualTo("/repos/test-owner/test-repo/pulls"))
                .withQueryParam("state", equalTo("open"))
                .withQueryParam("base", equalTo("main")));
    }

    /**
     * Test that PRs with only HEAD checkout strategy are NOT included when their target branch is updated.
     */
    @Test
    public void testBranchUpdateExcludesPRsWithOnlyHeadStrategy() throws Exception {
        // Setup WireMock stubs
        setupRepositoryStub("test-owner", "test-repo");
        setupPullRequestListStub("test-owner", "test-repo", "main", "prs-targeting-main-head-only.json");
        setupPullRequestStub("test-owner", "test-repo", 3, "pr-3-head-only.json");

        // Create GitHubSCMSource with branch discovery and PR discovery with only HEAD strategy
        GitHubSCMSource source = createSource("test-owner", "test-repo", true, true, false);

        // Fire push event for branch update to "main"
        PushGHEventSubscriber subscriber = new PushGHEventSubscriber();
        String payload = loadResource("pushEventMainBranchUpdated.json");
        subscriber.onEvent(new GHSubscriberEvent("test-origin", GHEvent.PUSH, payload));

        // Wait for event to be fired and captured
        waitForEvent();
        assertNotNull("Event should have been captured", capturedEvent);

        // Call heads() and collect results
        Map<SCMHead, SCMRevision> heads = collectHeads(capturedEvent, source);

        // Verify only the branch is included, not the PR
        assertThat("Should have only 1 head: main branch", heads.size(), is(1));

        BranchSCMHead mainBranch = findBranchHead(heads, "main");
        assertNotNull("Main branch should be included", mainBranch);

        // Verify PR #3 is NOT included
        PullRequestSCMHead pr3 = findPRHead(heads, 3);
        assertNull("PR #3 with only HEAD strategy should NOT be included", pr3);
    }

    /**
     * Test that when a branch is CREATED, it does NOT query for PRs.
     * New branches typically don't have PRs targeting them yet.
     */
    @Test
    public void testBranchCreatedDoesNotQueryPRs() throws Exception {
        setupRepositoryStub("test-owner", "test-repo");

        GitHubSCMSource source = createSource("test-owner", "test-repo", true, true, true);

        PushGHEventSubscriber subscriber = new PushGHEventSubscriber();
        String payload = loadResource("pushEventBranchCreated.json");
        subscriber.onEvent(new GHSubscriberEvent("test-origin", GHEvent.PUSH, payload));

        waitForEvent();
        assertNotNull("Event should have been captured", capturedEvent);
        assertEquals("Event type should be CREATED", SCMEvent.Type.CREATED, capturedEvent.getType());

        Map<SCMHead, SCMRevision> heads = collectHeads(capturedEvent, source);

        // Should have only the new branch, not any PRs
        assertThat("Should have only 1 head: the new branch", heads.size(), is(1));

        BranchSCMHead newBranch = findBranchHead(heads, "feature-branch");
        assertNotNull("New branch should be included", newBranch);

        // Verify NO query to PR list endpoint was made
        githubApi.verify(0, getRequestedFor(urlPathMatching("/repos/test-owner/test-repo/pulls.*")));
    }

    /**
     * Test that when a branch is DELETED, the heads() returns empty/deleted marker
     * and does NOT query for PRs.
     */
    @Test
    public void testBranchDeletedDoesNotQueryPRs() throws Exception {
        setupRepositoryStub("test-owner", "test-repo");

        GitHubSCMSource source = createSource("test-owner", "test-repo", true, true, true);

        PushGHEventSubscriber subscriber = new PushGHEventSubscriber();
        String payload = loadResource("pushEventBranchDeleted.json");
        subscriber.onEvent(new GHSubscriberEvent("test-origin", GHEvent.PUSH, payload));

        waitForEvent();
        assertNotNull("Event should have been captured", capturedEvent);
        assertEquals("Event type should be REMOVED", SCMEvent.Type.REMOVED, capturedEvent.getType());

        Map<SCMHead, SCMRevision> heads = collectHeads(capturedEvent, source);

        // For deleted branches, the implementation includes the deleted branch to notify listeners
        // The actual deletion is indicated by the event type being REMOVED
        assertThat("Should have 1 head for deleted branch notification", heads.size(), is(1));

        BranchSCMHead deletedBranch = findBranchHead(heads, "old-feature");
        assertNotNull("Deleted branch should be included for notification", deletedBranch);

        // Verify NO query to PR list endpoint was made
        githubApi.verify(0, getRequestedFor(urlPathMatching("/repos/test-owner/test-repo/pulls.*")));
    }

    /**
     * Test that tag push events do NOT query for PRs.
     * Tags don't have PRs targeting them.
     */
    @Test
    public void testTagCreatedDoesNotQueryPRs() throws Exception {
        setupRepositoryStub("test-owner", "test-repo");

        // Create source with tag discovery enabled
        GitHubSCMSource source = new GitHubSCMSource("test-owner", "test-repo", null, false);
        source.setTraits(Arrays.asList(new BranchDiscoveryTrait(true, true), new TagDiscoveryTrait()));
        source.forceApiUri("http://localhost:" + githubApi.port());

        PushGHEventSubscriber subscriber = new PushGHEventSubscriber();
        String payload = loadResource("pushEventTagCreated.json");
        subscriber.onEvent(new GHSubscriberEvent("test-origin", GHEvent.PUSH, payload));

        waitForEvent();
        assertNotNull("Event should have been captured", capturedEvent);
        assertEquals("Event type should be CREATED", SCMEvent.Type.CREATED, capturedEvent.getType());

        Map<SCMHead, SCMRevision> heads = collectHeads(capturedEvent, source);

        // Should have only the tag
        assertThat("Should have only 1 head: the tag", heads.size(), is(1));

        // Verify it's a tag head
        SCMHead tagHead = heads.keySet().iterator().next();
        assertTrue("Should be a GitHubTagSCMHead", tagHead instanceof GitHubTagSCMHead);
        assertEquals("Tag name should be v1.0.0", "v1.0.0", tagHead.getName());

        // Verify NO query to PR list endpoint was made
        githubApi.verify(0, getRequestedFor(urlPathMatching("/repos/test-owner/test-repo/pulls.*")));
    }

    /**
     * Test that when PR discovery is disabled, branch updates do NOT query for PRs.
     */
    @Test
    public void testBranchUpdateWithPRDiscoveryDisabled() throws Exception {
        setupRepositoryStub("test-owner", "test-repo");

        // Create source with only branch discovery, NO PR discovery
        GitHubSCMSource source = new GitHubSCMSource("test-owner", "test-repo", null, false);
        source.setTraits(Collections.singletonList(new BranchDiscoveryTrait(true, true)));
        source.forceApiUri("http://localhost:" + githubApi.port());

        PushGHEventSubscriber subscriber = new PushGHEventSubscriber();
        String payload = loadResource("pushEventMainBranchUpdated.json");
        subscriber.onEvent(new GHSubscriberEvent("test-origin", GHEvent.PUSH, payload));

        waitForEvent();
        assertNotNull("Event should have been captured", capturedEvent);

        Map<SCMHead, SCMRevision> heads = collectHeads(capturedEvent, source);

        // Should have only the branch
        assertThat("Should have only 1 head: main branch", heads.size(), is(1));

        BranchSCMHead mainBranch = findBranchHead(heads, "main");
        assertNotNull("Main branch should be included", mainBranch);

        // Verify NO query to PR list endpoint was made
        githubApi.verify(0, getRequestedFor(urlPathMatching("/repos/test-owner/test-repo/pulls.*")));
    }

    /**
     * Test handling when GitHub API returns an error for PR query.
     * Should log warning but still include the branch in heads.
     */
    @Test
    public void testBranchUpdateWithPRQueryError() throws Exception {
        setupRepositoryStub("test-owner", "test-repo");

        // Stub PR query to return 500 error
        githubApi.stubFor(get(urlPathEqualTo("/repos/test-owner/test-repo/pulls"))
                .withQueryParam("state", equalTo("open"))
                .withQueryParam("base", equalTo("main"))
                .willReturn(aResponse().withStatus(500).withBody("Internal Server Error")));

        GitHubSCMSource source = createSource("test-owner", "test-repo", true, true, true);

        PushGHEventSubscriber subscriber = new PushGHEventSubscriber();
        String payload = loadResource("pushEventMainBranchUpdated.json");
        subscriber.onEvent(new GHSubscriberEvent("test-origin", GHEvent.PUSH, payload));

        waitForEvent();
        assertNotNull("Event should have been captured", capturedEvent);

        Map<SCMHead, SCMRevision> heads = collectHeads(capturedEvent, source);

        // Should still have the branch, even though PR query failed
        assertThat("Should have at least the branch", heads.size(), greaterThanOrEqualTo(1));

        BranchSCMHead mainBranch = findBranchHead(heads, "main");
        assertNotNull("Main branch should be included despite PR query error", mainBranch);
    }

    /**
     * Test that both origin and fork PRs with MERGE strategy are included.
     */
    @Test
    public void testBranchUpdateIncludesBothOriginAndForkPRs() throws Exception {
        setupRepositoryStub("test-owner", "test-repo");
        setupPullRequestListStub("test-owner", "test-repo", "develop", "prs-targeting-develop-mixed.json");
        setupPullRequestStub("test-owner", "test-repo", 10, "pr-10-origin.json");
        setupPullRequestStub("test-owner", "test-repo", 11, "pr-11-fork.json");

        GitHubSCMSource source = createSource("test-owner", "test-repo", true, true, true);

        PushGHEventSubscriber subscriber = new PushGHEventSubscriber();
        String payload = loadResource("pushEventDevelopBranchUpdated.json");
        subscriber.onEvent(new GHSubscriberEvent("test-origin", GHEvent.PUSH, payload));

        waitForEvent();
        assertNotNull("Event should have been captured", capturedEvent);

        Map<SCMHead, SCMRevision> heads = collectHeads(capturedEvent, source);

        // Should have develop branch + 2 PRs
        assertThat("Should have 3 heads", heads.size(), is(3));

        BranchSCMHead developBranch = findBranchHead(heads, "develop");
        assertNotNull("Develop branch should be included", developBranch);

        PullRequestSCMHead pr10 = findPRHead(heads, 10);
        assertNotNull("Origin PR #10 should be included", pr10);

        PullRequestSCMHead pr11 = findPRHead(heads, 11);
        assertNotNull("Fork PR #11 should be included", pr11);
    }

    // Helper methods

    @SuppressWarnings({"SameParameterValue", "unused"})
    private GitHubSCMSource createSource(
            String owner, String repo, boolean branches, boolean prs, boolean mergeStrategy) {
        GitHubSCMSource source = new GitHubSCMSource(owner, repo, null, false);
        List<SCMSourceTrait> traits = new ArrayList<>();

        if (branches) {
            traits.add(new BranchDiscoveryTrait(true, true));
        }

        if (prs) {
            if (mergeStrategy) {
                traits.add(new OriginPullRequestDiscoveryTrait(
                        EnumSet.of(ChangeRequestCheckoutStrategy.MERGE, ChangeRequestCheckoutStrategy.HEAD)));
                traits.add(new ForkPullRequestDiscoveryTrait(
                        EnumSet.of(ChangeRequestCheckoutStrategy.MERGE, ChangeRequestCheckoutStrategy.HEAD),
                        new ForkPullRequestDiscoveryTrait.TrustContributors()));
            } else {
                traits.add(new OriginPullRequestDiscoveryTrait(EnumSet.of(ChangeRequestCheckoutStrategy.HEAD)));
                traits.add(new ForkPullRequestDiscoveryTrait(
                        EnumSet.of(ChangeRequestCheckoutStrategy.HEAD),
                        new ForkPullRequestDiscoveryTrait.TrustContributors()));
            }
        }

        source.setTraits(traits);
        source.forceApiUri("http://localhost:" + githubApi.port());
        return source;
    }

    @SuppressWarnings("SameParameterValue")
    private void setupRepositoryStub(String owner, String repo) {
        githubApi.stubFor(get(urlEqualTo("/repos/" + owner + "/" + repo))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBodyFile(
                                "PushGHEventSubscriberIntegrationTest/repository-" + owner + "-" + repo + ".json")));
    }

    @SuppressWarnings("SameParameterValue")
    private void setupPullRequestListStub(String owner, String repo, String base, String responseFile) {
        githubApi.stubFor(get(urlPathEqualTo("/repos/" + owner + "/" + repo + "/pulls"))
                .withQueryParam("state", equalTo("open"))
                .withQueryParam("base", equalTo(base))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBodyFile("PushGHEventSubscriberIntegrationTest/" + responseFile)));
    }

    @SuppressWarnings("SameParameterValue")
    private void setupPullRequestStub(String owner, String repo, int number, String responseFile) {
        githubApi.stubFor(get(urlEqualTo("/repos/" + owner + "/" + repo + "/pulls/" + number))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBodyFile("PushGHEventSubscriberIntegrationTest/" + responseFile)));
    }

    private String loadResource(String name) throws IOException {
        return IOUtils.toString(
                Objects.requireNonNull(getClass().getResourceAsStream("PushGHEventSubscriberIntegrationTest/" + name)),
                StandardCharsets.UTF_8);
    }

    private void waitForEvent() throws InterruptedException {
        long watermark = SCMEvents.getWatermark();
        SCMEvents.awaitOne(watermark, 5, TimeUnit.SECONDS);
        TestEventListener.awaitUntilReceived();
    }

    private Map<SCMHead, SCMRevision> collectHeads(SCMHeadEvent<?> event, GitHubSCMSource source) {
        return event.heads(source);
    }

    private BranchSCMHead findBranchHead(Map<SCMHead, SCMRevision> heads, String branchName) {
        for (SCMHead head : heads.keySet()) {
            if (head instanceof BranchSCMHead && head.getName().equals(branchName)) {
                return (BranchSCMHead) head;
            }
        }
        return null;
    }

    private PullRequestSCMHead findPRHead(Map<SCMHead, SCMRevision> heads, int number) {
        for (SCMHead head : heads.keySet()) {
            if (head instanceof PullRequestSCMHead prHead) {
                if (prHead.getNumber() == number) {
                    return prHead;
                }
            }
        }
        return null;
    }

    /**
     * Test extension that listens for SCM events and captures them for testing.
     */
    @TestExtension
    public static class TestEventListener extends jenkins.scm.api.SCMEventListener {
        private static boolean eventReceived = false;

        @Override
        public void onSCMHeadEvent(SCMHeadEvent<?> event) {
            capturedEvent = event;
            eventReceived = true;
        }

        public static void reset() {
            eventReceived = false;
            capturedEvent = null;
        }

        public static void awaitUntilReceived() throws InterruptedException {
            long start = System.currentTimeMillis();
            while (!eventReceived && (System.currentTimeMillis() - start) < 5000) {
                Thread.sleep(50);
            }
        }
    }
}
