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

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadMigration;
import jenkins.scm.api.SCMHeadOrigin;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import jenkins.scm.api.mixin.ChangeRequestSCMHead2;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;

/**
 * Head corresponding to a pull request. Named like {@code PR-123} or {@code PR-123-merge} or {@code
 * PR-123-head}.
 */
public class PullRequestSCMHead extends SCMHead implements ChangeRequestSCMHead2 {

    private static final Logger LOGGER = Logger.getLogger(PullRequestSCMHead.class.getName());
    private static final AtomicBoolean UPGRADE_SKIPPED_2_0_X = new AtomicBoolean(false);

    private static final long serialVersionUID = 1;

    private Boolean merge;
    private final int number;
    private final BranchSCMHead target;
    private final String sourceOwner;
    private final String sourceRepo;
    private final String sourceBranch;
    private final SCMHeadOrigin origin;
    /** Only populated if de-serializing instances. */
    private transient Metadata metadata;

    PullRequestSCMHead(PullRequestSCMHead copy) {
        super(copy.getName());
        this.merge = copy.merge;
        this.number = copy.number;
        this.target = copy.target;
        this.sourceOwner = copy.sourceOwner;
        this.sourceRepo = copy.sourceRepo;
        this.sourceBranch = copy.sourceBranch;
        this.origin = copy.origin;
        this.metadata = copy.metadata;
    }

    PullRequestSCMHead(GHPullRequest pr, String name, boolean merge) {
        super(name);
        // the merge flag is encoded into the name, so safe to store here
        this.merge = merge;
        this.number = pr.getNumber();
        this.target = new BranchSCMHead(pr.getBase().getRef());
        // the source stuff is immutable for a pull request on github, so safe to store here
        GHRepository repository = pr.getHead().getRepository(); // may be null for deleted forks JENKINS-41246
        this.sourceOwner = repository == null ? null : repository.getOwnerName();
        this.sourceRepo = repository == null ? null : repository.getName();
        this.sourceBranch = pr.getHead().getRef();

        if (pr.getRepository().getOwnerName().equalsIgnoreCase(sourceOwner)) {
            this.origin = SCMHeadOrigin.DEFAULT;
        } else {
            // if the forked repo name differs from the upstream repo name
            this.origin = pr.getBase().getRepository().getName().equalsIgnoreCase(sourceRepo)
                    ? new SCMHeadOrigin.Fork(this.sourceOwner)
                    : new SCMHeadOrigin.Fork(repository == null ? this.sourceOwner : repository.getFullName());
        }
    }

    public PullRequestSCMHead(
            @NonNull String name,
            String sourceOwner,
            String sourceRepo,
            String sourceBranch,
            int number,
            BranchSCMHead target,
            SCMHeadOrigin origin,
            ChangeRequestCheckoutStrategy strategy) {
        super(name);
        this.merge = ChangeRequestCheckoutStrategy.MERGE == strategy;
        this.number = number;
        this.target = target;
        this.sourceOwner = sourceOwner;
        this.sourceRepo = sourceRepo;
        this.sourceBranch = sourceBranch;
        this.origin = origin;
    }

    /** {@inheritDoc} */
    @Override
    public String getPronoun() {
        return Messages.PullRequestSCMHead_Pronoun();
    }

    public int getNumber() {
        return number;
    }

    /**
     * Default for old settings.
     *
     * @return the deserialized object.
     */
    @SuppressFBWarnings("SE_PRIVATE_READ_RESOLVE_NOT_INHERITED") // because JENKINS-41453
    private Object readResolve() {
        if (merge == null) {
            merge = true;
        }
        if (metadata != null) {
            // Upgrade from 1.x:
            if (UPGRADE_SKIPPED_2_0_X.compareAndSet(false, true)) {
                LOGGER.log(
                        Level.WARNING,
                        "GitHub Branch Source plugin was directly upgraded from 1.x to 2.2.0 "
                                + "or newer without completing a full fetch from all repositories. Consequently startup may be "
                                + "delayed while GitHub is queried for the missing information");
            }
            // we need the help of FixMetadataMigration
            return new FixMetadata(getName(), merge, metadata.getNumber(), new BranchSCMHead(metadata.getBaseRef()));
        }
        if (origin == null && !(this instanceof FixOrigin)) {
            // Upgrade from 2.0.x

            // we need the help of FixOriginMigration
            return new FixOrigin(this);
        }
        return this;
    }

