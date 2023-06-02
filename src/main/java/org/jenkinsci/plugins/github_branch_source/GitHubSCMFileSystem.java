/*
 * The MIT License
 *
 * Copyright (c) 2016-2017 CloudBees, Inc.
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
import hudson.plugins.git.GitSCM;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.plugins.git.GitTagSCMRevision;
import jenkins.scm.api.SCMFile;
import jenkins.scm.api.SCMFileSystem;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceDescriptor;
import org.apache.commons.lang.time.FastDateFormat;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHRef;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTagObject;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;

/** Implements {@link SCMFileSystem} for GitHub. */
public class GitHubSCMFileSystem extends SCMFileSystem implements GitHubClosable {
    private final GitHub gitHub;
    private final GHRepository repo;
    private final String ref;
    private boolean open;

    /**
     * Constructor.
     *
     * @param gitHub the {@link GitHub}
     * @param repo the {@link GHRepository}
     * @param refName the ref name, e.g. {@code heads/branchName}, {@code tags/tagName}, {@code
     *     pull/N/head} or the SHA.
     * @param rev the optional revision.
     * @throws IOException if I/O errors occur.
     */
    protected GitHubSCMFileSystem(GitHub gitHub, GHRepository repo, String refName, @CheckForNull SCMRevision rev)
            throws IOException {
        super(rev);
        this.gitHub = gitHub;
        this.open = true;
        this.repo = repo;
        if (rev != null) {
            if (rev.getHead() instanceof PullRequestSCMHead) {
                PullRequestSCMRevision prRev = (PullRequestSCMRevision) rev;
                PullRequestSCMHead pr = (PullRequestSCMHead) prRev.getHead();
                if (pr.isMerge()) {
                    this.ref = prRev.getMergeHash();
                } else {
                    this.ref = prRev.getPullHash();
                }
            } else if (rev instanceof AbstractGitSCMSource.SCMRevisionImpl) {
                this.ref = ((AbstractGitSCMSource.SCMRevisionImpl) rev).getHash();
            } else {
                this.ref = refName;
            }
        } else {
            this.ref = refName;
        }
    }

    /** {@inheritDoc} */
    @Override
    public void close() throws IOException {
        synchronized (this) {
            if (!open) {
                return;
            }
            open = false;
        }
        Connector.release(gitHub);
    }

    /** {@inheritDoc} */
    @Override
    public synchronized boolean isOpen() {
        return open;
    }

    /** {@inheritDoc} */
    @Override
    public long lastModified() throws IOException {
        return repo.getCommit(ref).getCommitDate().getTime();
    }

