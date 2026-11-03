package org.jenkinsci.plugins.github_branch_source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.PrintStream;
import java.util.Collections;
import java.util.Date;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.trait.SCMHeadFilter;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceRequest;
import org.junit.Test;
import org.kohsuke.github.GHPullRequest;

public class StalePullRequestFilterTraitTest {

    // ── constructor / setters ────────────────────────────────────────────────

    @Test
    public void daysStaleIsStoredAsSupplied() {
        StalePullRequestFilterTrait trait = new StalePullRequestFilterTrait(30);
        assertEquals(30, trait.getDaysStale());
    }

    @Test
    public void daysStaleMinimumIsOne() {
        assertEquals(1, new StalePullRequestFilterTrait(0).getDaysStale());
        assertEquals(1, new StalePullRequestFilterTrait(-5).getDaysStale());
        assertEquals(1, new StalePullRequestFilterTrait(1).getDaysStale());
    }

    @Test
    public void regexFieldsDefaultToNull() {
        StalePullRequestFilterTrait trait = new StalePullRequestFilterTrait(30);
        assertNull(trait.getIncludeRegex());
        assertNull(trait.getExcludeRegex());
    }

    @Test
    public void blankRegexIsTreatedAsNull() {
        StalePullRequestFilterTrait trait = new StalePullRequestFilterTrait(30);
        trait.setIncludeRegex("   ");
        trait.setExcludeRegex("");
        assertNull(trait.getIncludeRegex());
        assertNull(trait.getExcludeRegex());
    }

    @Test
    public void regexFieldsAreStored() {
        StalePullRequestFilterTrait trait = new StalePullRequestFilterTrait(30);
        trait.setIncludeRegex("PR-[0-9]+");
        trait.setExcludeRegex("PR-1");
        assertEquals("PR-[0-9]+", trait.getIncludeRegex());
        assertEquals("PR-1", trait.getExcludeRegex());
    }

