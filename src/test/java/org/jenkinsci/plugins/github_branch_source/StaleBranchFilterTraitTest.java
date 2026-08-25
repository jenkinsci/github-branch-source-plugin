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
import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHRepository;

public class StaleBranchFilterTraitTest {

    // ── constructor / setters ────────────────────────────────────────────────

    @Test
    public void daysStaleIsStoredAsSupplied() {
        StaleBranchFilterTrait trait = new StaleBranchFilterTrait(30);
        assertEquals(30, trait.getDaysStale());
    }

    @Test
    public void daysStaleMinimumIsOne() {
        assertEquals(1, new StaleBranchFilterTrait(0).getDaysStale());
        assertEquals(1, new StaleBranchFilterTrait(-5).getDaysStale());
        assertEquals(1, new StaleBranchFilterTrait(1).getDaysStale());
    }

    @Test
    public void regexFieldsDefaultToNull() {
        StaleBranchFilterTrait trait = new StaleBranchFilterTrait(30);
        assertNull(trait.getIncludeRegex());
        assertNull(trait.getExcludeRegex());
    }

    @Test
    public void blankRegexIsTreatedAsNull() {
        StaleBranchFilterTrait trait = new StaleBranchFilterTrait(30);
        trait.setIncludeRegex("   ");
        trait.setExcludeRegex("");
        assertNull(trait.getIncludeRegex());
        assertNull(trait.getExcludeRegex());
    }

    @Test
    public void regexFieldsAreStored() {
        StaleBranchFilterTrait trait = new StaleBranchFilterTrait(30);
        trait.setIncludeRegex("feature/.*");
        trait.setExcludeRegex("feature/keep-.*");
        assertEquals("feature/.*", trait.getIncludeRegex());
        assertEquals("feature/keep-.*", trait.getExcludeRegex());
    }

    @Test
    public void dryRunDefaultsToFalseAndIsStored() {
        StaleBranchFilterTrait trait = new StaleBranchFilterTrait(30);
        assertFalse(trait.isDryRun());
        trait.setDryRun(true);
        assertTrue(trait.isDryRun());
    }

    // ── filter builder ───────────────────────────────────────────────────────

    private SCMHeadFilter buildFilter(StaleBranchFilterTrait trait) {
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
        return buildFilter(new StaleBranchFilterTrait(daysStale));
    }

    private GitHubSCMSourceRequest mockRequest(String branchName, long ageInDays) throws Exception {
        long commitTimeMs = System.currentTimeMillis() - ageInDays * 24 * 60 * 60 * 1000L;

        GHCommit commit = mock(GHCommit.class);
        when(commit.getCommitDate()).thenReturn(new Date(commitTimeMs));

        GHRepository repo = mock(GHRepository.class);
        when(repo.getCommit(org.mockito.ArgumentMatchers.anyString())).thenReturn(commit);

        GHBranch branch = mock(GHBranch.class);
        when(branch.getName()).thenReturn(branchName);
        when(branch.getSHA1()).thenReturn("abc123");

        GitHubSCMSourceRequest request = mock(GitHubSCMSourceRequest.class);
        when(request.getBranches()).thenReturn(Collections.singletonList(branch));
        when(request.getRepository()).thenReturn(repo);

        hudson.model.TaskListener listener = mock(hudson.model.TaskListener.class);
        when(listener.getLogger()).thenReturn(mock(PrintStream.class));
        when(request.listener()).thenReturn(listener);

        return request;
    }

    // ── basic stale logic ────────────────────────────────────────────────────

    @Test
    public void freshBranchIsNotExcluded() throws Exception {
        SCMHeadFilter filter = buildFilter(30);
        SCMHead head = new BranchSCMHead("main");
        GitHubSCMSourceRequest request = mockRequest("main", 5);
        assertFalse(filter.isExcluded(request, head));
    }

    @Test
    public void staleBranchIsExcluded() throws Exception {
        SCMHeadFilter filter = buildFilter(30);
        SCMHead head = new BranchSCMHead("old-feature");
        GitHubSCMSourceRequest request = mockRequest("old-feature", 31);
        assertTrue(filter.isExcluded(request, head));
    }

    @Test
    public void branchExactlyAtThresholdIsExcluded() throws Exception {
        SCMHeadFilter filter = buildFilter(30);
        SCMHead head = new BranchSCMHead("borderline");
        GitHubSCMSourceRequest request = mockRequest("borderline", 30);
        assertTrue(filter.isExcluded(request, head));
    }

    @Test
    public void staleBranchInDryRunIsNotExcluded() throws Exception {
        StaleBranchFilterTrait trait = new StaleBranchFilterTrait(30);
        trait.setDryRun(true);
        SCMHeadFilter filter = buildFilter(trait);
        SCMHead head = new BranchSCMHead("old-feature");
        GitHubSCMSourceRequest request = mockRequest("old-feature", 60);
        assertFalse(filter.isExcluded(request, head));
    }

    @Test
    public void defaultBranchIsNeverExcluded() throws Exception {
        SCMHeadFilter filter = buildFilter(30);
        SCMHead head = new BranchSCMHead("main");

        long commitTimeMs = System.currentTimeMillis() - 365L * 24 * 60 * 60 * 1000L;
        GHCommit commit = mock(GHCommit.class);
        when(commit.getCommitDate()).thenReturn(new Date(commitTimeMs));

        GHRepository repo = mock(GHRepository.class);
        when(repo.getCommit(org.mockito.ArgumentMatchers.anyString())).thenReturn(commit);
        when(repo.getDefaultBranch()).thenReturn("main");

        GHBranch branch = mock(GHBranch.class);
        when(branch.getName()).thenReturn("main");
        when(branch.getSHA1()).thenReturn("abc123");

        GitHubSCMSourceRequest request = mock(GitHubSCMSourceRequest.class);
        when(request.getBranches()).thenReturn(Collections.singletonList(branch));
        when(request.getRepository()).thenReturn(repo);

        assertFalse(filter.isExcluded(request, head));
    }

