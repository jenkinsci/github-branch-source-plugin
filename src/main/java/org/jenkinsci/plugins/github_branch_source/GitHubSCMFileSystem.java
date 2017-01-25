/*
 * The MIT License
 *
 * Copyright (c) 2016 CloudBees, Inc.
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
 *
 */

package org.jenkinsci.plugins.github_branch_source;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Item;
import hudson.scm.SCM;
import java.io.IOException;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.scm.api.SCMFile;
import jenkins.scm.api.SCMFileSystem;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.HttpException;

public class GitHubSCMFileSystem extends SCMFileSystem {
    private final GHRepository repo;
    private final String ref;

    protected GitHubSCMFileSystem(GHRepository repo, String ref, @CheckForNull SCMRevision rev) throws IOException {
        super(rev);
        this.repo = repo;
        if (rev != null) {
            if (rev.getHead() instanceof PullRequestSCMHead) {
                PullRequestSCMHead pr = (PullRequestSCMHead) rev.getHead();
                assert !pr.isMerge(); // TODO see below
                this.ref = ((PullRequestSCMRevision) rev).getPullHash();
            } else if (rev instanceof AbstractGitSCMSource.SCMRevisionImpl) {
                this.ref = ((AbstractGitSCMSource.SCMRevisionImpl) rev).getHash();
            } else {
                this.ref = ref;
            }
        } else {
            this.ref = ref;
        }
    }

    @Override
    public long lastModified() throws IOException {
        // TODO figure out how to implement this
        return 0L;
    }

    @NonNull
    @Override
    public SCMFile getRoot() {
        return new GitHubSCMFile(repo, ref);
    }

    @Extension
    public static class BuilderImpl extends SCMFileSystem.Builder {

        @Override
        public boolean supports(SCM source) {
            // TODO implement a GitHubSCM so we can work for those
            return false;
        }

        @Override
        public boolean supports(SCMSource source) {
            return source instanceof GitHubSCMSource;
        }

        @Override
        public SCMFileSystem build(@NonNull Item owner, @NonNull SCM scm, @CheckForNull SCMRevision rev) {
            return null;
        }

        @Override
        public SCMFileSystem build(@NonNull SCMSource source, @NonNull SCMHead head, @CheckForNull SCMRevision rev)
                throws IOException, InterruptedException {
            GitHubSCMSource src = (GitHubSCMSource) source;
            String apiUri = src.getApiUri();
            StandardCredentials credentials =
                    Connector.lookupScanCredentials(src.getOwner(), apiUri, src.getScanCredentialsId());

            // Github client and validation
            GitHub github = Connector.connect(apiUri, credentials);
            try {
                github.checkApiUrlValidity();
            } catch (HttpException e) {
                String message = String.format("It seems %s is unreachable",
                        apiUri == null ? GitHubSCMSource.GITHUB_URL : apiUri);
                throw new IOException(message);
            }
            String ref;
            if (head instanceof BranchSCMHead) {
                ref = head.getName();
            } else if (head instanceof PullRequestSCMHead) {
                PullRequestSCMHead pr = (PullRequestSCMHead) head;
                if (!pr.isMerge() && pr.getSourceRepo() != null) {
                    return new GitHubSCMFileSystem(
                            github.getUser(pr.getSourceOwner()).getRepository(pr.getSourceRepo()),
                            pr.getSourceBranch(),
                            rev);
                }
                return null; // TODO support merge revisions somehow
            } else {
                return null;
            }

            GHRepository repo = github.getUser(src.getRepoOwner()).getRepository(src.getRepository());
            if (rev == null) {
                rev = new AbstractGitSCMSource.SCMRevisionImpl((BranchSCMHead) head, repo.getBranch(ref).getSHA1());
            }
            return new GitHubSCMFileSystem(repo, ref, rev);
        }
    }
}
