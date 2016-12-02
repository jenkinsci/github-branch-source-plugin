/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
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
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.mixin.ChangeRequestSCMHead;
import org.kohsuke.github.GHPullRequest;

/**
 * Head corresponding to a pull request.
 * Named like {@code PR-123} or {@code PR-123-merged} or {@code PR-123-unmerged}.
 */
public final class PullRequestSCMHead extends SCMHead implements ChangeRequestSCMHead {

    private static final long serialVersionUID = 1;

    private Boolean merge;
    private final int number;
    private final BranchSCMHead target;
    private final String sourceOwner;
    private final String sourceRepo;
    private final String sourceBranch;

    PullRequestSCMHead(GHPullRequest pr, String name, boolean merge) {
        super(name);
        // the merge flag is encoded into the name, so safe to store here
        this.merge = merge;
        this.number = pr.getNumber();
        this.target = new BranchSCMHead(pr.getBase().getRef());
        // the source stuff is immutable for a pull request on github, so safe to store here
        this.sourceOwner = pr.getHead().getRepository().getOwnerName();
        this.sourceRepo = pr.getHead().getRepository().getName();
        this.sourceBranch = pr.getHead().getRef();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getPronoun() {
        return Messages.PullRequestSCMHead_Pronoun();
    }

    public int getNumber() {
        return number;
    }

    /** Default for old settings. */
    private Object readResolve() {
        if (merge == null) {
            merge = true;
        }
        // leave trusted at false to be on the safe side
        return this;
    }

    /**
     * Whether we intend to build the merge of the PR head with the base branch.
     * 
     */
    public boolean isMerge() {
        return merge;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public String getId() {
        return Integer.toString(number);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public SCMHead getTarget() {
        return target;
    }

    public String getSourceOwner() {
        return sourceOwner;
    }

    public String getSourceBranch() {
        return sourceBranch;
    }

    public String getSourceRepo() {
        return sourceRepo;
    }
}
