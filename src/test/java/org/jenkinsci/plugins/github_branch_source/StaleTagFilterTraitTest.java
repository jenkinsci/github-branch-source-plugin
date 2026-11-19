package org.jenkinsci.plugins.github_branch_source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.PrintStream;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.trait.SCMHeadFilter;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceRequest;
import org.junit.Test;

public class StaleTagFilterTraitTest {

    // ── constructor / setters ────────────────────────────────────────────────

    @Test
    public void daysStaleIsStoredAsSupplied() {
        StaleTagFilterTrait trait = new StaleTagFilterTrait(30);
        assertEquals(30, trait.getDaysStale());
    }

    @Test
    public void daysStaleMinimumIsOne() {
        assertEquals(1, new StaleTagFilterTrait(0).getDaysStale());
        assertEquals(1, new StaleTagFilterTrait(-5).getDaysStale());
        assertEquals(1, new StaleTagFilterTrait(1).getDaysStale());
    }

    @Test
    public void regexFieldsDefaultToNull() {
        StaleTagFilterTrait trait = new StaleTagFilterTrait(30);
        assertNull(trait.getIncludeRegex());
        assertNull(trait.getExcludeRegex());
    }

    @Test
    public void blankRegexIsTreatedAsNull() {
        StaleTagFilterTrait trait = new StaleTagFilterTrait(30);
        trait.setIncludeRegex("   ");
        trait.setExcludeRegex("");
        assertNull(trait.getIncludeRegex());
        assertNull(trait.getExcludeRegex());
    }

    @Test
    public void regexFieldsAreStored() {
        StaleTagFilterTrait trait = new StaleTagFilterTrait(30);
        trait.setIncludeRegex("v[0-9]+\\..*");
        trait.setExcludeRegex("v.*-lts");
        assertEquals("v[0-9]+\\..*", trait.getIncludeRegex());
        assertEquals("v.*-lts", trait.getExcludeRegex());
    }