    @Test
    public void protectedBranchIsNeverExcluded() throws Exception {
        SCMHeadFilter filter = buildFilter(30);
        SCMHead head = new BranchSCMHead("release");

        long commitTimeMs = System.currentTimeMillis() - 365L * 24 * 60 * 60 * 1000L;
        GHCommit commit = mock(GHCommit.class);
        when(commit.getCommitDate()).thenReturn(new Date(commitTimeMs));

        GHRepository repo = mock(GHRepository.class);
        when(repo.getCommit(org.mockito.ArgumentMatchers.anyString())).thenReturn(commit);
        when(repo.getDefaultBranch()).thenReturn("main");

        GHBranch branch = mock(GHBranch.class);
        when(branch.getName()).thenReturn("release");
        when(branch.getSHA1()).thenReturn("abc123");
        when(branch.isProtected()).thenReturn(true);

        GitHubSCMSourceRequest request = mock(GitHubSCMSourceRequest.class);
        when(request.getBranches()).thenReturn(Collections.singletonList(branch));
        when(request.getRepository()).thenReturn(repo);

        hudson.model.TaskListener listener = mock(hudson.model.TaskListener.class);
        when(listener.getLogger()).thenReturn(mock(PrintStream.class));
        when(request.listener()).thenReturn(listener);

        assertFalse(filter.isExcluded(request, head));
    }

    @Test
    public void nonBranchHeadIsNotExcluded() throws Exception {
        SCMHeadFilter filter = buildFilter(30);
        SCMHead prHead = mock(SCMHead.class);
        SCMSourceRequest request = mock(GitHubSCMSourceRequest.class);
        assertFalse(filter.isExcluded(request, prHead));
    }

    @Test
    public void nonGitHubRequestIsNotExcluded() throws Exception {
        SCMHeadFilter filter = buildFilter(30);
        SCMHead head = new BranchSCMHead("main");
        SCMSourceRequest request = mock(SCMSourceRequest.class);
        assertFalse(filter.isExcluded(request, head));
    }

    // ── includeRegex ─────────────────────────────────────────────────────────

    @Test
    public void staleBranchMatchingIncludeRegexIsExcluded() throws Exception {
        StaleBranchFilterTrait trait = new StaleBranchFilterTrait(30);
        trait.setIncludeRegex("feature/.*");
        SCMHeadFilter filter = buildFilter(trait);

        SCMHead head = new BranchSCMHead("feature/old");
        GitHubSCMSourceRequest request = mockRequest("feature/old", 60);
        assertTrue(filter.isExcluded(request, head));
    }

    @Test
    public void staleBranchNotMatchingIncludeRegexIsNotExcluded() throws Exception {
        StaleBranchFilterTrait trait = new StaleBranchFilterTrait(30);
        trait.setIncludeRegex("feature/.*");
        SCMHeadFilter filter = buildFilter(trait);

        SCMHead head = new BranchSCMHead("bugfix/old");
        GitHubSCMSourceRequest request = mockRequest("bugfix/old", 60);
        assertFalse(filter.isExcluded(request, head));
    }

    // ── excludeRegex ─────────────────────────────────────────────────────────

    @Test
    public void staleBranchMatchingExcludeRegexIsNotExcluded() throws Exception {
        StaleBranchFilterTrait trait = new StaleBranchFilterTrait(30);
        trait.setExcludeRegex("release/.*");
        SCMHeadFilter filter = buildFilter(trait);

        SCMHead head = new BranchSCMHead("release/1.0");
        GitHubSCMSourceRequest request = mockRequest("release/1.0", 60);
        assertFalse(filter.isExcluded(request, head));
    }

    @Test
    public void staleBranchNotMatchingExcludeRegexIsExcluded() throws Exception {
        StaleBranchFilterTrait trait = new StaleBranchFilterTrait(30);
        trait.setExcludeRegex("release/.*");
        SCMHeadFilter filter = buildFilter(trait);

        SCMHead head = new BranchSCMHead("feature/old");
        GitHubSCMSourceRequest request = mockRequest("feature/old", 60);
        assertTrue(filter.isExcluded(request, head));
    }

    // ── includeRegex + excludeRegex together ─────────────────────────────────

    @Test
    public void excludeRegexTakesPrecedenceOverIncludeRegex() throws Exception {
        StaleBranchFilterTrait trait = new StaleBranchFilterTrait(30);
        trait.setIncludeRegex("feature/.*");
        trait.setExcludeRegex("feature/keep-.*");
        SCMHeadFilter filter = buildFilter(trait);

        // Matches include but also matches exclude — should not be excluded
        SCMHead head = new BranchSCMHead("feature/keep-this");
        GitHubSCMSourceRequest request = mockRequest("feature/keep-this", 60);
        assertFalse(filter.isExcluded(request, head));
    }

    @Test
    public void matchesIncludeButNotExcludeIsExcludedWhenStale() throws Exception {
        StaleBranchFilterTrait trait = new StaleBranchFilterTrait(30);
        trait.setIncludeRegex("feature/.*");
        trait.setExcludeRegex("feature/keep-.*");
        SCMHeadFilter filter = buildFilter(trait);

        SCMHead head = new BranchSCMHead("feature/old");
        GitHubSCMSourceRequest request = mockRequest("feature/old", 60);
        assertTrue(filter.isExcluded(request, head));
    }
}
