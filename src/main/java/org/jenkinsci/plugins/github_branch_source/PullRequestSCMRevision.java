/*
 * The MIT License
 *
 * Copyright 2016-2017 CloudBees, Inc.
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
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.AbortException;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import jenkins.scm.api.mixin.ChangeRequestSCMHead2;
import jenkins.scm.api.mixin.ChangeRequestSCMRevision;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.export.Exported;

/** Revision of a pull request. */
public class PullRequestSCMRevision extends ChangeRequestSCMRevision<PullRequestSCMHead> {

    private static final long serialVersionUID = 1L;
    static final String NOT_MERGEABLE_HASH = "NOT_MERGEABLE";

    private final @NonNull String baseHash;
    private final @NonNull String pullHash;
    private final String mergeHash;

    public PullRequestSCMRevision(
            @NonNull PullRequestSCMHead head, @NonNull String baseHash, @NonNull String pullHash) {
        this(head, baseHash, pullHash, null);
    }

    PullRequestSCMRevision(
            @NonNull PullRequestSCMHead head, @NonNull String baseHash, @NonNull String pullHash, String mergeHash) {
        super(head, new AbstractGitSCMSource.SCMRevisionImpl(head.getTarget(), baseHash));
        this.baseHash = baseHash;
        this.pullHash = pullHash;
        this.mergeHash = mergeHash;
    }

    @SuppressFBWarnings({"SE_PRIVATE_READ_RESOLVE_NOT_INHERITED", "RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE"})
    private Object readResolve() {
        if (getTarget() == null) {
            // fix an instance prior to the type migration, thankfully we have all the required info
            return new PullRequestSCMRevision((PullRequestSCMHead) getHead(), baseHash, pullHash);
        }
        return this;
    }

    /**
     * The commit hash of the base branch we are tracking. If {@link
     * ChangeRequestSCMHead2#getCheckoutStrategy()} {@link ChangeRequestCheckoutStrategy#MERGE}, this
     * would be the current head of the base branch. Otherwise it would be the PR’s {@code .base.sha},
     * the common ancestor of the PR branch and the base branch.
     *
     * @return the commit hash of the base branch we are tracking.
     */
    @NonNull
    public String getBaseHash() {
        return baseHash;
    }

    /**
     * The commit hash of the head of the pull request branch.
     *
     * @return The commit hash of the head of the pull request branch
     */
    @Exported
    @NonNull
    public String getPullHash() {
        return pullHash;
    }

    /**
     * The commit hash of the head of the pull request branch.
     *
     * @return The commit hash of the head of the pull request branch
     */
    @CheckForNull
    public String getMergeHash() {
        return mergeHash;
    }

    void validateMergeHash() throws AbortException {
        if (NOT_MERGEABLE_HASH.equals(this.mergeHash)) {
            throw new AbortException("Pull request "
                    + ((PullRequestSCMHead) this.getHead()).getNumber()
                    + " : Not mergeable at "
                    + this.toString());
        }
    }

    @Override
    public boolean equivalent(ChangeRequestSCMRevision<?> o) {
        if (!(o instanceof PullRequestSCMRevision)) {
            return false;
        }
        PullRequestSCMRevision other = (PullRequestSCMRevision) o;

        // JENKINS-57583 - Equivalent is used to make decisions about when to build.
        // mergeHash is an implementation detail of github, generated from base and target
        // If only mergeHash changes we do not consider it a different revision
        return getHead().equals(other.getHead()) && pullHash.equals(other.pullHash);
    }

    @Override
    public int _hashCode() {
        return pullHash.hashCode();
    }

    @Override
    public String toString() {
        String result = pullHash;
        if (getHead() instanceof PullRequestSCMHead && ((PullRequestSCMHead) getHead()).isMerge()) {
            result += "+" + baseHash + " (" + StringUtils.defaultIfBlank(mergeHash, "UNKNOWN_MERGE_STATE") + ")";
        }
        return result;
    }
}