    /** {@inheritDoc} */
    @Override
    public boolean changesSince(SCMRevision revision, @NonNull OutputStream changeLogStream)
            throws UnsupportedOperationException, IOException, InterruptedException {
        if (Objects.equals(getRevision(), revision)) {
            // special case where somebody is asking one of two stupid questions:
            // 1. what has changed between the latest and the latest
            // 2. what has changed between the current revision and the current revision
            return false;
        }
        int count = 0;
        FastDateFormat iso = FastDateFormat.getInstance("yyyy-MM-dd'T'HH:mm:ssZ");
        StringBuilder log = new StringBuilder(1024);
        String endHash;
        if (revision instanceof AbstractGitSCMSource.SCMRevisionImpl) {
            endHash =
                    ((AbstractGitSCMSource.SCMRevisionImpl) revision).getHash().toLowerCase(Locale.ENGLISH);
        } else {
            endHash = null;
        }
        // this is the format expected by GitSCM, so we need to format each GHCommit with the same
        // format
        // commit %H%ntree %T%nparent %P%nauthor %aN <%aE> %ai%ncommitter %cN <%cE>
        // %ci%n%n%w(76,4,4)%s%n%n%b
        for (GHCommit commit :
                repo.queryCommits().from(ref).pageSize(GitSCM.MAX_CHANGELOG).list()) {
            if (commit.getSHA1().toLowerCase(Locale.ENGLISH).equals(endHash)) {
                break;
            }
            log.setLength(0);
            log.append("commit ").append(commit.getSHA1()).append('\n');
            log.append("tree ").append(commit.getTree().getSha()).append('\n');
            log.append("parent");
            for (String parent : commit.getParentSHA1s()) {
                log.append(' ').append(parent);
            }
            log.append('\n');
            GHCommit.ShortInfo info = commit.getCommitShortInfo();
            log.append("author ")
                    .append(info.getAuthor().getName())
                    .append(" <")
                    .append(info.getAuthor().getEmail())
                    .append("> ")
                    .append(iso.format(info.getAuthoredDate()))
                    .append('\n');
            log.append("committer ")
                    .append(info.getCommitter().getName())
                    .append(" <")
                    .append(info.getCommitter().getEmail())
                    .append("> ")
                    .append(iso.format(info.getCommitDate()))
                    .append('\n');
            log.append('\n');
            String msg = info.getMessage();
            if (msg.endsWith("\r\n")) {
                msg = msg.substring(0, msg.length() - 2);
            } else if (msg.endsWith("\n")) {
                msg = msg.substring(0, msg.length() - 1);
            }
            msg = msg.replace("\r\n", "\n").replace("\r", "\n").replace("\n", "\n    ");
            log.append("    ").append(msg).append('\n');
            changeLogStream.write(log.toString().getBytes(StandardCharsets.UTF_8));
            changeLogStream.flush();
            count++;
            if (count >= GitSCM.MAX_CHANGELOG) {
                break;
            }
        }
        return count > 0;
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public SCMFile getRoot() {
        return new GitHubSCMFile(this, repo, ref);
    }

    @Extension
    public static class BuilderImpl extends SCMFileSystem.Builder {

        /** {@inheritDoc} */
        @Override
        public boolean supports(SCM source) {
            // TODO implement a GitHubSCM so we can work for those
            return false;
        }

        @Override
        protected boolean supportsDescriptor(SCMDescriptor scmDescriptor) {
            // TODO implement a GitHubSCM so we can work for those
            return false;
        }

        /** {@inheritDoc} */
        @Override
        public boolean supports(SCMSource source) {
            return source instanceof GitHubSCMSource;
        }

        @Override
        protected boolean supportsDescriptor(SCMSourceDescriptor scmSourceDescriptor) {
            return scmSourceDescriptor instanceof GitHubSCMSource.DescriptorImpl;
        }

        /** {@inheritDoc} */
        @Override
        public SCMFileSystem build(@NonNull Item owner, @NonNull SCM scm, @CheckForNull SCMRevision rev) {
            return null;
        }

        /** {@inheritDoc} */
        @Override
        public SCMFileSystem build(@NonNull SCMSource source, @NonNull SCMHead head, @CheckForNull SCMRevision rev)
                throws IOException, InterruptedException {
            GitHubSCMSource src = (GitHubSCMSource) source;
            String apiUri = src.getApiUri();
            StandardCredentials credentials = Connector.lookupScanCredentials(
                    (Item) src.getOwner(), apiUri, src.getScanCredentialsId(), src.getRepoOwner());

            // Github client and validation
            GitHub github = Connector.connect(apiUri, credentials);
            try {
                String refName;

                if (head instanceof BranchSCMHead) {
                    refName = "heads/" + head.getName();
                } else if (head instanceof GitHubTagSCMHead) {
                    refName = "tags/" + head.getName();
                } else if (head instanceof PullRequestSCMHead) {
                    refName = null;
                    if (rev instanceof PullRequestSCMRevision) {
                        PullRequestSCMRevision prRev = (PullRequestSCMRevision) rev;
                        if (((PullRequestSCMHead) head).isMerge()) {
                            if (prRev.getMergeHash() == null) {
                                return null;
                            }
                            prRev.validateMergeHash();
                        }
                    } else {
                        return null;
                    }
                } else {
                    return null;
                }

                GHUser user = github.getUser(src.getRepoOwner());
                if (user == null) {
                    return null;
                }
                GHRepository repo = user.getRepository(src.getRepository());
                if (repo == null) {
                    return null;
                }

                if (rev == null) {
                    GHRef ref = repo.getRef(refName);
                    if ("tag".equalsIgnoreCase(ref.getObject().getType())) {
                        GHTagObject tag = repo.getTagObject(ref.getObject().getSha());
                        if (head instanceof GitHubTagSCMHead) {
                            rev = new GitTagSCMRevision(
                                    (GitHubTagSCMHead) head, tag.getObject().getSha());
                        } else {
                            // we should never get here, but just in case, we have the information to construct
                            // the correct head, so let's do that
                            rev = new GitTagSCMRevision(
                                    new GitHubTagSCMHead(
                                            head.getName(),
                                            tag.getTagger().getDate().getTime()),
                                    tag.getObject().getSha());
                        }
                    } else {
                        rev = new AbstractGitSCMSource.SCMRevisionImpl(
                                head, ref.getObject().getSha());
                    }
                }

                // Instead of calling release in many case and skipping this one case
                // Make another call to connect() for this case
                // and always release the existing instance as part of finally block.
                // The result is the same but with far fewer code paths calling release().
                GitHub fileSystemGitHub = Connector.connect(apiUri, credentials);
                return new GitHubSCMFileSystem(fileSystemGitHub, repo, refName, rev);
            } catch (IOException | RuntimeException e) {
                throw e;
            } finally {
                Connector.release(github);
            }
        }
    }
}
