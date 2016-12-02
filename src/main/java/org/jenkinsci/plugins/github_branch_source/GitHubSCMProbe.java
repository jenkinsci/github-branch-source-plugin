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

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.scm.api.SCMFile;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMProbe;
import jenkins.scm.api.SCMProbeStat;
import jenkins.scm.api.SCMRevision;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRef;
import org.kohsuke.github.GHRepository;

class GitHubSCMProbe extends SCMProbe {
    private static final long serialVersionUID = 1L;
    private final SCMRevision revision;
    private final transient GHRepository repo;
    private final String ref;
    private final String name;

    public GitHubSCMProbe(GHRepository repo, SCMHead head, SCMRevision revision) {
        this.revision = revision;
        this.repo = repo;
        this.name = head.getName();
        if (head instanceof PullRequestSCMHead) {
            PullRequestSCMHead pr = (PullRequestSCMHead) head;
            this.ref = "refs/pull/" + pr.getNumber() + (pr.isMerge() ? "/merge" : "/head");
        } else {
            this.ref = "refs/heads/" + head.getName();
        }
    }

    @Override
    public void close() throws IOException {
        // no-op as the GHRepository does not keep a persistent connection
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
        if (repo == null) {
            throw new IOException("No connection available");
        }
        try {
            int index = path.lastIndexOf('/') + 1;
            List<GHContent> directoryContent = repo.getDirectoryContent(path.substring(0, index), ref);
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
        String ref;
        if (revision != null) {
            if (revision.getHead() instanceof PullRequestSCMHead) {
                ref = this.ref;
            } else if (revision instanceof AbstractGitSCMSource.SCMRevisionImpl){
                ref = ((AbstractGitSCMSource.SCMRevisionImpl) revision).getHash();
            } else {
                ref = this.ref;
            }
        } else {
            ref = this.ref;
        }
        return new GitHubSCMFile(repo, ref);
    }

}
