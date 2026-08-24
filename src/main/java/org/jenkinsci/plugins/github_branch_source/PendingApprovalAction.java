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

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.XmlFile;
import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.Queue;
import hudson.model.queue.ScheduleResult;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.model.TransientActionFactory;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * Shown on a branch job while its fork pull request waits for external approval. Gives an
 * administrator the buttons and endpoints to approve or reject the build.
 */
public class PendingApprovalAction implements Action {

    private static final Logger LOGGER = Logger.getLogger(PendingApprovalAction.class.getName());

    /** Marker stored as the approver when a PR was approved automatically. */
    private static final String AUTO_APPROVAL = "auto-approval";

    private final transient Job<?, ?> owner;
    private final ApprovalState state;
    private final int prNumber;
    private final String prAuthor;
    private final String currentPullHash;
    private final boolean requireApprovalForNewCommits;

    PendingApprovalAction(
            Job<?, ?> owner,
            ApprovalState state,
            int prNumber,
            String prAuthor,
            String currentPullHash,
            boolean requireApprovalForNewCommits) {
        this.owner = owner;
        this.state = state;
        this.prNumber = prNumber;
        this.prAuthor = prAuthor;
        this.currentPullHash = currentPullHash;
        this.requireApprovalForNewCommits = requireApprovalForNewCommits;
    }

    @Override
    public String getIconFileName() {
        if (state == ApprovalState.PENDING) {
            return "symbol-warning plugin-ionicons-api";
        }
        return null;
    }

    @Override
    public String getDisplayName() {
        if (state == ApprovalState.PENDING) {
            return Messages.PendingApprovalAction_displayName();
        }
        return Messages.PendingApprovalAction_approved();
    }

    @Override
    public String getUrlName() {
        return "pendingApproval";
    }

    public ApprovalState getState() {
        return state;
    }

    public int getPrNumber() {
        return prNumber;
    }

    public String getPrAuthor() {
        return prAuthor;
    }

    public String getCurrentPullHash() {
        return currentPullHash;
    }

    public boolean isRequireApprovalForNewCommits() {
        return requireApprovalForNewCommits;
    }

    public Job<?, ?> getOwner() {
        return owner;
    }

    /**
     * Approves the pull request, enabling the job and scheduling a build.
     *
     * @param req the stapler request
     * @return redirect to the parent job
     */
    @RequirePOST
    public HttpResponse doApprove(StaplerRequest2 req) {
        owner.checkPermission(Item.CONFIGURE);
        try {
            String approvedBy = Jenkins.get().getAuthentication2().getName();
            ApprovalData data = ApprovalData.load(owner);
            data.state = ApprovalState.APPROVED;
            data.approvedBy = approvedBy;
            data.approvedAt = System.currentTimeMillis();
            data.approvedPullHash = currentPullHash;
            data.save(owner);
            enableAndBuild();
            LOGGER.log(Level.INFO, "PR #{0} in {1} approved by {2}", new Object[] {
                prNumber, owner.getFullName(), approvedBy
            });
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to approve PR #" + prNumber, e);
        }
        return new HttpRedirect("..");
    }

    /**
     * Rejects the pull request approval.
     *
     * @param req the stapler request
     * @return redirect to the parent job
     */
    @RequirePOST
    public HttpResponse doReject(StaplerRequest2 req) {
        owner.checkPermission(Item.CONFIGURE);
        try {
            ApprovalData data = ApprovalData.load(owner);
            data.state = ApprovalState.PENDING;
            data.approvedBy = null;
            data.approvedAt = 0;
            data.approvedPullHash = null;
            data.save(owner);
            disableJob();
            LOGGER.log(Level.INFO, "PR #{0} in {1} rejected by {2}", new Object[] {
                prNumber,
                owner.getFullName(),
                Jenkins.get().getAuthentication2().getName()
            });
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to reject PR #" + prNumber, e);
        }
        return new HttpRedirect("..");
    }

    private void enableAndBuild() throws IOException {
        if (isJobDisabled()) {
            makeDisabled(false);
        }
        if (owner instanceof Queue.Task) {
            ScheduleResult result = Jenkins.get()
                    .getQueue()
                    .schedule2((Queue.Task) owner, 0, new CauseAction(new ExternalApprovalCause()));
            if (result.isRefused()) {
                LOGGER.log(Level.WARNING, "Failed to schedule build for {0}", owner.getFullName());
            }
        }
    }

    private void disableJob() throws IOException {
        if (!isJobDisabled()) {
            makeDisabled(true);
        }
    }

