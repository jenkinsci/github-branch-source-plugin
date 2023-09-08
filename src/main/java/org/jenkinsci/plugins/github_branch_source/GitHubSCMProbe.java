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
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.scm.api.SCMFile;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMProbe;
import jenkins.scm.api.SCMProbeStat;
import jenkins.scm.api.SCMRevision;
import org.eclipse.jgit.lib.Constants;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRef;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

@SuppressFBWarnings("SE_TRANSIENT_FIELD_NOT_RESTORED")
class GitHubSCMProbe extends SCMProbe implements GitHubClosable {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(GitHubSCMProbe.class.getName());
    private final SCMRevision revision;
    private final transient GitHub gitHub;
    private final transient GHRepository repo;
    private final String ref;
    private final String name;
    private transient boolean open = true;

    public GitHubSCMProbe(
            String apiUri, StandardCredentials credentials, GHRepository repo, SCMHead head, SCMRevision revision)
            throws IOException {
        this.gitHub = Connector.connect(apiUri, credentials);
        this.revision = revision;
        this.repo = repo;
        this.name = head.getName();
        if (head instanceof PullRequestSCMHead) {
            PullRequestSCMHead pr = (PullRequestSCMHead) head;
            this.ref = "pull/" + pr.getNumber() + (pr.isMerge() ? "/merge" : "/head");
        } else if (head instanceof GitHubTagSCMHead) {
            this.ref = "tags/" + head.getName();
        } else {
            this.ref = "heads/" + head.getName();
        }
    }

    @Override
    public void close() throws IOException {
        if (gitHub == null || repo == null) {
            return;
        }
        synchronized (this) {
            if (!open) {
                return;
            }
            open = false;
        }
        Connector.release(gitHub);
    }

    private synchronized void checkOpen() throws IOException {
        if (!open) {
            throw new IOException("Closed");
        }
        if (repo == null) {
            throw new IOException("No connection available");
        }
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public long lastModified() {
        if (repo == null) {
            return 0L;
        }
        synchronized (this) {
            if (!open) {
                return 0L;
            }
        }
        if (revision instanceof AbstractGitSCMSource.SCMRevisionImpl) {
            try {
                GHCommit commit = repo.getCommit(((AbstractGitSCMSource.SCMRevisionImpl) revision).getHash());
                return commit.getCommitDate().getTime();
            } catch (IOException e) {
                // ignore
            }
        } else if (revision == null) {
            try {
                GHRef ref = repo.getRef(this.ref);
                GHCommit commit = repo.getCommit(ref.getObject().getSha());
                return commit.getCommitDate().getTime();
            } catch (IOException e) {
                // ignore
            }
        }
        return 0;
    }

    @NonNull
    @Override
    public SCMProbeStat stat(@NonNull String path) throws IOException {
        checkOpen();
        try {
            int index = path.lastIndexOf('/') + 1;
            List<GHContent> directoryContent =
                    repo.getDirectoryContent(path.substring(0, index), Constants.R_REFS + ref);
            for (GHContent content : directoryContent) {
                if (content.getPath().equals(path)) {
                    if (content.isFile()) {
                        return SCMProbeStat.fromType(SCMFile.Type.REGULAR_FILE);
                    } else if (content.isDirectory()) {
                        return SCMProbeStat.fromType(SCMFile.Type.DIRECTORY);
                    } else if ("symlink".equals(content.getType())) {
                        return SCMProbeStat.fromType(SCMFile.Type.LINK);
                    } else {
                        return SCMProbeStat.fromType(SCMFile.Type.OTHER);
                    }
                }
            }
            for (GHContent content : directoryContent) {
                if (content.getPath().equalsIgnoreCase(path)) {
                    return SCMProbeStat.fromAlternativePath(content.getPath());
                }
            }
        } catch (FileNotFoundException fnf) {
            // means that does not exist and this is handled below this try/catch block.
        }
        return SCMProbeStat.fromType(SCMFile.Type.NONEXISTENT);
    }

    @Override
    public SCMFile getRoot() {
        if (repo == null) {
            return null;
        }
        synchronized (this) {
            if (!open) {
                return null;
            }
        }
        String ref;
        if (revision != null) {
            if (revision.getHead() instanceof PullRequestSCMHead) {
                ref = this.ref;
            } else if (revision instanceof AbstractGitSCMSource.SCMRevisionImpl) {
                ref = ((AbstractGitSCMSource.SCMRevisionImpl) revision).getHash();
            } else {
                ref = this.ref;
            }
        } else {
            ref = this.ref;
        }
        return new GitHubSCMFile(this, repo, ref);
    }

    @Override
    public synchronized boolean isOpen() {
        return open;
    }
}
