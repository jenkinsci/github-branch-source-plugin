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
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.scm.api.SCMFile;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMProbe;
import jenkins.scm.api.SCMProbeStat;
import jenkins.scm.api.SCMRevision;
import org.eclipse.jgit.lib.Constants;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHRef;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;


@SuppressFBWarnings("SE_TRANSIENT_FIELD_NOT_RESTORED")
class GitHubSCMProbe extends SCMProbe implements GitHubClosable {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(GitHubSCMProbe.class.getName());
    static /*mostly final*/ boolean JENKINS_54126_WORKAROUND = Boolean.parseBoolean(System.getProperty(GitHubSCMProbe.class.getName() + ".JENKINS_54126_WORKAROUND", Boolean.FALSE.toString()));
    static /*mostly final*/ boolean STAT_RETHROW_API_FNF = Boolean.parseBoolean(System.getProperty(GitHubSCMProbe.class.getName() + ".STAT_RETHROW_API_FNF", Boolean.TRUE.toString()));
    private final SCMRevision revision;
    private final transient GitHub gitHub;
    private final transient GHRepository repo;
    private final String ref;
    private final String name;
    private transient boolean open = true;

    public GitHubSCMProbe(GitHub github, GHRepository repo, SCMHead head, SCMRevision revision) {
        this.gitHub = github;
        this.revision = revision;
        this.repo = repo;
        this.name = head.getName();
        if (head instanceof PullRequestSCMHead) {
            PullRequestSCMHead pr = (PullRequestSCMHead) head;
            this.ref = "pull/" + pr.getNumber() + (pr.isMerge() ? "/merge" : "/head");
        } else if (head instanceof GitHubTagSCMHead){
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
        int index = path.lastIndexOf('/') + 1;
        try {
            List<GHContent> directoryContent = repo.getDirectoryContent(path.substring(0, index), Constants.R_REFS + ref);
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
        } catch (GHFileNotFoundException fnf) {
            boolean finicky = false;
            if (index == 0 || index == 1) {
                // the revision does not exist, we should complain unless JENKINS-54126.
                finicky = true;
            } else {
                try {
                    repo.getDirectoryContent("/", Constants.R_REFS + ref);
                } catch (IOException e) {
                    // this must be an issue with the revision, so complain unless JENKINS-54126
                    fnf.addSuppressed(e);
                    finicky = true;
                }
                // means that does not exist and this is handled below this try/catch block.
            }
            if (finicky && JENKINS_54126_WORKAROUND) {
                LOG.log(Level.FINE, String.format("JENKINS-54126 Received finacky response from GitHub %s : %s", repo.getFullName(), ref), fnf);
                final Optional<List<String>> status;
                final Map<String, List<String>> responseHeaderFields = fnf.getResponseHeaderFields();
                if (responseHeaderFields != null) {
                    status = Optional.ofNullable(responseHeaderFields.get(null));
                } else {
                    status = Optional.empty();
                }

                if (GitHubSCMSource.getCacheSize() > 0
                        && gitHub.getConnector() instanceof Connector.ForceValidationOkHttpConnector
                        && status.isPresent() && status.get().stream().anyMatch((s) -> s.contains("40"))) { //Any status >= 400 is a FNF in okhttp
                    //JENKINS-54126 try again without cache headers
                    LOG.log(Level.FINE, "JENKINS-54126 Attempting the request again with workaround.");
                    final Connector.ForceValidationOkHttpConnector oldConnector = (Connector.ForceValidationOkHttpConnector) gitHub.getConnector();
                    try {
                        //TODO I'm not sure we are alone in using this connector so maybe concurrent modification problems
                        gitHub.setConnector(oldConnector.getUncachedConnector());
                        return stat(path);
                    } finally {
                        gitHub.setConnector(oldConnector);
                    }
                } else if (STAT_RETHROW_API_FNF){
                    throw fnf;
                } else {
                    LOG.log(Level.FINE, "JENKINS-54126 silently ignoring the problem.");
                }
            }
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
            } else if (revision instanceof AbstractGitSCMSource.SCMRevisionImpl){
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
