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
import hudson.Extension;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadMigration;
import jenkins.scm.api.SCMRevision;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Head corresponding to a branch.
 *
 * @since 2.0.0
 */
public class BranchSCMHead extends SCMHead {
    /** {@inheritDoc} */
    public BranchSCMHead(@NonNull String name) {
        super(name);
    }

    /** {@inheritDoc} */
    @Override
    public String getPronoun() {
        return Messages.BranchSCMHead_Pronoun();
    }

    @Restricted(NoExternalUse.class)
    @Extension
    public static class MigrationImpl
            extends SCMHeadMigration<GitHubSCMSource, SCMHead, AbstractGitSCMSource.SCMRevisionImpl> {
        public MigrationImpl() {
            super(GitHubSCMSource.class, SCMHead.class, AbstractGitSCMSource.SCMRevisionImpl.class);
        }

        @Override
        public SCMHead migrate(@NonNull GitHubSCMSource source, @NonNull SCMHead head) {
            return new BranchSCMHead(head.getName());
        }

        @Override
        public SCMRevision migrate(
                @NonNull GitHubSCMSource source, @NonNull AbstractGitSCMSource.SCMRevisionImpl revision) {
            return new AbstractGitSCMSource.SCMRevisionImpl(migrate(source, revision.getHead()), revision.getHash());
        }
    }
}
