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
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.logging.Logger;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.mixin.ChangeRequestSCMHead;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.github.GHPullRequest;

/**
 * Head corresponding to a pull request.
 * Named like {@code PR-123} or {@code PR-123-merged} or {@code PR-123-unmerged}.
 */
public final class PullRequestSCMHead extends SCMHead implements ChangeRequestSCMHead {

    private static final Logger LOGGER = Logger.getLogger(PullRequestSCMHead.class.getName());

    private static final long serialVersionUID = 1;

    private Boolean merge;
    private final int number;
    private final BranchSCMHead target;
    private final String sourceOwner;
    private final String sourceRepo;
    private final String sourceBranch;
    /**
     * Only populated if de-serializing instances.
     */
    private transient Metadata metadata;

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

    PullRequestSCMHead(@NonNull String name, boolean merge, int number,
                       BranchSCMHead target, String sourceOwner, String sourceRepo, String sourceBranch) {
        super(name);
        this.merge = merge;
        this.number = number;
        this.target = target;
        this.sourceOwner = sourceOwner;
        this.sourceRepo = sourceRepo;
        this.sourceBranch = sourceBranch;
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

    /**
     * Default for old settings.
     */
    @SuppressFBWarnings("SE_PRIVATE_READ_RESOLVE_NOT_INHERITED") // because JENKINS-41453
    private Object readResolve() {
        if (merge == null) {
            merge = true;
        }
        if (metadata != null) {
            // the source branch info is missing, thankfully, the missing information is not part of the key
            // so we can just use dummy values and this should only affect SCMFileSystem API. Once
            // there is an index, the head will be replaced with Branch API 2.0.x and it should all go away
            return new PullRequestSCMHead(
                    getName(),
                    merge,
                    metadata.getNumber(),
                    new BranchSCMHead(metadata.getBaseRef()),
                    null,
                    null,
                    null
            );
        }
        return this;
    }

    /**
     * Whether we intend to build the merge of the PR head with the base branch.
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

    /**
     * Holds legacy data so we can recover the details.
     */
    private static class Metadata {
        private final int number;
        private final String url;
        private final String userLogin;
        private final String baseRef;

        public Metadata(int number, String url, String userLogin, String baseRef) {
            this.number = number;
            this.url = url;
            this.userLogin = userLogin;
            this.baseRef = baseRef;
        }

        public int getNumber() {
            return number;
        }

        public String getUrl() {
            return url;
        }

        public String getUserLogin() {
            return userLogin;
        }

        public String getBaseRef() {
            return baseRef;
        }
    }
}
