/*
 * The MIT License
 *
 * Copyright 2015-2016 CloudBees, Inc.
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

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.AbortException;
import hudson.Extension;
import hudson.Util;
import hudson.console.HyperlinkNote;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.SCMSourceDescriptor;
import jenkins.scm.api.SCMSourceOwner;
import org.eclipse.jgit.transport.RefSpec;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHMyself;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.HttpException;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.net.ssl.SSLHandshakeException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import static hudson.model.Items.XSTREAM2;
import hudson.model.Run;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.Revision;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.PreBuildMerge;
import hudson.plugins.git.util.MergeRecord;
import hudson.scm.SCM;
import java.util.HashSet;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.jenkinsci.plugins.gitclient.CheckoutCommand;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.MergeCommand;
import static org.jenkinsci.plugins.github.config.GitHubServerConfig.GITHUB_URL;

public class GitHubSCMSource extends AbstractGitSCMSource {

    private static final Logger LOGGER = Logger.getLogger(GitHubSCMSource.class.getName());

    private final String apiUri;

    /** Credentials for actual clone; may be SSH private key. */
    private final String checkoutCredentialsId;

    /** Credentials for GitHub API; currently only supports username/password (personal access token). */
    private final String scanCredentialsId;

    private final String repoOwner;

    private final String repository;

    private @Nonnull String includes = DescriptorImpl.defaultIncludes;

    private @Nonnull String excludes = DescriptorImpl.defaultExcludes;

    /** Whether to build regular origin branches. */
    private @Nonnull Boolean buildOriginBranch = DescriptorImpl.defaultBuildOriginBranch;
    /** Whether to build origin branches which happen to also have a PR filed from them (but here we are naming and building as a branch). */
    private @Nonnull Boolean buildOriginBranchWithPR = DescriptorImpl.defaultBuildOriginBranchWithPR;
    /** Whether to build PRs filed from the origin, where the build is of the merge with the base branch. */
    private @Nonnull Boolean buildOriginPRMerge = DescriptorImpl.defaultBuildOriginPRMerge;
    /** Whether to build PRs filed from the origin, where the build is of the branch head. */
    private @Nonnull Boolean buildOriginPRHead = DescriptorImpl.defaultBuildOriginPRHead;
    /** Whether to build PRs filed from a fork, where the build is of the merge with the base branch. */
    private @Nonnull Boolean buildForkPRMerge = DescriptorImpl.defaultBuildForkPRMerge;
    /** Whether to build PRs filed from a fork, where the build is of the branch head. */
    private @Nonnull Boolean buildForkPRHead = DescriptorImpl.defaultBuildForkPRHead;

    @DataBoundConstructor
    public GitHubSCMSource(String id, String apiUri, String checkoutCredentialsId, String scanCredentialsId, String repoOwner, String repository) {
        super(id);
        this.apiUri = Util.fixEmpty(apiUri);
        this.repoOwner = repoOwner;
        this.repository = repository;
        this.scanCredentialsId = Util.fixEmpty(scanCredentialsId);
        this.checkoutCredentialsId = checkoutCredentialsId;
    }

    /** Use defaults for old settings. */
    @SuppressFBWarnings(value="RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE", justification="Only non-null after we set them here!")
    private Object readResolve() {
        if (buildOriginBranch == null) {
            buildOriginBranch = DescriptorImpl.defaultBuildOriginBranch;
        }
        if (buildOriginBranchWithPR == null) {
            buildOriginBranchWithPR = DescriptorImpl.defaultBuildOriginBranchWithPR;
        }
        if (buildOriginPRMerge == null) {
            buildOriginPRMerge = DescriptorImpl.defaultBuildOriginPRMerge;
        }
        if (buildOriginPRHead == null) {
            buildOriginPRHead = DescriptorImpl.defaultBuildOriginPRHead;
        }
        if (buildForkPRMerge == null) {
            buildForkPRMerge = DescriptorImpl.defaultBuildForkPRMerge;
        }
        if (buildForkPRHead == null) {
            buildForkPRHead = DescriptorImpl.defaultBuildForkPRHead;
        }
        return this;
    }

    @CheckForNull
    public String getApiUri() {
        return apiUri;
    }

    /**
     * Returns the effective credentials used to check out sources:
     *
     * null - anonymous access
     * SAME - represents that we want to use the same as scan credentials
     * UUID - represents the credentials identifier
     *
     * @return A string or null.
     */
    @CheckForNull
    @Override
    @SuppressWarnings("ConvertToStringSwitch") // more cumbersome with null check
    public String getCredentialsId() {
        if (DescriptorImpl.ANONYMOUS.equals(checkoutCredentialsId)) {
            return null;
        } else if (DescriptorImpl.SAME.equals(checkoutCredentialsId)) {
            return scanCredentialsId;
        } else {
            return checkoutCredentialsId;
        }
    }

    @CheckForNull
    public String getScanCredentialsId() {
        return scanCredentialsId;
    }

    @CheckForNull
    public String getCheckoutCredentialsId() {
        return checkoutCredentialsId;
    }

    public String getRepoOwner() {
        return repoOwner;
    }

    public String getRepository() {
        return repository;
    }

    @Override
    protected List<RefSpec> getRefSpecs() {
        return new ArrayList<>(Arrays.asList(new RefSpec("+refs/heads/*:refs/remotes/origin/*"),
            // For PRs we check out the head, then perhaps merge with the base branch.
            new RefSpec("+refs/pull/*/head:refs/remotes/origin/pr/*")));
    }

    /**
     * Returns a {@link RepositoryUriResolver} according to credentials configuration.
     *
     * @return a {@link RepositoryUriResolver}
     */
    public RepositoryUriResolver getUriResolver() {
        String credentialsId = getCredentialsId();
        if (credentialsId == null) {
            return new HttpsRepositoryUriResolver();
        } else {
            if (getCredentials(StandardCredentials.class, credentialsId) instanceof SSHUserPrivateKey) {
                return new SshRepositoryUriResolver();
            } else {
                // Defaults to HTTP/HTTPS
                return new HttpsRepositoryUriResolver();
            }
        }
    }

    /**
     * Returns a credentials by type and identifier.
     *
     * @param type Type that we are looking for
     * @param credentialsId Identifier of credentials
     * @return The credentials or null if it does not exists
     */
    private <T extends StandardCredentials> T getCredentials(@Nonnull Class<T> type, @Nonnull String credentialsId) {
        return CredentialsMatchers.firstOrNull(CredentialsProvider.lookupCredentials(
                type, getOwner(), ACL.SYSTEM,
                Collections.<DomainRequirement> emptyList()), CredentialsMatchers.allOf(
                CredentialsMatchers.withId(credentialsId),
                CredentialsMatchers.instanceOf(type)));
    }

    @Override
    public @Nonnull String getIncludes() {
        return includes;
    }

    @DataBoundSetter public void setIncludes(@Nonnull String includes) {
        this.includes = includes;
    }

    @Override
    public @Nonnull String getExcludes() {
        return excludes;
    }

    @DataBoundSetter public void setExcludes(@Nonnull String excludes) {
        this.excludes = excludes;
    }

    public boolean getBuildOriginBranch() {
        return buildOriginBranch;
    }

    @DataBoundSetter
    public void setBuildOriginBranch(boolean buildOriginBranch) {
        this.buildOriginBranch = buildOriginBranch;
    }

    public boolean getBuildOriginBranchWithPR() {
        return buildOriginBranchWithPR;
    }

    @DataBoundSetter
    public void setBuildOriginBranchWithPR(boolean buildOriginBranchWithPR) {
        this.buildOriginBranchWithPR = buildOriginBranchWithPR;
    }

    public boolean getBuildOriginPRMerge() {
        return buildOriginPRMerge;
    }

    @DataBoundSetter
    public void setBuildOriginPRMerge(boolean buildOriginPRMerge) {
        this.buildOriginPRMerge = buildOriginPRMerge;
    }

    public boolean getBuildOriginPRHead() {
        return buildOriginPRHead;
    }

    @DataBoundSetter
    public void setBuildOriginPRHead(boolean buildOriginPRHead) {
        this.buildOriginPRHead = buildOriginPRHead;
    }

    public boolean getBuildForkPRMerge() {
        return buildForkPRMerge;
    }

    @DataBoundSetter
    public void setBuildForkPRMerge(boolean buildForkPRMerge) {
        this.buildForkPRMerge = buildForkPRMerge;
    }

    public boolean getBuildForkPRHead() {
        return buildForkPRHead;
    }

    @DataBoundSetter
    public void setBuildForkPRHead(boolean buildForkPRHead) {
        this.buildForkPRHead = buildForkPRHead;
    }

    @Override
    public String getRemote() {
        return getUriResolver().getRepositoryUri(apiUri, repoOwner, repository);
    }

    @Override protected final void retrieve(SCMHeadObserver observer, final TaskListener listener) throws IOException, InterruptedException {
        StandardCredentials credentials = Connector.lookupScanCredentials(getOwner(), apiUri, scanCredentialsId);

        // Github client and validation
        GitHub github = Connector.connect(apiUri, credentials);
        try {
            github.checkApiUrlValidity();
        } catch (HttpException e) {
            String message = String.format("It seems %s is unreachable", apiUri == null ? GITHUB_URL : apiUri);
            throw new AbortException(message);
        }

        try {
            // Input data validation
            if (credentials != null && !github.isCredentialValid()) {
                String message = String.format("Invalid scan credentials %s to connect to %s, skipping", CredentialsNameProvider.name(credentials), apiUri == null ? GITHUB_URL : apiUri);
                throw new AbortException(message);
            }
            if (!github.isAnonymous()) {
                listener.getLogger().format("Connecting to %s using %s%n", apiUri == null ? GITHUB_URL : apiUri, CredentialsNameProvider.name(credentials));
            } else {
                listener.getLogger().format("Connecting to %s with no credentials, anonymous access%n", apiUri == null ? GITHUB_URL : apiUri);
            }

            // Input data validation
            if (repository == null || repository.isEmpty()) {
                throw new AbortException("No repository selected, skipping");
            }

            String fullName = repoOwner + "/" + repository;
            final GHRepository repo = github.getRepository(fullName);
            listener.getLogger().format("Looking up %s%n", HyperlinkNote.encodeTo(repo.getHtmlUrl().toString(), fullName));
            doRetrieve(observer, listener, repo);
            listener.getLogger().format("%nDone examining %s%n%n", fullName);
        } catch (RateLimitExceededException rle) {
            throw new AbortException(rle.getMessage());
        }
    }

    private void doRetrieve(SCMHeadObserver observer, TaskListener listener, GHRepository repo) throws IOException, InterruptedException {
        SCMSourceCriteria criteria = getCriteria();

        // To implement buildOriginBranch && !buildOriginBranchWithPR we need to first find the pull requests, so we can skip corresponding origin branches later. Awkward.
        Set<String> originBranchesWithPR = new HashSet<>();

        if (buildOriginBranchWithPR || buildOriginPRMerge || buildOriginPRHead || buildForkPRMerge || buildForkPRHead) {
            listener.getLogger().format("%n  Getting remote pull requests...%n");
            int pullrequests = 0;
            for (GHPullRequest ghPullRequest : repo.getPullRequests(GHIssueState.OPEN)) {
                int number = ghPullRequest.getNumber();
                listener.getLogger().format("%n    Checking pull request %s%n", HyperlinkNote.encodeTo(ghPullRequest.getHtmlUrl().toString(), "#" + number));
                boolean fork = !repo.getOwner().equals(ghPullRequest.getHead().getUser());
                if (fork && !buildForkPRMerge && !buildForkPRHead) {
                    listener.getLogger().format("    Submitted from fork, skipping%n%n");
                    continue;
                }
                if (!fork && !buildOriginPRMerge && !buildOriginPRHead && !buildOriginBranchWithPR) {
                    listener.getLogger().format("    Submitted from origin repository, skipping%n%n");
                    continue;
                }
                if (!fork) {
                    originBranchesWithPR.add(ghPullRequest.getHead().getRef());
                }
                boolean trusted = isTrusted(repo, ghPullRequest);
                if (!trusted) {
                    listener.getLogger().format("    (not from a trusted source)%n");
                }
                for (boolean merge : new boolean[] {false, true}) {
                    String branchName = "PR-" + number;
                    if (merge && fork) {
                        if (!buildForkPRMerge) {
                            continue; // not doing this combination
                        }
                        if (buildForkPRHead) {
                            branchName += "-merge"; // make sure they are distinct
                        }
                        // If we only build merged, or only unmerged, then we use the /PR-\d+/ scheme as before.
                    }
                    if (merge && !fork) {
                        if (!buildOriginPRMerge) {
                            continue;
                        }
                        if (buildForkPRHead) {
                            branchName += "-merge";
                        }
                    }
                    if (!merge && fork) {
                        if (!buildForkPRHead) {
                            continue;
                        }
                        if (buildForkPRMerge) {
                            branchName += "-head";
                        }
                    }
                    if (!merge && !fork) {
                        if (!buildOriginPRHead) {
                            continue;
                        }
                        if (buildOriginPRMerge) {
                            branchName += "-head";
                        }
                    }
                    listener.getLogger().format("    Job name: %s%n", branchName);
                    PullRequestSCMHead head = new PullRequestSCMHead(ghPullRequest, branchName, merge, trusted);
                    if (criteria != null) {
                        // Would be more precise to check whether the merge of the base branch head with the PR branch head contains a given file, etc.,
                        // but this would be a lot more work, and is unlikely to differ from using refs/pull/123/merge:
                        SCMSourceCriteria.Probe probe = getProbe(branchName, "pull request", "refs/pull/" + number + (merge ? "/merge" : "/head"), repo, listener);
                        if (criteria.isHead(probe, listener)) {
                            // FYI https://developer.github.com/v3/pulls/#response-1
                            Boolean mergeable = ghPullRequest.getMergeable();
                            if (Boolean.FALSE.equals(mergeable)) {
                                if (merge)  {
                                    listener.getLogger().format("      Not mergeable, build likely to fail%n");
                                } else {
                                    listener.getLogger().format("      Not mergeable, but will be built anyway%n");
                                }
                            }
                            listener.getLogger().format("    Met criteria%n");
                        } else {
                            listener.getLogger().format("    Does not meet criteria%n");
                            continue;
                        }
                    }
                    String baseHash;
                    if (merge) {
                        baseHash = repo.getRef("heads/" + ghPullRequest.getBase().getRef()).getObject().getSha();
                    } else {
                        baseHash = ghPullRequest.getBase().getSha();
                    }
                    PullRequestSCMRevision rev = new PullRequestSCMRevision(head, baseHash, ghPullRequest.getHead().getSha());
                    observer.observe(head, rev);
                    if (!observer.isObserving()) {
                        return;
                    }
                }
                pullrequests++;
            }
            listener.getLogger().format("%n  %d pull requests were processed%n", pullrequests);
        }

        if (buildOriginBranch || buildOriginBranchWithPR) {
            listener.getLogger().format("%n  Getting remote branches...%n");
            int branches = 0;
            for (Map.Entry<String,GHBranch> entry : repo.getBranches().entrySet()) {
                final String branchName = entry.getKey();
                if (isExcluded(branchName)) {
                    continue;
                }
                boolean hasPR = originBranchesWithPR.contains(branchName);
                if (!hasPR && !buildOriginBranch) {
                    listener.getLogger().format("%n    Skipping branch %s since there is no corresponding PR%n", branchName);
                    continue;
                }
                if (hasPR && !buildOriginBranchWithPR) {
                    listener.getLogger().format("%n    Skipping branch %s since there is a corresponding PR%n", branchName);
                    continue;
                }
                listener.getLogger().format("%n    Checking branch %s%n", HyperlinkNote.encodeTo(repo.getHtmlUrl().toString() + "/tree/" + branchName, branchName));
                if (criteria != null) {
                    SCMSourceCriteria.Probe probe = getProbe(branchName, "branch", "refs/heads/" + branchName, repo, listener);
                    if (criteria.isHead(probe, listener)) {
                        listener.getLogger().format("    Met criteria%n");
                    } else {
                        listener.getLogger().format("    Does not meet criteria%n");
                        continue;
                    }
                }
                SCMHead head = new SCMHead(branchName);
                SCMRevision hash = new SCMRevisionImpl(head, entry.getValue().getSHA1());
                observer.observe(head, hash);
                if (!observer.isObserving()) {
                    return;
                }
                branches++;
            }
            listener.getLogger().format("%n  %d branches were processed%n", branches);
        }
    }

    /**
     * Returns a {@link jenkins.scm.api.SCMSourceCriteria.Probe} for use in {@link #doRetrieve}.
     *
     * @param branch branch name
     * @param thing readable name of what this is, e.g. {@code branch}
     * @param ref the refspec, e.g. {@code refs/heads/myproduct-stable}
     * @param repo A repository on GitHub.
     * @param listener A TaskListener to log useful information
     *
     * @return A {@link jenkins.scm.api.SCMSourceCriteria.Probe}
     */
    protected SCMSourceCriteria.Probe getProbe(final String branch, final String thing, final String ref, final
            GHRepository repo, final TaskListener listener) {
        return new SCMSourceCriteria.Probe() {
            private static final long serialVersionUID = 5012552654534124387L;
            @Override public String name() {
                return branch;
            }
            @Override public long lastModified() {
                return 0; // TODO
            }
            @Override public boolean exists(@Nonnull String path) throws IOException {
                try {
                    int index = path.lastIndexOf('/') + 1;
                    List<GHContent> directoryContent = repo.getDirectoryContent(path.substring(0, index), ref);
                    for (GHContent content : directoryContent) {
                        if (content.isFile()) {
                            String filename = path.substring(index);
                            if (content.getName().equals(filename)) {
                                listener.getLogger().format("      ‘%s’ exists in this %s%n", path, thing);
                                return true;
                            }
                            if (content.getName().equalsIgnoreCase(filename)) {
                                listener.getLogger().format("      ‘%s’ not found (but found ‘%s’, search is case sensitive) in this %s, skipping%n", path, content.getName(), thing);
                                return false;
                            }
                        }
                    }
                } catch (FileNotFoundException fnf) {
                    // means that does not exist and this is handled below this try/catch block.
                }
                listener.getLogger().format("      ‘%s’ does not exist in this %s%n", path, thing);
                return false;
            }
        };
    }

    @Override
    @CheckForNull
    protected SCMRevision retrieve(SCMHead head, TaskListener listener) throws IOException, InterruptedException {
        StandardCredentials credentials = Connector.lookupScanCredentials(getOwner(), apiUri, scanCredentialsId);

        // Github client and validation
        GitHub github = Connector.connect(apiUri, credentials);
        try {
            github.checkApiUrlValidity();
        } catch (HttpException e) {
            String message = String.format("It seems %s is unreachable", apiUri == null ? GITHUB_URL : apiUri);
            throw new AbortException(message);
        }

        try {
            if (credentials != null && !github.isCredentialValid()) {
                String message = String.format("Invalid scan credentials %s to connect to %s, skipping", CredentialsNameProvider.name(credentials), apiUri == null ? GITHUB_URL : apiUri);
                throw new AbortException(message);
            }
            if (!github.isAnonymous()) {
                listener.getLogger().format("Connecting to %s using %s%n", apiUri == null ? GITHUB_URL : apiUri, CredentialsNameProvider.name(credentials));
            } else {
                listener.getLogger().format("Connecting to %s using anonymous access%n", apiUri == null ? GITHUB_URL : apiUri);
            }
            String fullName = repoOwner + "/" + repository;
            GHRepository repo = github.getRepository(fullName);
            return doRetrieve(head, listener, repo);
        } catch (RateLimitExceededException rle) {
            throw new AbortException(rle.getMessage());
        }
    }

    protected SCMRevision doRetrieve(SCMHead head, TaskListener listener, GHRepository repo) throws IOException, InterruptedException {
        if (head instanceof PullRequestSCMHead) {
            PullRequestSCMHead prhead = (PullRequestSCMHead) head;
            int number = prhead.getNumber();
            GHPullRequest pr = repo.getPullRequest(number);
            String baseHash;
            if (prhead.isMerge()) {
                PullRequestAction metadata = prhead.getAction(PullRequestAction.class);
                if (metadata == null) {
                    throw new IOException("Cannot find base branch metadata from " + prhead);
                }
                baseHash = repo.getRef("heads/" + metadata.getTarget().getName()).getObject().getSha();
            } else {
                baseHash = pr.getBase().getSha();
            }
            return new PullRequestSCMRevision((PullRequestSCMHead) head, baseHash, pr.getHead().getSha());
        } else {
            return new SCMRevisionImpl(head, repo.getRef("heads/" + head.getName()).getObject().getSha());
        }
    }

    @Override
    public SCM build(SCMHead head, SCMRevision revision) {
        if (revision == null) {
            // TODO will this work sanely for PRs? Branch.scm seems to be used only as a fallback for SCMBinder/SCMVar where they would perhaps better just report an error.
            return super.build(head, null);
        } else if (head instanceof PullRequestSCMHead) {
            if (!(revision instanceof PullRequestSCMRevision)) {
                // TODO this seems to happen for PR-6-unmerged in cloudbeers/PR-demo; why?
                LOGGER.log(Level.WARNING, "Unexpected revision class {0} for {1}", new Object[] {revision.getClass().getName(), head});
                return super.build(head, revision);
            }
            PullRequestSCMRevision prRev = (PullRequestSCMRevision) revision;
            GitSCM scm = (GitSCM) super.build(/* does this matter? */head, new SCMRevisionImpl(/* again probably irrelevant */head, prRev.getPullHash()));
            if (((PullRequestSCMHead) head).isMerge()) {
                PullRequestAction action = head.getAction(PullRequestAction.class);
                String baseName;
                if (action != null) {
                    baseName = action.getTarget().getName();
                } else {
                    baseName = "master?"; // OK if not found, just informational anyway
                }
                scm.getExtensions().add(new MergeWith(baseName, prRev.getBaseHash()));
            }
            return scm;
        } else {
            return super.build(head, /* casting just as an assertion */(SCMRevisionImpl) revision);
        }
    }
    /**
     * Similar to {@link PreBuildMerge}, but we cannot use that unmodified: we need to specify the exact base branch hash.
     * It is possible to just ask Git to check out {@code refs/pull/123/merge}, but this has two problems:
     * GitHub’s implementation is not all that reliable (for example JENKINS-33237, and time-delayed snapshots);
     * and it is subject to a race condition between the {@code baseHash} we think we are merging with and a possibly newer one that was just pushed.
     * Performing the merge ourselves is simple enough and ensures that we are building exactly what the {@link PullRequestSCMRevision} represented.
     */
    private static class MergeWith extends GitSCMExtension {
        private final String baseName;
        private final String baseHash;
        MergeWith(String baseName, String baseHash) {
            this.baseName = baseName;
            this.baseHash = baseHash;
        }
        @Override
        public Revision decorateRevisionToBuild(GitSCM scm, Run<?, ?> build, GitClient git, TaskListener listener, Revision marked, Revision rev) throws IOException, InterruptedException, GitException {
            listener.getLogger().println("Merging " + baseName + " commit " + baseHash + " into PR head commit " + rev.getSha1String());
            checkout(scm, build, git, listener, rev);
            try {
                git.setAuthor("Jenkins", /* could parse out of JenkinsLocationConfiguration.get().getAdminAddress() but seems overkill */"nobody@nowhere");
                git.setCommitter("Jenkins", "nobody@nowhere");
                MergeCommand cmd = git.merge().setRevisionToMerge(ObjectId.fromString(baseHash));
                for (GitSCMExtension ext : scm.getExtensions()) {
                    // By default we do a regular merge, allowing it to fast-forward.
                    ext.decorateMergeCommand(scm, build, git, listener, cmd);
                }
                cmd.execute();
            } catch (GitException x) {
                // Try to revert merge conflict markers.
                // TODO IGitAPI offers a reset(hard) method yet GitClient does not. Why?
                checkout(scm, build, git, listener, rev);
                // TODO would be nicer to throw an AbortException with just the message, but this is actually worse until git-client 1.19.7+
                throw x;
            }
            build.addAction(new MergeRecord(baseName, baseHash)); // does not seem to be used, but just in case
            ObjectId mergeRev = git.revParse(Constants.HEAD);
            listener.getLogger().println("Merge succeeded, producing " + mergeRev.name());
            return new Revision(mergeRev, rev.getBranches()); // note that this ensures Build.revision != Build.marked
        }
        private void checkout(GitSCM scm, Run<?,?> build, GitClient git, TaskListener listener, Revision rev) throws InterruptedException, IOException, GitException {
            CheckoutCommand checkoutCommand = git.checkout().ref(rev.getSha1String());
            for (GitSCMExtension ext : scm.getExtensions()) {
                ext.decorateCheckoutCommand(scm, build, git, listener, checkoutCommand);
            }
            checkoutCommand.execute();
        }
    }

    @Override
    public SCMRevision getTrustedRevision(SCMRevision revision, TaskListener listener) throws IOException, InterruptedException {
        if (revision instanceof PullRequestSCMRevision && !((PullRequestSCMHead) revision.getHead()).isTrusted()) {
            PullRequestAction metadata = revision.getHead().getAction(PullRequestAction.class);
            if (metadata != null) {
                PullRequestSCMRevision rev = (PullRequestSCMRevision) revision;
                listener.getLogger().println("Loading trusted files from base branch " + metadata.getTarget().getName() + " at " + rev.getBaseHash() + " rather than " + rev.getPullHash());
                return new SCMRevisionImpl(metadata.getTarget(), rev.getBaseHash());
            } else {
                throw new IOException("Cannot find base branch metadata from " + revision.getHead());
            }
        }
        return revision;
    }

    /**
     * Evaluates whether this pull request is coming from a trusted source.
     * Quickest is to check whether the author of the PR
     * <a href="https://developer.github.com/v3/repos/collaborators/#check-if-a-user-is-a-collaborator">is a collaborator of the repository</a>.
     * By checking <a href="https://developer.github.com/v3/repos/collaborators/#list-collaborators">all collaborators</a>
     * it is possible to further ascertain if they are in a team which was specifically granted push permission,
     * but this is potentially expensive as there might be multiple pages of collaborators to retrieve.
     * TODO since the GitHub API wrapper currently supports neither, we list all collaborator names and check for membership,
     * paying the performance penalty without the benefit of the accuracy.
     * @param ghPullRequest a PR
     * @return true if this is a trusted PR
     * @see <a href="https://developer.github.com/v3/pulls/#get-a-single-pull-request">PR metadata</a>
     * @see <a href="http://stackoverflow.com/questions/15096331/github-api-how-to-find-the-branches-of-a-pull-request#comment54931031_15096596">base revision oddity</a>
     */
    private boolean isTrusted(@Nonnull GHRepository repo, @Nonnull GHPullRequest ghPullRequest) throws IOException {
        return repo.getCollaboratorNames().contains(ghPullRequest.getUser().getLogin());
    }

    @Extension public static class DescriptorImpl extends SCMSourceDescriptor {

        public static final String defaultIncludes = "*";
        public static final String defaultExcludes = "";
        public static final String ANONYMOUS = "ANONYMOUS";
        public static final String SAME = "SAME";
        // Prior to JENKINS-33161 the unconditional behavior was to build fork PRs plus origin branches, and try to build a merge revision for PRs.
        public static final boolean defaultBuildOriginBranch = true;
        public static final boolean defaultBuildOriginBranchWithPR = true;
        public static final boolean defaultBuildOriginPRMerge = false;
        public static final boolean defaultBuildOriginPRHead = false;
        public static final boolean defaultBuildForkPRMerge = true;
        public static final boolean defaultBuildForkPRHead = false;

        @Initializer(before = InitMilestone.PLUGINS_STARTED)
        public static void addAliases() {
            XSTREAM2.addCompatibilityAlias("org.jenkinsci.plugins.github_branch_source.OriginGitHubSCMSource", GitHubSCMSource.class);
        }

        @Override
        public String getDisplayName() {
            return "GitHub";
        }

        @Restricted(NoExternalUse.class)
        public FormValidation doCheckIncludes(@QueryParameter String value) {
            if (value.isEmpty()) {
                return FormValidation.warning(Messages.GitHubSCMSource_did_you_mean_to_use_to_match_all_branches());
            }
            return FormValidation.ok();
        }

        @Restricted(NoExternalUse.class)
        public FormValidation doCheckScanCredentialsId(@AncestorInPath SCMSourceOwner context,
                @QueryParameter String scanCredentialsId, @QueryParameter String apiUri) {
            if (!scanCredentialsId.isEmpty()) {
                StandardCredentials credentials = Connector.lookupScanCredentials(context, apiUri, scanCredentialsId);
                if (credentials == null) {
                    return FormValidation.error("Credentials not found");
                } else {
                    try {
                        GitHub connector = Connector.connect(apiUri, credentials);
                        if (connector.isCredentialValid()) {
                            return FormValidation.ok();
                        } else {
                            return FormValidation.error("Invalid credentials");
                        }
                    } catch (IOException e) {
                        // ignore, never thrown
                        LOGGER.log(Level.WARNING, "Exception validating credentials {0} on {1}", new Object[] {CredentialsNameProvider.name(credentials), apiUri});
                        return FormValidation.error("Exception validating credentials");
                    }
                }
            } else {
                return FormValidation.warning("Credentials are recommended");
            }
        }

        @Restricted(NoExternalUse.class)
        public FormValidation doCheckBuildOriginBranchWithPR(
            @QueryParameter boolean buildOriginBranch,
            @QueryParameter boolean buildOriginBranchWithPR,
            @QueryParameter boolean buildOriginPRMerge,
            @QueryParameter boolean buildOriginPRHead,
            @QueryParameter boolean buildForkPRMerge,
            @QueryParameter boolean buildForkPRHead
        ) {
            if (buildOriginBranch && !buildOriginBranchWithPR && !buildOriginPRMerge && !buildOriginPRHead && !buildForkPRMerge && !buildForkPRHead) {
                // TODO in principle we could make doRetrieve populate originBranchesWithPR without actually including any PRs, but it would be more work and probably never wanted anyway.
                return FormValidation.warning("If you are not building any PRs, all origin branches will be built.");
            }
            return FormValidation.ok();
        }

        @Restricted(NoExternalUse.class)
        public FormValidation doCheckBuildOriginPRHead(@QueryParameter boolean buildOriginBranchWithPR, @QueryParameter boolean buildOriginPRMerge, @QueryParameter boolean buildOriginPRHead) {
            if (buildOriginBranchWithPR && buildOriginPRHead) {
                return FormValidation.warning("Redundant to build an origin PR both as a branch and as an unmerged PR.");
            }
            if (buildOriginPRMerge && buildOriginPRHead) {
                return FormValidation.ok("Merged vs. unmerged PRs will be distinguished in the job name (*-merge vs. *-head).");
            }
            return FormValidation.ok();
        }

        @Restricted(NoExternalUse.class)
        public FormValidation doCheckBuildForkPRHead/* web method name controls UI position of message; we want this at the bottom */(
            @QueryParameter boolean buildOriginBranch,
            @QueryParameter boolean buildOriginBranchWithPR,
            @QueryParameter boolean buildOriginPRMerge,
            @QueryParameter boolean buildOriginPRHead,
            @QueryParameter boolean buildForkPRMerge,
            @QueryParameter boolean buildForkPRHead
        ) {
            if (!buildOriginBranch && !buildOriginBranchWithPR && !buildOriginPRMerge && !buildOriginPRHead && !buildForkPRMerge && !buildForkPRHead) {
                return FormValidation.warning("You need to build something!");
            }
            if (buildForkPRMerge && buildForkPRHead) {
                return FormValidation.ok("Merged vs. unmerged PRs will be distinguished in the job name (*-merge vs. *-head).");
            }
            return FormValidation.ok();
        }

        public ListBoxModel doFillApiUriItems() {
            ListBoxModel result = new ListBoxModel();
            result.add("GitHub", "");
            for (Endpoint e : GitHubConfiguration.get().getEndpoints()) {
                result.add(e.getName() == null ? e.getApiUri() : e.getName(), e.getApiUri());
            }
            return result;
        }

        public ListBoxModel doFillCheckoutCredentialsIdItems(@AncestorInPath SCMSourceOwner context, @QueryParameter String apiUri) {
            StandardListBoxModel result = new StandardListBoxModel();
            result.add("- same as scan credentials -", SAME);
            result.add("- anonymous -", ANONYMOUS);
            Connector.fillCheckoutCredentialsIdItems(result, context, apiUri);
            return result;
        }

        public ListBoxModel doFillScanCredentialsIdItems(@AncestorInPath SCMSourceOwner context, @QueryParameter String apiUri) {
            StandardListBoxModel result = new StandardListBoxModel();
            result.withEmptySelection();
            Connector.fillScanCredentialsIdItems(result, context, apiUri);
            return result;
        }

        public ListBoxModel doFillRepositoryItems(@AncestorInPath SCMSourceOwner context, @QueryParameter String apiUri,
                @QueryParameter String scanCredentialsId, @QueryParameter String repoOwner) {
            Set<String> result = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

            repoOwner = Util.fixEmptyAndTrim(repoOwner);
            if (repoOwner == null) {
                return nameAndValueModel(result);
            }
            try {
                StandardCredentials credentials = Connector.lookupScanCredentials(context, apiUri, scanCredentialsId);
                GitHub github = Connector.connect(apiUri, credentials);

                if (!github.isAnonymous()) {
                    GHMyself myself = null;
                    try {
                        myself = github.getMyself();
                    } catch (IllegalStateException e) {
                        LOGGER.log(Level.WARNING, e.getMessage());
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "Exception retrieving the repositories of the owner {0} on {1} with credentials {2}",
                            new Object[] {repoOwner, apiUri, CredentialsNameProvider.name(credentials)});
                    }
                    if (myself != null && repoOwner.equalsIgnoreCase(myself.getLogin())) {
                        for (String name : myself.getAllRepositories().keySet()) {
                            result.add(name);
                        }
                        return nameAndValueModel(result);
                    }
                }

                GHOrganization org = null;
                try {
                    org = github.getOrganization(repoOwner);
                } catch (FileNotFoundException fnf) {
                    LOGGER.log(Level.FINE, "There is not any GH Organization named {0}", repoOwner);
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, e.getMessage());
                }
                if (org != null && repoOwner.equalsIgnoreCase(org.getLogin())) {
                    for (GHRepository repo : org.listRepositories()) {
                        result.add(repo.getName());
                    }
                    return nameAndValueModel(result);
                }

                GHUser user = null;
                try {
                    user = github.getUser(repoOwner);
                } catch (FileNotFoundException fnf) {
                    LOGGER.log(Level.FINE, "There is not any GH User named {0}", repoOwner);
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, e.getMessage());
                }
                if (user != null && repoOwner.equalsIgnoreCase(user.getLogin())) {
                    for (GHRepository repo : user.listRepositories()) {
                        result.add(repo.getName());
                    }
                    return nameAndValueModel(result);
                }
            } catch (SSLHandshakeException he) {
                LOGGER.log(Level.SEVERE, he.getMessage());
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, e.getMessage());
            }
            return nameAndValueModel(result);
        }
        /**
         * Creates a list box model from a list of values.
         * ({@link ListBoxModel#ListBoxModel(Collection)} takes {@link hudson.util.ListBoxModel.Option}s,
         * not {@link String}s, and those are not {@link Comparable}.)
         */
        private static ListBoxModel nameAndValueModel(Collection<String> items) {
            ListBoxModel model = new ListBoxModel();
            for (String item : items) {
                model.add(item);
            }
            return model;
        }

    }

}