    private boolean isJobDisabled() {
        try {
            java.lang.reflect.Method m = owner.getClass().getMethod("isDisabled");
            return (boolean) m.invoke(owner);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private void makeDisabled(boolean disabled) throws IOException {
        if (owner instanceof hudson.model.AbstractProject) {
            ((hudson.model.AbstractProject<?, ?>) owner).makeDisabled(disabled);
        } else {
            try {
                java.lang.reflect.Method m = owner.getClass().getMethod("setDisabled", boolean.class);
                m.invoke(owner, disabled);
            } catch (ReflectiveOperationException e) {
                LOGGER.log(Level.WARNING, "Cannot change disabled state of " + owner.getFullName(), e);
            }
        }
    }

    /** Approval state of an external pull request. */
    public enum ApprovalState {
        PENDING,
        APPROVED
    }

    /** Marks a build as having been triggered by an external approval. */
    public static class ExternalApprovalCause extends Cause {
        @Override
        public String getShortDescription() {
            return "External approval granted";
        }
    }

    /** The approval state, saved alongside the job in its directory. */
    static class ApprovalData implements Serializable {
        private static final long serialVersionUID = 1L;

        ApprovalState state = ApprovalState.PENDING;

        @Nullable
        String approvedBy;

        long approvedAt;

        @Nullable
        String approvedPullHash;

        static XmlFile getConfigFile(Job<?, ?> job) {
            return new XmlFile(new File(job.getRootDir(), "pending-approval.xml"));
        }

        static ApprovalData load(Job<?, ?> job) {
            XmlFile file = getConfigFile(job);
            if (file.exists()) {
                try {
                    return (ApprovalData) file.read();
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Failed to load approval data for " + job.getFullName(), e);
                }
            }
            return new ApprovalData();
        }

        void save(Job<?, ?> job) throws IOException {
            getConfigFile(job).write(this);
        }

        static boolean exists(Job<?, ?> job) {
            return getConfigFile(job).exists();
        }

        static void delete(Job<?, ?> job) {
            XmlFile file = getConfigFile(job);
            if (file.exists()) {
                if (!file.getFile().delete()) {
                    LOGGER.log(Level.WARNING, "Failed to delete approval data for {0}", job.getFullName());
                }
            }
        }
    }

    /**
     * Attaches a {@link PendingApprovalAction} to any branch job that needs external approval.
     */
    @Extension
    public static class ActionFactory extends TransientActionFactory<Job> {

        @Override
        public Class<Job> type() {
            return Job.class;
        }

        @NonNull
        @Override
        public Collection<? extends Action> createFor(@NonNull Job target) {
            ExternalApprovalInfo info = ExternalApprovalHelper.getApprovalInfo(target);
            if (info == null) {
                return Collections.emptyList();
            }
            ApprovalData data = resolveApprovalData(target, info);
            return Collections.singletonList(new PendingApprovalAction(
                    target,
                    data.state,
                    info.prNumber,
                    info.prAuthor,
                    info.currentPullHash,
                    info.requireApprovalForNewCommits));
        }
    }

    /**
     * Loads the approval record, initializing it on first use and re-evaluating it when new commits
     * arrive. This is the single writer of the approval state and may make one GitHub API call (to
     * check auto-approval labels), so it must not be called from the scheduler.
     *
     * @param job the branch job
     * @param info the approval info
     * @return the resolved approval data (persisted if it changed)
     */
    private static ApprovalData resolveApprovalData(Job<?, ?> job, ExternalApprovalInfo info) {
        ApprovalData data = ApprovalData.load(job);
        boolean changed = false;
        if (!ApprovalData.exists(job)) {
            // First time we see this PR: auto-approve it if it matches the configured users/labels.
            if (ExternalApprovalHelper.evaluateAutoApproval(info)) {
                data.state = ApprovalState.APPROVED;
                data.approvedBy = AUTO_APPROVAL;
                data.approvedPullHash = info.currentPullHash;
            } else {
                data.state = ApprovalState.PENDING;
            }
            changed = true;
        } else if (info.requireApprovalForNewCommits
                && data.state == ApprovalState.APPROVED
                && data.approvedPullHash != null
                && !data.approvedPullHash.equals(info.currentPullHash)) {
            // A new commit was pushed after approval: keep it approved only if still auto-approved,
            // otherwise require a fresh approval.
            if (ExternalApprovalHelper.evaluateAutoApproval(info)) {
                data.approvedBy = AUTO_APPROVAL;
                data.approvedPullHash = info.currentPullHash;
            } else {
                data.state = ApprovalState.PENDING;
                data.approvedBy = null;
                data.approvedAt = 0;
                data.approvedPullHash = null;
            }
            changed = true;
        }
        if (changed) {
            try {
                data.save(job);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to persist approval data for " + job.getFullName(), e);
            }
        }
        return data;
    }

    /**
     * Holds a job back from building while it is still waiting for external approval.
     */
    @Extension
    public static class QueueDecisionHandler extends Queue.QueueDecisionHandler {

        @Override
        public boolean shouldSchedule(Queue.Task task, java.util.List<Action> actions) {
            if (!(task instanceof Job)) {
                return true;
            }
            Job<?, ?> job = (Job<?, ?>) task;
            ExternalApprovalInfo info = ExternalApprovalHelper.getApprovalInfo(job);
            if (info == null) {
                return true;
            }
            ApprovalData data = ApprovalData.load(job);
            boolean approved = data.state == ApprovalState.APPROVED;
            // A new commit pushed after approval invalidates it, unless the author is an auto-trusted
            // user (checked cheaply here so we never hit the GitHub API under the queue lock).
            if (approved
                    && info.requireApprovalForNewCommits
                    && data.approvedPullHash != null
                    && !data.approvedPullHash.equals(info.currentPullHash)
                    && !ExternalApprovalHelper.isAutoApprovedUser(info)) {
                approved = false;
            }
            if (!approved) {
                PendingApprovalAction helper = new PendingApprovalAction(
                        job, ApprovalState.PENDING, info.prNumber, info.prAuthor, null, false);
                if (!helper.isJobDisabled()) {
                    try {
                        helper.makeDisabled(true);
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "Failed to disable " + job.getFullName(), e);
                    }
                }
                return false;
            }
            return true;
        }
    }
}
