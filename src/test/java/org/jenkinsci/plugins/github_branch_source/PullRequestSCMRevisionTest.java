/*
 * The MIT License
 *
 * Copyright (c) 2019 CloudBees, Inc.
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
 *
 */

package org.jenkinsci.plugins.github_branch_source;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;

import hudson.AbortException;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadOrigin;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

class PullRequestSCMRevisionTest extends AbstractGitHubWireMockTest {
    private GitHub github;
    private GHRepository repo;

    private static final SCMHead MASTER = new BranchSCMHead("master");
    private static final PullRequestSCMHead PR_HEAD = new PullRequestSCMHead(
            "",
            "stephenc",
            "yolo",
            "master",
            1,
            (BranchSCMHead) MASTER,
            SCMHeadOrigin.DEFAULT,
            ChangeRequestCheckoutStrategy.HEAD);
    private static final PullRequestSCMHead PR_MERGE = new PullRequestSCMHead(
            "",
            "stephenc",
            "yolo",
            "master",
            1,
            (BranchSCMHead) MASTER,
            SCMHeadOrigin.DEFAULT,
            ChangeRequestCheckoutStrategy.MERGE);

    @Override
    @BeforeEach
    void beforeEach() throws Exception {
        super.beforeEach();
        github = Connector.connect("http://localhost:" + githubApi.getPort(), null);
        repo = github.getRepository("cloudbeers/yolo");
    }

    @Test
    void createHeadWithNullMergeRevision() {
        PullRequestSCMHead currentHead = PR_HEAD;
        PullRequestSCMHead otherHead = PR_MERGE;

        PullRequestSCMRevision currentRevision =
                new PullRequestSCMRevision(currentHead, "master-revision", "pr-branch-revision");
        assertThat(currentRevision.toString(), is("pr-branch-revision"));

        assertDoesNotThrow(currentRevision::validateMergeHash, "Validation should succeed, but: ");

        // equivalence
        assertTrue(currentRevision.equivalent(
                new PullRequestSCMRevision(currentHead, "master-revision-changed", "pr-branch-revision", "any")));
        assertFalse(currentRevision.equivalent(new PullRequestSCMRevision(
                currentHead, "master-revision-changed", "pr-branch-revision-changed", "any")));
        assertFalse(currentRevision.equivalent(
                new PullRequestSCMRevision(otherHead, "master-revision-changed", "pr-branch-revision", "any")));

        // equality
        assertThat(
                currentRevision,
                is(new PullRequestSCMRevision(currentHead, "master-revision", "pr-branch-revision", null)));
        assertThat(
                currentRevision,
                is(new PullRequestSCMRevision(currentHead, "master-revision", "pr-branch-revision", "any")));
        assertThat(
                currentRevision,
                is(new PullRequestSCMRevision(currentHead, "master-revision-changed", "pr-branch-revision", "any")));
        assertThat(
                currentRevision,
                not(is(new PullRequestSCMRevision(
                        currentHead, "master-revision", "pr-branch-revision-changed", "any"))));

        assertThat(
                currentRevision,
                not(is(new PullRequestSCMRevision(otherHead, "master-revision", "pr-branch-revision", null))));
    }

    @Test
    void createHeadWithMergeRevision() {
        PullRequestSCMHead currentHead = PR_HEAD;
        PullRequestSCMHead otherHead = PR_MERGE;

        PullRequestSCMRevision currentRevision =
                new PullRequestSCMRevision(currentHead, "master-revision", "pr-branch-revision", "pr-merge-revision");
        assertThat(currentRevision.toString(), is("pr-branch-revision"));

        assertDoesNotThrow(currentRevision::validateMergeHash, "Validation should succeed, but: ");

        // equivalence
        assertTrue(currentRevision.equivalent(
                new PullRequestSCMRevision(currentHead, "master-revision-changed", "pr-branch-revision", "any")));
        assertFalse(currentRevision.equivalent(new PullRequestSCMRevision(
                currentHead, "master-revision-changed", "pr-branch-revision-changed", "any")));
        assertFalse(currentRevision.equivalent(
                new PullRequestSCMRevision(otherHead, "master-revision-changed", "pr-branch-revision", "any")));

        // equality
        assertThat(
                currentRevision,
                is(new PullRequestSCMRevision(currentHead, "master-revision", "pr-branch-revision", null)));
        assertThat(
                currentRevision,
                is(new PullRequestSCMRevision(currentHead, "master-revision", "pr-branch-revision", "any")));
        assertThat(
                currentRevision,
                is(new PullRequestSCMRevision(currentHead, "master-revision-changed", "pr-branch-revision", "any")));
        assertThat(
                currentRevision,
                not(is(new PullRequestSCMRevision(
                        currentHead, "master-revision", "pr-branch-revision-changed", "any"))));

        assertThat(
                currentRevision,
                not(is(new PullRequestSCMRevision(otherHead, "master-revision", "pr-branch-revision", null))));
    }

