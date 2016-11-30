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

import hudson.model.Action;
import java.util.LinkedList;
import java.util.List;
import jenkins.scm.api.SCMHead;
import org.kohsuke.github.GHPullRequest;

/**
 * Head corresponding to a pull request.
 * Named like {@code PR-123} or {@code PR-123-merged} or {@code PR-123-unmerged}.
 */
public final class PullRequestSCMHead extends SCMHead {

    private static final long serialVersionUID = 1;

    private final PullRequestAction metadata;
    private Boolean merge;
    private final boolean trusted;
    private final boolean commentOnBuildFailure;


    PullRequestSCMHead(GHPullRequest pr, String name, boolean merge, boolean trusted, boolean commentOnBuildFailure) {
        super(name);
        metadata = new PullRequestAction(pr);
        this.merge = merge;
        this.trusted = trusted;
        this.commentOnBuildFailure = commentOnBuildFailure;
    }

    PullRequestSCMHead(GHPullRequest pr, String name, boolean merge, boolean trusted) {
        this(pr, name, merge, trusted, false);
    }

    public int getNumber() {
        if (metadata != null) {
            return Integer.parseInt(metadata.getId());
        } else { // settings compatibility
            // if predating PullRequestAction, then also predate -merged/-unmerged suffices
            return Integer.parseInt(getName().substring("PR-".length()));
        }
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
     * Whether this PR was observed to have come from a trusted author.
     */
    public boolean isTrusted() {
        return trusted;
    }

    /**
     * Whether this PR needs notification comment on build failure
     */
    public boolean needsCommentOnBuildFailure() { return commentOnBuildFailure; }

    @Override
    public List<? extends Action> getAllActions() {
        List<Action> actions = new LinkedList<Action>(super.getAllActions());
        actions.add(metadata);
        return actions;
    }

}
