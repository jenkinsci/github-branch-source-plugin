/*
 * The MIT License
 *
 * Copyright 2026 Olivier Lamy
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import hudson.model.FreeStyleProject;
import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class PendingApprovalActionTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void approvalDataPersistence() throws Exception {
        FreeStyleProject job = r.createFreeStyleProject("test-pr-job");

        assertThat(PendingApprovalAction.ApprovalData.exists(job), is(false));

        PendingApprovalAction.ApprovalData data = new PendingApprovalAction.ApprovalData();
        data.state = PendingApprovalAction.ApprovalState.PENDING;
        data.save(job);

        assertThat(PendingApprovalAction.ApprovalData.exists(job), is(true));
        assertThat(new File(job.getRootDir(), "pending-approval.xml").exists(), is(true));

        PendingApprovalAction.ApprovalData loaded = PendingApprovalAction.ApprovalData.load(job);
        assertThat(loaded.state, is(PendingApprovalAction.ApprovalState.PENDING));
        assertThat(loaded.approvedBy, nullValue());

        loaded.state = PendingApprovalAction.ApprovalState.APPROVED;
        loaded.approvedBy = "admin";
        loaded.approvedAt = 1234567890L;
        loaded.approvedPullHash = "abc123";
        loaded.save(job);

        PendingApprovalAction.ApprovalData reloaded = PendingApprovalAction.ApprovalData.load(job);
        assertThat(reloaded.state, is(PendingApprovalAction.ApprovalState.APPROVED));
        assertThat(reloaded.approvedBy, is("admin"));
        assertThat(reloaded.approvedAt, is(1234567890L));
        assertThat(reloaded.approvedPullHash, is("abc123"));

        PendingApprovalAction.ApprovalData.delete(job);
        assertThat(PendingApprovalAction.ApprovalData.exists(job), is(false));
    }

    @Test
    public void actionProperties() {
        PendingApprovalAction pending = new PendingApprovalAction(
                null, PendingApprovalAction.ApprovalState.PENDING, 42, "fork-author", "deadbeef", false);
        assertThat(pending.getState(), is(PendingApprovalAction.ApprovalState.PENDING));
        assertThat(pending.getPrNumber(), is(42));
        assertThat(pending.getPrAuthor(), is("fork-author"));
        assertThat(pending.getCurrentPullHash(), is("deadbeef"));
        assertThat(pending.isRequireApprovalForNewCommits(), is(false));
        assertThat(pending.getDisplayName(), is(Messages.PendingApprovalAction_displayName()));
        assertThat(pending.getIconFileName(), notNullValue());
        assertThat(pending.getUrlName(), is("pendingApproval"));

        PendingApprovalAction approved = new PendingApprovalAction(
                null, PendingApprovalAction.ApprovalState.APPROVED, 42, "fork-author", "deadbeef", false);
        assertThat(approved.getState(), is(PendingApprovalAction.ApprovalState.APPROVED));
        assertThat(approved.getDisplayName(), is(Messages.PendingApprovalAction_approved()));
        assertThat(approved.getIconFileName(), nullValue());
    }

    @Test
    public void externalApprovalCause() {
        PendingApprovalAction.ExternalApprovalCause cause = new PendingApprovalAction.ExternalApprovalCause();
        assertThat(cause.getShortDescription(), is("External approval granted"));
    }

    @Test
    public void regularJobsAreLeftAlone() throws Exception {
        FreeStyleProject job = r.createFreeStyleProject("regular-job");
        new PendingApprovalAction.ApprovalItemListener().onCreated(job);
        assertThat(job.isDisabled(), is(false));
        assertThat(job.getAction(PendingApprovalAction.class), nullValue());
    }
}