    @Test
    public void dryRunDefaultsToFalseAndIsStored() {
        StaleTagFilterTrait trait = new StaleTagFilterTrait(30);
        assertFalse(trait.isDryRun());
        trait.setDryRun(true);
        assertTrue(trait.isDryRun());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private SCMHeadFilter buildFilter(StaleTagFilterTrait trait) {
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
        return buildFilter(new StaleTagFilterTrait(daysStale));
    }

    /** The tag's timestamp is carried on the head itself (set at discovery time). */
    private GitHubTagSCMHead tagHead(String tagName, long ageInDays) {
        long tagTimeMs = System.currentTimeMillis() - ageInDays * 24 * 60 * 60 * 1000L;
        return new GitHubTagSCMHead(tagName, tagTimeMs);
    }

    private GitHubSCMSourceRequest mockRequest() throws Exception {
        GitHubSCMSourceRequest request = mock(GitHubSCMSourceRequest.class);
        hudson.model.TaskListener listener = mock(hudson.model.TaskListener.class);
        when(listener.getLogger()).thenReturn(mock(PrintStream.class));
        when(request.listener()).thenReturn(listener);
        return request;
    }

    // ── basic stale logic ────────────────────────────────────────────────────

    @Test
    public void freshTagIsNotExcluded() throws Exception {
        SCMHeadFilter filter = buildFilter(30);
        SCMHead head = tagHead("v1.0.0", 5);
        assertFalse(filter.isExcluded(mockRequest(), head));
    }

    @Test
    public void staleTagIsExcluded() throws Exception {
        SCMHeadFilter filter = buildFilter(30);
        SCMHead head = tagHead("v0.1.0", 31);
        assertTrue(filter.isExcluded(mockRequest(), head));
    }

    @Test
    public void tagExactlyAtThresholdIsExcluded() throws Exception {
        SCMHeadFilter filter = buildFilter(30);
        SCMHead head = tagHead("v0.2.0", 30);
        assertTrue(filter.isExcluded(mockRequest(), head));
    }

    @Test
    public void staleTagInDryRunIsNotExcluded() throws Exception {
        StaleTagFilterTrait trait = new StaleTagFilterTrait(30);
        trait.setDryRun(true);
        SCMHeadFilter filter = buildFilter(trait);
        SCMHead head = tagHead("v0.1.0", 60);
        assertFalse(filter.isExcluded(mockRequest(), head));
    }

    @Test
    public void tagWithUnknownTimestampIsNotExcluded() throws Exception {
        // discovery writes a 0L sentinel when it couldn't resolve a date
        SCMHeadFilter filter = buildFilter(30);
        SCMHead head = new GitHubTagSCMHead("v0.3.0", 0L);
        assertFalse(filter.isExcluded(mockRequest(), head));
    }

    // ── non-tag heads ────────────────────────────────────────────────────────

    @Test
    public void nonTagHeadIsNotExcluded() throws Exception {
        SCMHeadFilter filter = buildFilter(30);
        SCMHead branchHead = new BranchSCMHead("main");
        SCMSourceRequest request = mock(GitHubSCMSourceRequest.class);
        assertFalse(filter.isExcluded(request, branchHead));
    }

    @Test
    public void nonGitHubRequestIsNotExcluded() throws Exception {
        SCMHeadFilter filter = buildFilter(30);
        SCMHead head = tagHead("v1.0.0", 60);
        SCMSourceRequest request = mock(SCMSourceRequest.class);
        assertFalse(filter.isExcluded(request, head));
    }

    // ── includeRegex ─────────────────────────────────────────────────────────

    @Test
    public void staleTagMatchingIncludeRegexIsExcluded() throws Exception {
        StaleTagFilterTrait trait = new StaleTagFilterTrait(30);
        trait.setIncludeRegex("v[0-9]+\\..*");
        SCMHeadFilter filter = buildFilter(trait);

        SCMHead head = tagHead("v1.2.3", 60);
        assertTrue(filter.isExcluded(mockRequest(), head));
    }

    @Test
    public void staleTagNotMatchingIncludeRegexIsNotExcluded() throws Exception {
        StaleTagFilterTrait trait = new StaleTagFilterTrait(30);
        trait.setIncludeRegex("v[0-9]+\\..*");
        SCMHeadFilter filter = buildFilter(trait);

        SCMHead head = tagHead("nightly-20240101", 60);
        assertFalse(filter.isExcluded(mockRequest(), head));
    }

    // ── excludeRegex ─────────────────────────────────────────────────────────

    @Test
    public void staleTagMatchingExcludeRegexIsNotExcluded() throws Exception {
        StaleTagFilterTrait trait = new StaleTagFilterTrait(30);
        trait.setExcludeRegex(".*-lts");
        SCMHeadFilter filter = buildFilter(trait);

        SCMHead head = tagHead("v2.0-lts", 365);
        assertFalse(filter.isExcluded(mockRequest(), head));
    }

    @Test
    public void staleTagNotMatchingExcludeRegexIsExcluded() throws Exception {
        StaleTagFilterTrait trait = new StaleTagFilterTrait(30);
        trait.setExcludeRegex(".*-lts");
        SCMHeadFilter filter = buildFilter(trait);

        SCMHead head = tagHead("v1.9.0", 60);
        assertTrue(filter.isExcluded(mockRequest(), head));
    }

    // ── includeRegex + excludeRegex together ─────────────────────────────────

    @Test
    public void excludeRegexTakesPrecedenceOverIncludeRegex() throws Exception {
        StaleTagFilterTrait trait = new StaleTagFilterTrait(30);
        trait.setIncludeRegex("v[0-9]+\\..*");
        trait.setExcludeRegex(".*-lts");
        SCMHeadFilter filter = buildFilter(trait);

        // Matches include but also matches exclude — should not be excluded
        SCMHead head = tagHead("v2.0-lts", 365);
        assertFalse(filter.isExcluded(mockRequest(), head));
    }

    @Test
    public void matchesIncludeButNotExcludeIsExcludedWhenStale() throws Exception {
        StaleTagFilterTrait trait = new StaleTagFilterTrait(30);
        trait.setIncludeRegex("v[0-9]+\\..*");
        trait.setExcludeRegex(".*-lts");
        SCMHeadFilter filter = buildFilter(trait);

        SCMHead head = tagHead("v1.9.0", 60);
        assertTrue(filter.isExcluded(mockRequest(), head));
    }
}
