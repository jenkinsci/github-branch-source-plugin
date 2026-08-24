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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.model.Job;
import jenkins.branch.Branch;
import jenkins.branch.BranchProjectFactory;
import jenkins.branch.BranchSource;
import jenkins.branch.MultiBranchProject;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadOrigin;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.trait.SCMSourceTrait;

/**
 * Utility class to determine if a branch job requires external approval.
 */
final class ExternalApprovalHelper {

    private ExternalApprovalHelper() {}

    /**
     * Checks if the given job is a fork pull request in a MultiBranchProject configured
     * with {@link ForkPullRequestDiscoveryTrait.TrustExternalApproval}.
     *
     * @param job the job to check
     * @return approval info if external approval is required, {@code null} otherwise
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
        ForkPullRequestDiscoveryTrait.TrustExternalApproval trustPolicy = findTrustPolicy(mp);
        if (trustPolicy == null) {
            return null;
        }
        String currentPullHash = getCurrentPullHash(factory, job);
        return new ExternalApprovalInfo(
                prHead.getNumber(),
                prHead.getSourceOwner(),
                currentPullHash,
                trustPolicy.isRequireApprovalForNewCommits());
    }

    @CheckForNull
    @SuppressWarnings("rawtypes")
    private static ForkPullRequestDiscoveryTrait.TrustExternalApproval findTrustPolicy(MultiBranchProject mp) {
        for (Object src : mp.getSources()) {
            if (src instanceof BranchSource) {
                SCMSource source = ((BranchSource) src).getSource();
                if (source instanceof GitHubSCMSource) {
                    for (SCMSourceTrait trait : ((GitHubSCMSource) source).getTraits()) {
                        if (trait instanceof ForkPullRequestDiscoveryTrait) {
                            Object trust = ((ForkPullRequestDiscoveryTrait) trait).getTrust();
                            if (trust instanceof ForkPullRequestDiscoveryTrait.TrustExternalApproval) {
                                return (ForkPullRequestDiscoveryTrait.TrustExternalApproval) trust;
                            }
                        }
                    }
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
}

/**
 * Holds information about a fork PR that requires external approval.
 */
class ExternalApprovalInfo {
    final int prNumber;
    final String prAuthor;
    final String currentPullHash;
    final boolean requireApprovalForNewCommits;

    ExternalApprovalInfo(int prNumber, String prAuthor, String currentPullHash, boolean requireApprovalForNewCommits) {
        this.prNumber = prNumber;
        this.prAuthor = prAuthor;
        this.currentPullHash = currentPullHash;
        this.requireApprovalForNewCommits = requireApprovalForNewCommits;
    }
}
