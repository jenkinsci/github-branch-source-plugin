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

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.model.Item;
import hudson.model.Job;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.branch.Branch;
import jenkins.branch.BranchProjectFactory;
import jenkins.branch.BranchSource;
import jenkins.branch.MultiBranchProject;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadOrigin;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.trait.SCMSourceTrait;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

/**
 * Helpers for working out whether a branch job needs external approval before it can build, and
 * whether a pull request can be approved automatically.
 */
final class ExternalApprovalHelper {

    private static final Logger LOGGER = Logger.getLogger(ExternalApprovalHelper.class.getName());

    private ExternalApprovalHelper() {}

    /**
     * Returns the approval details when {@code job} is a fork pull request in a multibranch project
     * that uses the {@link ForkPullRequestDiscoveryTrait.TrustExternalApproval} policy.
     *
     * @param job the job to check
     * @return the approval info, or {@code null} when external approval doesn't apply to this job
     */
    @CheckForNull
    @SuppressWarnings({"rawtypes", "unchecked"})
    static ExternalApprovalInfo getApprovalInfo(Job<?, ?> job) {
        if (!(job.getParent() instanceof MultiBranchProject)) {
            return null;
        }
        MultiBranchProject mp = (MultiBranchProject) job.getParent();
        BranchProjectFactory factory = mp.getProjectFactory();
        if (!factory.isProject(job)) {
            return null;
        }
        Branch branch = factory.getBranch(job);
        SCMHead head = branch.getHead();
        if (!(head instanceof PullRequestSCMHead)) {
            return null;
        }
        PullRequestSCMHead prHead = (PullRequestSCMHead) head;
        if (prHead.getOrigin().equals(SCMHeadOrigin.DEFAULT)) {
            return null;
        }
        GitHubSCMSource source = findSourceWithExternalApproval(mp);
        if (source == null) {
            return null;
        }
        ForkPullRequestDiscoveryTrait.TrustExternalApproval trustPolicy = getTrustPolicy(source);
        if (trustPolicy == null) {
            return null;
        }
        String currentPullHash = getCurrentPullHash(factory, job);
        return new ExternalApprovalInfo(
                prHead.getNumber(),
                prHead.getSourceOwner(),
                currentPullHash,
                trustPolicy.isRequireApprovalForNewCommits(),
                trustPolicy.getAutoApprovalUsers(),
                trustPolicy.getAutoApprovalLabels(),
                source,
                mp);
    }

    /** Finds the project's {@link GitHubSCMSource} that uses the external-approval policy, if any. */
    @CheckForNull
    @SuppressWarnings("rawtypes")
    private static GitHubSCMSource findSourceWithExternalApproval(MultiBranchProject mp) {
        for (Object src : mp.getSources()) {
            if (src instanceof BranchSource) {
                SCMSource source = ((BranchSource) src).getSource();
                if (source instanceof GitHubSCMSource && getTrustPolicy((GitHubSCMSource) source) != null) {
                    return (GitHubSCMSource) source;
                }
            }
        }
        return null;
    }

    @CheckForNull
    private static ForkPullRequestDiscoveryTrait.TrustExternalApproval getTrustPolicy(GitHubSCMSource source) {
        for (SCMSourceTrait trait : source.getTraits()) {
            if (trait instanceof ForkPullRequestDiscoveryTrait) {
                Object trust = ((ForkPullRequestDiscoveryTrait) trait).getTrust();
                if (trust instanceof ForkPullRequestDiscoveryTrait.TrustExternalApproval) {
                    return (ForkPullRequestDiscoveryTrait.TrustExternalApproval) trust;
                }
            }
        }
        return null;
    }

    @CheckForNull
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String getCurrentPullHash(BranchProjectFactory factory, Job<?, ?> job) {
        SCMRevision revision = factory.getRevision(job);
        if (revision instanceof PullRequestSCMRevision) {
            return ((PullRequestSCMRevision) revision).getPullHash();
        }
        return null;
    }

    /**
     * Returns {@code true} when the PR author is on the auto-approval user list. This is just a
     * list check with no GitHub call, so it's safe to use from the scheduler.
     */
    static boolean isAutoApprovedUser(ExternalApprovalInfo info) {
        if (info.autoApprovalUsers == null || info.prAuthor == null) {
            return false;
        }
        for (String user : info.autoApprovalUsers) {
            // GitHub logins are case-insensitive.
            if (user.equalsIgnoreCase(info.prAuthor)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Decides whether a PR can be approved automatically, either because its author is on the user
     * list or because it carries one of the auto-approval labels. Checking labels costs one GitHub
     * call, so only call this when first creating the approval record, never from the scheduler.
     *
     * @param info the approval info
     * @return {@code true} if the PR should be auto-approved
     */
    static boolean evaluateAutoApproval(ExternalApprovalInfo info) {
        if (isAutoApprovedUser(info)) {
            return true;
        }
        if (info.autoApprovalLabels == null || info.autoApprovalLabels.isEmpty() || info.source == null) {
            return false;
        }
        GitHubSCMSource src = info.source;
        StandardCredentials credentials = Connector.lookupScanCredentials(
                info.context, src.getApiUri(), src.getCredentialsId(), src.getRepoOwner());
        GitHub github = null;
        try {
            github = Connector.connect(src.getApiUri(), credentials);
            GHRepository repo = github.getRepository(src.getRepoOwner() + "/" + src.getRepository());
            GHPullRequest pr = repo.getPullRequest(info.prNumber);
            for (GHLabel label : pr.getLabels()) {
                if (info.autoApprovalLabels.contains(label.getName())) {
                    return true;
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to check auto-approval labels for PR #" + info.prNumber, e);
        } finally {
            if (github != null) {
                Connector.release(github);
            }
        }
        return false;
    }
}

/**
 * A snapshot of a fork PR plus everything needed to decide its approval.
 */
class ExternalApprovalInfo {
    final int prNumber;
    final String prAuthor;
    final String currentPullHash;
    final boolean requireApprovalForNewCommits;

    @CheckForNull
    final List<String> autoApprovalUsers;

    @CheckForNull
    final List<String> autoApprovalLabels;

    @CheckForNull
    final GitHubSCMSource source;

    @CheckForNull
    final Item context;

    ExternalApprovalInfo(
            int prNumber,
            String prAuthor,
            String currentPullHash,
            boolean requireApprovalForNewCommits,
            @CheckForNull List<String> autoApprovalUsers,
            @CheckForNull List<String> autoApprovalLabels,
            @CheckForNull GitHubSCMSource source,
            @CheckForNull Item context) {
        this.prNumber = prNumber;
        this.prAuthor = prAuthor;
        this.currentPullHash = currentPullHash;
        this.requireApprovalForNewCommits = requireApprovalForNewCommits;
        this.autoApprovalUsers = autoApprovalUsers;
        this.autoApprovalLabels = autoApprovalLabels;
        this.source = source;
        this.context = context;
    }
}