    @Test
    void createMergeWithNullMergeRevision() {
        PullRequestSCMHead currentHead = PR_MERGE;
        PullRequestSCMHead otherHead = PR_HEAD;

        PullRequestSCMRevision currentRevision =
                new PullRequestSCMRevision(currentHead, "master-revision", "pr-branch-revision");
        assertThat(currentRevision.toString(), is("pr-branch-revision+master-revision (UNKNOWN_MERGE_STATE)"));

        assertDoesNotThrow(currentRevision::validateMergeHash, "Validation should succeed, but: ");

        // equivalence
        assertTrue(currentRevision.equivalent(
                new PullRequestSCMRevision(currentHead, "master-revision-changed", "pr-branch-revision", "any")));
        assertFalse(currentRevision.equivalent(new PullRequestSCMRevision(
                currentHead, "master-revision-changed", "pr-branch-revision-changed", "any")));
        assertFalse(currentRevision.equivalent(
                new PullRequestSCMRevision(otherHead, "master-revision-changed", "pr-branch-revision", "any")));

        // equality
        assertThat(
                currentRevision,
                is(new PullRequestSCMRevision(currentHead, "master-revision", "pr-branch-revision", null)));
        assertThat(
                currentRevision,
                is(new PullRequestSCMRevision(currentHead, "master-revision", "pr-branch-revision", "any")));
        assertThat(
                currentRevision,
                not(is(new PullRequestSCMRevision(
                        currentHead, "master-revision-changed", "pr-branch-revision", "any"))));
        assertThat(
                currentRevision,
                not(is(new PullRequestSCMRevision(
                        currentHead, "master-revision", "pr-branch-revision-changed", "any"))));

        assertThat(
                currentRevision,
                not(is(new PullRequestSCMRevision(otherHead, "master-revision", "pr-branch-revision", null))));
    }

    @Test
    void createMergeWithNotMergeableRevision() {
        PullRequestSCMHead currentHead = PR_MERGE;
        PullRequestSCMHead otherHead = PR_HEAD;

        PullRequestSCMRevision currentRevision = new PullRequestSCMRevision(
                currentHead, "master-revision", "pr-branch-revision", PullRequestSCMRevision.NOT_MERGEABLE_HASH);
        assertThat(currentRevision.toString(), is("pr-branch-revision+master-revision (NOT_MERGEABLE)"));

        // validation should fail for this PR.
        Exception abort = null;
        try {
            currentRevision.validateMergeHash();
        } catch (Exception e) {
            abort = e;
        }
        assertThat(abort, instanceOf(AbortException.class));
        assertThat(abort.getMessage(), containsString("Not mergeable"));

        // equivalence
        assertTrue(currentRevision.equivalent(
                new PullRequestSCMRevision(currentHead, "master-revision-changed", "pr-branch-revision", "any")));
        assertFalse(currentRevision.equivalent(new PullRequestSCMRevision(
                currentHead, "master-revision-changed", "pr-branch-revision-changed", "any")));
        assertFalse(currentRevision.equivalent(
                new PullRequestSCMRevision(otherHead, "master-revision-changed", "pr-branch-revision", "any")));

        // equality
        assertThat(
                currentRevision,
                is(new PullRequestSCMRevision(currentHead, "master-revision", "pr-branch-revision", null)));
        assertThat(
                currentRevision,
                is(new PullRequestSCMRevision(currentHead, "master-revision", "pr-branch-revision", "any")));
        assertThat(
                currentRevision,
                not(is(new PullRequestSCMRevision(
                        currentHead, "master-revision-changed", "pr-branch-revision", "any"))));
        assertThat(
                currentRevision,
                not(is(new PullRequestSCMRevision(
                        currentHead, "master-revision", "pr-branch-revision-changed", "any"))));

        assertThat(
                currentRevision,
                not(is(new PullRequestSCMRevision(otherHead, "master-revision", "pr-branch-revision", null))));
    }

    @Test
    void createMergeWithMergeRevision() {
        PullRequestSCMHead currentHead = PR_MERGE;
        PullRequestSCMHead otherHead = PR_HEAD;

        PullRequestSCMRevision currentRevision =
                new PullRequestSCMRevision(currentHead, "master-revision", "pr-branch-revision", "pr-merge-revision");
        assertThat(currentRevision.toString(), is("pr-branch-revision+master-revision (pr-merge-revision)"));

        assertDoesNotThrow(currentRevision::validateMergeHash, "Validation should succeed, but: ");

        // equivalence
        assertTrue(currentRevision.equivalent(
                new PullRequestSCMRevision(currentHead, "master-revision-changed", "pr-branch-revision", "any")));
        assertFalse(currentRevision.equivalent(new PullRequestSCMRevision(
                currentHead, "master-revision-changed", "pr-branch-revision-changed", "any")));
        assertFalse(currentRevision.equivalent(
                new PullRequestSCMRevision(otherHead, "master-revision-changed", "pr-branch-revision", "any")));

        // equality
        assertThat(
                currentRevision,
                is(new PullRequestSCMRevision(currentHead, "master-revision", "pr-branch-revision", null)));
        assertThat(
                currentRevision,
                is(new PullRequestSCMRevision(currentHead, "master-revision", "pr-branch-revision", "any")));
        assertThat(
                currentRevision,
                not(is(new PullRequestSCMRevision(
                        currentHead, "master-revision-changed", "pr-branch-revision", "any"))));
        assertThat(
                currentRevision,
                not(is(new PullRequestSCMRevision(
                        currentHead, "master-revision", "pr-branch-revision-changed", "any"))));

        assertThat(
                currentRevision,
                not(is(new PullRequestSCMRevision(otherHead, "master-revision", "pr-branch-revision", null))));
    }
}