    @Test
    public void dryRunDefaultsToFalseAndIsStored() {
        StalePullRequestFilterTrait trait = new StalePullRequestFilterTrait(30);
        assertFalse(trait.isDryRun());
        trait.setDryRun(true);
        assertTrue(trait.isDryRun());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private SCMHeadFilter buildFilter(StalePullRequestFilterTrait trait) {
        final SCMHeadFilter[] captured = new SCMHeadFilter[1];
        SCMSourceContext<?, ?> ctx = mock(SCMSourceContext.class);
        when(ctx.withFilter(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> {
            captured[0] = inv.getArgument(0);
            return ctx;
        });
        trait.decorateContext(ctx);
        return captured[0];
    }

    private SCMHeadFilter buildFilter(int daysStale) {
        return buildFilter(new StalePullRequestFilterTrait(daysStale));
    }

    private GitHubSCMSourceRequest mockRequest(int prNumber, long ageInDays) throws Exception {
        long updatedTimeMs = System.currentTimeMillis() - ageInDays * 24 * 60 * 60 * 1000L;

        GHPullRequest pr = mock(GHPullRequest.class);
        when(pr.getNumber()).thenReturn(prNumber);
        when(pr.getUpdatedAt()).thenReturn(new Date(updatedTimeMs));

        GitHubSCMSourceRequest request = mock(GitHubSCMSourceRequest.class);
        when(request.getPullRequests()).thenReturn(Collections.singletonList(pr));

        hudson.model.TaskListener listener = mock(hudson.model.TaskListener.class);
        when(listener.getLogger()).thenReturn(mock(PrintStream.class));
        when(request.listener()).thenReturn(listener);

        return request;
    }

    private GitHubSCMSourceRequest mockRequestWithNullUpdatedAt(int prNumber) throws Exception {
        GHPullRequest pr = mock(GHPullRequest.class);
        when(pr.getNumber()).thenReturn(prNumber);
        when(pr.getUpdatedAt()).thenReturn(null);

        GitHubSCMSourceRequest request = mock(GitHubSCMSourceRequest.class);
        when(request.getPullRequests()).thenReturn(Collections.singletonList(pr));

        hudson.model.TaskListener listener = mock(hudson.model.TaskListener.class);
        when(listener.getLogger()).thenReturn(mock(PrintStream.class));
        when(request.listener()).thenReturn(listener);

        return request;
    }

    private PullRequestSCMHead mockPRHead(int number) {
        PullRequestSCMHead head = mock(PullRequestSCMHead.class);
        when(head.getName()).thenReturn("PR-" + number);
        when(head.getNumber()).thenReturn(number);
        return head;
    }

    // ── basic stale logic ────────────────────────────────────────────────────

    @Test
    public void freshPullRequestIsNotExcluded() throws Exception {
        SCMHeadFilter filter = buildFilter(30);
        PullRequestSCMHead head = mockPRHead(42);
        GitHubSCMSourceRequest request = mockRequest(42, 5);
        assertFalse(filter.isExcluded(request, head));
    }

    @Test
    public void stalePullRequestIsExcluded() throws Exception {
        SCMHeadFilter filter = buildFilter(30);
        PullRequestSCMHead head = mockPRHead(42);
        GitHubSCMSourceRequest request = mockRequest(42, 31);
        assertTrue(filter.isExcluded(request, head));
    }

    @Test
    public void pullRequestExactlyAtThresholdIsExcluded() throws Exception {
        SCMHeadFilter filter = buildFilter(30);
        PullRequestSCMHead head = mockPRHead(42);
        GitHubSCMSourceRequest request = mockRequest(42, 30);
        assertTrue(filter.isExcluded(request, head));
    }

    @Test
    public void stalePullRequestInDryRunIsNotExcluded() throws Exception {
        StalePullRequestFilterTrait trait = new StalePullRequestFilterTrait(30);
        trait.setDryRun(true);
        SCMHeadFilter filter = buildFilter(trait);
        PullRequestSCMHead head = mockPRHead(42);
        GitHubSCMSourceRequest request = mockRequest(42, 60);
        assertFalse(filter.isExcluded(request, head));
    }

    @Test
    public void nullUpdatedAtIsNotExcluded() throws Exception {
        SCMHeadFilter filter = buildFilter(30);
        PullRequestSCMHead head = mockPRHead(42);
        GitHubSCMSourceRequest request = mockRequestWithNullUpdatedAt(42);
        assertFalse(filter.isExcluded(request, head));
    }

    @Test
    public void nonPullRequestHeadIsNotExcluded() throws Exception {
        SCMHeadFilter filter = buildFilter(30);
        SCMHead branchHead = new BranchSCMHead("main");
        SCMSourceRequest request = mock(GitHubSCMSourceRequest.class);
        assertFalse(filter.isExcluded(request, branchHead));
    }

    @Test
    public void nonGitHubRequestIsNotExcluded() throws Exception {
        SCMHeadFilter filter = buildFilter(30);
        PullRequestSCMHead head = mockPRHead(42);
        SCMSourceRequest request = mock(SCMSourceRequest.class);
        assertFalse(filter.isExcluded(request, head));
    }

    // ── includeRegex ─────────────────────────────────────────────────────────

    @Test
    public void stalePRMatchingIncludeRegexIsExcluded() throws Exception {
        StalePullRequestFilterTrait trait = new StalePullRequestFilterTrait(30);
        trait.setIncludeRegex("PR-[0-9]+");
        SCMHeadFilter filter = buildFilter(trait);

        PullRequestSCMHead head = mockPRHead(42);
        GitHubSCMSourceRequest request = mockRequest(42, 60);
        assertTrue(filter.isExcluded(request, head));
    }

    @Test
    public void stalePRNotMatchingIncludeRegexIsNotExcluded() throws Exception {
        StalePullRequestFilterTrait trait = new StalePullRequestFilterTrait(30);
        trait.setIncludeRegex("PR-1");
        SCMHeadFilter filter = buildFilter(trait);

        PullRequestSCMHead head = mockPRHead(42);
        GitHubSCMSourceRequest request = mockRequest(42, 60);
        assertFalse(filter.isExcluded(request, head));
    }

    // ── excludeRegex ─────────────────────────────────────────────────────────

    @Test
    public void stalePRMatchingExcludeRegexIsNotExcluded() throws Exception {
        StalePullRequestFilterTrait trait = new StalePullRequestFilterTrait(30);
        trait.setExcludeRegex("PR-1");
        SCMHeadFilter filter = buildFilter(trait);

        PullRequestSCMHead head = mockPRHead(1);
        GitHubSCMSourceRequest request = mockRequest(1, 60);
        assertFalse(filter.isExcluded(request, head));
    }

    @Test
    public void stalePRNotMatchingExcludeRegexIsExcluded() throws Exception {
        StalePullRequestFilterTrait trait = new StalePullRequestFilterTrait(30);
        trait.setExcludeRegex("PR-1");
        SCMHeadFilter filter = buildFilter(trait);

        PullRequestSCMHead head = mockPRHead(42);
        GitHubSCMSourceRequest request = mockRequest(42, 60);
        assertTrue(filter.isExcluded(request, head));
    }

    // ── includeRegex + excludeRegex together ─────────────────────────────────

    @Test
    public void excludeRegexTakesPrecedenceOverIncludeRegex() throws Exception {
        StalePullRequestFilterTrait trait = new StalePullRequestFilterTrait(30);
        trait.setIncludeRegex("PR-[0-9]+");
        trait.setExcludeRegex("PR-1");
        SCMHeadFilter filter = buildFilter(trait);

        PullRequestSCMHead head = mockPRHead(1);
        GitHubSCMSourceRequest request = mockRequest(1, 60);
        assertFalse(filter.isExcluded(request, head));
    }

    @Test
    public void matchesIncludeButNotExcludeIsExcludedWhenStale() throws Exception {
        StalePullRequestFilterTrait trait = new StalePullRequestFilterTrait(30);
        trait.setIncludeRegex("PR-[0-9]+");
        trait.setExcludeRegex("PR-1");
        SCMHeadFilter filter = buildFilter(trait);

        PullRequestSCMHead head = mockPRHead(42);
        GitHubSCMSourceRequest request = mockRequest(42, 60);
        assertTrue(filter.isExcluded(request, head));
    }
}