    /**
     * Whether we intend to build the merge of the PR head with the base branch.
     *
     * @return {@code true} if this is a merge PR head.
     */
    public boolean isMerge() {
        return merge;
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public ChangeRequestCheckoutStrategy getCheckoutStrategy() {
        return merge ? ChangeRequestCheckoutStrategy.MERGE : ChangeRequestCheckoutStrategy.HEAD;
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public String getId() {
        return Integer.toString(number);
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public BranchSCMHead getTarget() {
        return target;
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public String getOriginName() {
        return sourceBranch;
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

    @NonNull
    @Override
    public SCMHeadOrigin getOrigin() {
        return origin == null ? SCMHeadOrigin.DEFAULT : origin;
    }

    /** Holds legacy data so we can recover the details. */
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

    /**
     * Used to handle data migration.
     *
     * @see FixOriginMigration
     * @deprecated used for data migration.
     */
    @Deprecated
    @Restricted(NoExternalUse.class)
    public static class FixOrigin extends PullRequestSCMHead {

        FixOrigin(PullRequestSCMHead pullRequestSCMHead) {
            super(pullRequestSCMHead);
        }
    }

    /**
     * Used to handle data migration.
     *
     * @see FixOriginMigration
     * @deprecated used for data migration.
     */
    @Restricted(NoExternalUse.class)
    @Extension
    public static class FixOriginMigration
            extends SCMHeadMigration<GitHubSCMSource, FixOrigin, PullRequestSCMRevision> {
        public FixOriginMigration() {
            super(GitHubSCMSource.class, FixOrigin.class, PullRequestSCMRevision.class);
        }

        @Override
        public PullRequestSCMHead migrate(@NonNull GitHubSCMSource source, @NonNull FixOrigin head) {
            return new PullRequestSCMHead(
                    head.getName(),
                    head.getSourceOwner(),
                    head.getSourceRepo(),
                    head.getSourceBranch(),
                    head.getNumber(),
                    head.getTarget(),
                    source.getRepoOwner().equalsIgnoreCase(head.getSourceOwner())
                            ? SCMHeadOrigin.DEFAULT
                            : new SCMHeadOrigin.Fork(head.getSourceOwner()),
                    head.getCheckoutStrategy());
        }

        @Override
        public SCMRevision migrate(@NonNull GitHubSCMSource source, @NonNull PullRequestSCMRevision revision) {
            PullRequestSCMHead head = migrate(source, (FixOrigin) revision.getHead());
            return head != null
                    ? new PullRequestSCMRevision(head, revision.getBaseHash(), revision.getPullHash())
                    : null;
        }
    }

    /**
     * Used to handle data migration.
     *
     * @see FixMetadataMigration
     * @deprecated used for data migration.
     */
    @Deprecated
    @Restricted(NoExternalUse.class)
    public static class FixMetadata extends PullRequestSCMHead {
        FixMetadata(String name, Boolean merge, int number, BranchSCMHead branchSCMHead) {
            super(
                    name,
                    null,
                    null,
                    null,
                    number,
                    branchSCMHead,
                    null,
                    merge ? ChangeRequestCheckoutStrategy.MERGE : ChangeRequestCheckoutStrategy.HEAD);
        }
    }

    /**
     * Used to handle data migration.
     *
     * @see FixOriginMigration
     * @deprecated used for data migration.
     */
    @Restricted(NoExternalUse.class)
    @Extension
    public static class FixMetadataMigration
            extends SCMHeadMigration<GitHubSCMSource, FixMetadata, PullRequestSCMRevision> {
        public FixMetadataMigration() {
            super(GitHubSCMSource.class, FixMetadata.class, PullRequestSCMRevision.class);
        }

        @Override
        public PullRequestSCMHead migrate(@NonNull GitHubSCMSource source, @NonNull FixMetadata head) {
            PullRequestSource src = source.retrievePullRequestSource(head.getNumber());
            return new PullRequestSCMHead(
                    head.getName(),
                    src == null ? null : src.getSourceOwner(),
                    src == null ? null : src.getSourceRepo(),
                    src == null ? null : src.getSourceBranch(),
                    head.getNumber(),
                    head.getTarget(),
                    src != null && source.getRepoOwner().equalsIgnoreCase(src.getSourceOwner())
                            ? SCMHeadOrigin.DEFAULT
                            : new SCMHeadOrigin.Fork(head.getSourceOwner()),
                    head.getCheckoutStrategy());
        }

        @Override
        public SCMRevision migrate(@NonNull GitHubSCMSource source, @NonNull PullRequestSCMRevision revision) {
            PullRequestSCMHead head = migrate(source, (FixMetadata) revision.getHead());
            return head != null
                    ? new PullRequestSCMRevision(head, revision.getBaseHash(), revision.getPullHash())
                    : null;
        }
    }
}
