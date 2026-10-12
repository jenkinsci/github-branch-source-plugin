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
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.ItemListener;
import hudson.model.listeners.RunListener;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.branch.MultiBranchProject;
import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;
import jenkins.model.TransientActionFactory;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.verb.POST;

/**
 * Shown on a branch job while its fork pull request waits for external approval, with the buttons
 * and endpoints an administrator uses to approve or take back that approval.
 *
 * <p>What actually holds the pull request back is the job's own disabled flag. Until someone
 * approves, the job stays disabled, so neither branch indexing nor a person clicking Build can start
 * it.
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

    /** Approves the pull request: enables the job and starts a build. */
    @POST
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
            setDisabled(owner, false);
            if (ParameterizedJobMixIn.scheduleBuild2(owner, 0, new CauseAction(new ExternalApprovalCause())) == null) {
                LOGGER.log(Level.WARNING, "Failed to schedule build for {0}", owner.getFullName());
            }
            LOGGER.log(Level.INFO, "PR #{0} in {1} approved by {2}", new Object[] {
                prNumber, owner.getFullName(), approvedBy
            });
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to approve PR #" + prNumber, e);
        }
        return new HttpRedirect("..");
    }

    /** Takes the approval back and disables the job again. */
    @POST
    public HttpResponse doReject(StaplerRequest2 req) {
        owner.checkPermission(Item.CONFIGURE);
        try {
            ApprovalData data = ApprovalData.load(owner);
            data.reset();
            data.save(owner);
            setDisabled(owner, true);
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

    /** Mirrors the approval onto the job. Anything short of an approval leaves it disabled. */
    private static void applyApprovalState(Job<?, ?> job, ApprovalState state) {
        setDisabled(job, state != ApprovalState.APPROVED);
    }

    private static void setDisabled(Job<?, ?> job, boolean disabled) {
        if (!(job instanceof ParameterizedJobMixIn.ParameterizedJob)) {
            LOGGER.log(Level.WARNING, "Cannot change the disabled state of {0}", job.getFullName());
            return;
        }
        ParameterizedJobMixIn.ParameterizedJob<?, ?> project = (ParameterizedJobMixIn.ParameterizedJob<?, ?>) job;
        if (project.isDisabled() == disabled) {
            return;
        }
        try {
            // Disabling also cancels anything this job already has queued.
            project.makeDisabled(disabled);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Cannot change the disabled state of " + job.getFullName(), e);
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

        /** Drops any approval, sending the pull request back to pending. */
        void reset() {
            state = ApprovalState.PENDING;
            approvedBy = null;
            approvedAt = 0;
            approvedPullHash = null;
        }

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

    /** Attaches a {@link PendingApprovalAction} to any branch job that needs external approval. */
    @Extension
    public static class ActionFactory extends TransientActionFactory<Job> {

        @Override
        public Class<Job> type() {
            return Job.class;
        }

        @NonNull
        @Override
        public Class<? extends Action> actionType() {
            return PendingApprovalAction.class;
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
     * Puts a newly discovered fork pull request on hold, and catches the jobs that were already
     * there when the trust policy got switched on.
     */
    @Extension
    public static class ApprovalItemListener extends ItemListener {

        @Override
        public void onCreated(Item item) {
            if (item instanceof Job) {
                refresh((Job<?, ?>) item);
            }
        }

        @Override
        public void onUpdated(Item item) {
            if (item instanceof MultiBranchProject) {
                for (Item child : ((MultiBranchProject<?, ?>) item).getItems()) {
                    if (child instanceof Job) {
                        refresh((Job<?, ?>) child);
                    }
                }
            }
        }

        private static void refresh(Job<?, ?> job) {
            ExternalApprovalInfo info = ExternalApprovalHelper.getApprovalInfo(job);
            if (info != null) {
                applyApprovalState(job, resolveApprovalData(job, info).state);
            }
        }
    }

    /**
     * Spends the approval once the build it was granted for has started: the job goes back to
     * disabled, so the next commit needs a fresh approval. Only does anything when the trust policy
     * asks for approval on new commits.
     */
    @Extension
    public static class ApprovalSpender extends RunListener<Run<?, ?>> {

        @Override
        public void onStarted(Run<?, ?> run, TaskListener listener) {
            Job<?, ?> job = run.getParent();
            ExternalApprovalInfo info = ExternalApprovalHelper.getApprovalInfo(job);
            if (info == null || !info.requireApprovalForNewCommits) {
                return;
            }
            if (ExternalApprovalHelper.isAutoApprovedUser(info)) {
                // Authors on the auto-approval list never have to ask again.
                return;
            }
            ApprovalData data = ApprovalData.load(job);
            if (data.state != ApprovalState.APPROVED) {
                return;
            }
            data.reset();
            try {
                data.save(job);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to reset the approval of " + job.getFullName(), e);
                return;
            }
            setDisabled(job, true);
            listener.getLogger()
                    .println("Approval spent: the next build of PR #" + info.prNumber + " needs a new approval.");
        }
    }

    /**
     * Loads the approval record, writing it the first time we see a pull request and looking at it
     * again once the approved commit has moved on. This is the only writer of the approval state,
     * and it can cost a GitHub call to read labels, so it does nothing at all in between. Whatever
     * it changes is saved and mirrored onto the job.
     */
    private static ApprovalData resolveApprovalData(Job<?, ?> job, ExternalApprovalInfo info) {
        ApprovalData data = ApprovalData.load(job);
        boolean changed = false;
        if (!ApprovalData.exists(job)) {
            // First time we see this PR: approve it straight away if it matches the users or labels.
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
            // Someone pushed after the approval. It only stays approved if it still auto-approves,
            // otherwise it goes back to waiting for a person.
            if (ExternalApprovalHelper.evaluateAutoApproval(info)) {
                data.approvedBy = AUTO_APPROVAL;
                data.approvedPullHash = info.currentPullHash;
            } else {
                data.reset();
            }
            changed = true;
        }
        if (changed) {
            try {
                data.save(job);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to persist approval data for " + job.getFullName(), e);
            }
            applyApprovalState(job, data.state);
        }
        return data;
    }
}
