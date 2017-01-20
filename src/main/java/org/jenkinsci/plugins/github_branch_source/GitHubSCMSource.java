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

import com.cloudbees.jenkins.GitHubWebHook;
import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.AbortException;
import hudson.Extension;
import hudson.Util;
import hudson.console.HyperlinkNote;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Action;
import hudson.model.Actionable;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.Revision;
import hudson.plugins.git.browser.GitRepositoryBrowser;
import hudson.plugins.git.browser.GithubWeb;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.PreBuildMerge;
import hudson.plugins.git.util.MergeRecord;
import hudson.scm.SCM;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLHandshakeException;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadCategory;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMProbe;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.SCMSourceDescriptor;
import jenkins.scm.api.SCMSourceEvent;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.api.metadata.ObjectMetadataAction;
import jenkins.scm.api.metadata.PrimaryInstanceMetadataAction;
import jenkins.scm.impl.ChangeRequestSCMHeadCategory;
import jenkins.scm.impl.UncategorizedSCMHeadCategory;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.RefSpec;
import org.jenkinsci.plugins.gitclient.CheckoutCommand;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.MergeCommand;
import org.jenkinsci.plugins.github.config.GitHubServerConfig;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.github.GHBranch;
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

import static hudson.model.Items.XSTREAM2;

public class GitHubSCMSource extends AbstractGitSCMSource {

    public static final String VALID_GITHUB_REPO_NAME = "^[0-9A-Za-z._-]+$";
    public static final String VALID_GITHUB_USER_NAME = "^[0-9A-Za-z]([0-9A-Za-z._-]+[0-9A-Za-z])$";
    public static final String VALID_GIT_SHA1 = "^[a-fA-F0-9]{40}$";
    public static final String GITHUB_URL = GitHubServerConfig.GITHUB_URL;
    private static final Logger LOGGER = Logger.getLogger(GitHubSCMSource.class.getName());
    /**
     * Log spam protection, only log at most once every 5 minutes.
     */
    // TODO remove once baseline Git plugin 3.0.2+
    private static final AtomicLong jenkins41244Warning = new AtomicLong();

    private final String apiUri;

    /** Credentials for actual clone; may be SSH private key. */
    private final String checkoutCredentialsId;

    /** Credentials for GitHub API; currently only supports username/password (personal access token). */
    private final String scanCredentialsId;

    private final String repoOwner;

    private final String repository;

    @NonNull
    private String includes = DescriptorImpl.defaultIncludes;

    @NonNull
    private String excludes = DescriptorImpl.defaultExcludes;

    /** Whether to build regular origin branches. */
    @NonNull
    private Boolean buildOriginBranch = DescriptorImpl.defaultBuildOriginBranch;
    /** Whether to build origin branches which happen to also have a PR filed from them (but here we are naming and building as a branch). */
    @NonNull
    private Boolean buildOriginBranchWithPR = DescriptorImpl.defaultBuildOriginBranchWithPR;
    /** Whether to build PRs filed from the origin, where the build is of the merge with the base branch. */
    @NonNull
    private Boolean buildOriginPRMerge = DescriptorImpl.defaultBuildOriginPRMerge;
    /** Whether to build PRs filed from the origin, where the build is of the branch head. */
    @NonNull
    private Boolean buildOriginPRHead = DescriptorImpl.defaultBuildOriginPRHead;
    /** Whether to build PRs filed from a fork, where the build is of the merge with the base branch. */
    @NonNull
    private Boolean buildForkPRMerge = DescriptorImpl.defaultBuildForkPRMerge;
    /** Whether to build PRs filed from a fork, where the build is of the branch head. */
    @NonNull
    private Boolean buildForkPRHead = DescriptorImpl.defaultBuildForkPRHead;

    /**
     * Cache of the official repository HTML URL as reported by {@link GitHub#getRepository(String)}.
     */
    private transient URL repositoryUrl;
    /**
     * The collaborator names used to determine if pull requests are from trusted authors
     */
    @CheckForNull
    private transient Set<String> collaboratorNames;

    /**
     * The cache of {@link ObjectMetadataAction} instances for each open PR.
     */
    @NonNull
    private transient /*effectively final*/ Map<Integer,ObjectMetadataAction> pullRequestMetadataCache;

    @DataBoundConstructor
    public GitHubSCMSource(String id, String apiUri, String checkoutCredentialsId, String scanCredentialsId, String repoOwner, String repository) {
        super(id);
        this.apiUri = Util.fixEmpty(apiUri);
        this.repoOwner = repoOwner;
        this.repository = repository;
        this.scanCredentialsId = Util.fixEmpty(scanCredentialsId);
        this.checkoutCredentialsId = checkoutCredentialsId;
        pullRequestMetadataCache = new ConcurrentHashMap<>();
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
        if (pullRequestMetadataCache == null) {
            pullRequestMetadataCache = new ConcurrentHashMap<>();
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

    /** {@inheritDoc} */
    @Override
    public String getPronoun() {
        return Messages.GitHubSCMSource_Pronoun();
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
    private <T extends StandardCredentials> T getCredentials(@NonNull Class<T> type, @NonNull String credentialsId) {
        return CredentialsMatchers.firstOrNull(CredentialsProvider.lookupCredentials(
                type, getOwner(), ACL.SYSTEM,
                Collections.<DomainRequirement> emptyList()), CredentialsMatchers.allOf(
                CredentialsMatchers.withId(credentialsId),
                CredentialsMatchers.instanceOf(type)));
    }

    @Override
    @NonNull
    public String getIncludes() {
        return includes;
    }

    @DataBoundSetter public void setIncludes(@NonNull String includes) {
        this.includes = includes;
    }

    @Override
    @NonNull
    public String getExcludes() {
        return excludes;
    }

    @DataBoundSetter public void setExcludes(@NonNull String excludes) {
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

    @Override
    protected final void retrieve(@CheckForNull SCMSourceCriteria criteria,
                                  @NonNull SCMHeadObserver observer,
                                  @CheckForNull SCMHeadEvent<?> event,
                                  @NonNull final TaskListener listener) throws IOException, InterruptedException {
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
                String message = String.format("Invalid scan credentials %s to connect to %s, skipping",
                        CredentialsNameProvider.name(credentials), apiUri == null ? GITHUB_URL : apiUri);
                throw new AbortException(message);
            }
            if (!github.isAnonymous()) {
                listener.getLogger().format("Connecting to %s using %s%n",
                        apiUri == null ? GITHUB_URL : apiUri, CredentialsNameProvider.name(credentials));
            } else {
                listener.getLogger().format("Connecting to %s with no credentials, anonymous access%n",
                        apiUri == null ? GITHUB_URL : apiUri);
            }

            // Input data validation
            if (repository == null || repository.isEmpty()) {
                throw new AbortException("No repository selected, skipping");
            }

            String fullName = repoOwner + "/" + repository;
            final GHRepository repo = github.getRepository(fullName);
            listener.getLogger().format("Looking up %s%n", HyperlinkNote.encodeTo(repo.getHtmlUrl().toString(), fullName));
            try {
                repositoryUrl = repo.getHtmlUrl();
                collaboratorNames = new HashSet<>(repo.getCollaboratorNames());
            } catch (FileNotFoundException e) {
                // not permitted
                listener.getLogger().println("Not permitted to query list of collaborators, assuming none");
                collaboratorNames = Collections.emptySet();
            }
            doRetrieve(criteria, observer, listener, repo);
            listener.getLogger().format("%nDone examining %s%n%n", fullName);
        } catch (RateLimitExceededException rle) {
            throw new AbortException(rle.getMessage());
        }
    }

    private void doRetrieve(SCMSourceCriteria criteria, SCMHeadObserver observer, TaskListener listener, GHRepository repo) throws IOException, InterruptedException {
        boolean wantPRs = true;
        boolean wantBranches = true;
        Set<Integer> wantPRNumbers = null;
        int wantBranchCount = 0;
        Set<SCMHead> includes = observer.getIncludes();
        if (includes != null) {
            wantPRs = false;
            wantBranches = false;
            wantPRNumbers = new HashSet<>();
            for (SCMHead h : includes) {
                if (h instanceof PullRequestSCMHead) {
                    wantPRs = true;
                    wantPRNumbers.add(((PullRequestSCMHead) h).getNumber());
                } else if (h instanceof BranchSCMHead) {
                    wantBranches = true;
                    wantBranchCount++;
                }
            }
        }

        // To implement buildOriginBranch && !buildOriginBranchWithPR we need to first find the pull requests,
        // so we can skip corresponding origin branches later. Awkward.
        Set<String> originBranchesWithPR = new HashSet<>();

        if ((wantPRs || (wantBranches && (!buildOriginBranch || !buildOriginBranchWithPR)))
                && (buildOriginBranchWithPR || buildOriginPRMerge || buildOriginPRHead || buildForkPRMerge
                || buildForkPRHead)) {
            int pullrequests = 0;
            boolean onlyWantPRBranch = false;
            if (includes != null && wantBranches && wantBranchCount == 1 && wantPRNumbers.size() == 1) {
                // there are some configuration options that let PullRequestGHEventSubscriber generate a collection
                // of both pull requests and a single branch, e.g. we may have the merge pr head, the fork pr head and
                // the origin branch head. This optimization here prevents iterating all the PRs just to trigger
                // an update of the single branch as we only need to retrieve that single PR
                PullRequestSCMHead prh = null;
                BranchSCMHead brh = null;
                for (SCMHead h : includes) {
                    if (h instanceof PullRequestSCMHead) {
                        prh = (PullRequestSCMHead) h;
                        if (brh != null) {
                            break;
                        }
                    }
                    if (h instanceof BranchSCMHead) {
                        brh = (BranchSCMHead) h;
                        if (prh != null) {
                            break;
                        }
                    }
                }
                if (brh != null && prh != null) {
                    // this is a case where we do not need to iterate all the pull requests as the only
                    // pull request we are interested in is the same as the branch we are interested in
                    // thus we can reduce API calls and save iterating all PRs
                    onlyWantPRBranch = repoOwner.equals(prh.getSourceOwner())
                            && brh.equals(prh.getTarget());
                }
            }
            Iterable<GHPullRequest> pullRequests;
            if (includes != null && (!wantBranches || onlyWantPRBranch) && wantPRNumbers.size() == 1) {
                // Special case optimization. We only want one PR number and we don't need to get any branches
                //
                // Here we can just get the only PR we are interested in and save API rate limit
                // if we needed more than one PR, we would have to make multiple API calls, one for each PR
                // and thus a single call to getPullRequests would be expected to be cheaper (note that if there
                // are multiple pages of pull requests and we are only interested in two pull requests on the last
                // page then this assumption would break down... but in general this will not be the expected case
                // hence we only optimize for the single PR case as that is expected to be common when validating
                // events
                int number = wantPRNumbers.iterator().next();
                listener.getLogger().format("%n  Getting remote pull request #%d...%n", number);
                GHPullRequest pr = repo.getPullRequest(number);
                if (pr == null || GHIssueState.CLOSED.equals(pr.getState())) {
                    pullRequests = Collections.emptyList();
                } else {
                    pullRequests = Collections.singletonList(pr);
                }
            } else {
                listener.getLogger().format("%n  Getting remote pull requests...%n");
                // we use a paged iterable so that if the observer is finished observing we stop making API calls
                pullRequests = repo.queryPullRequests().state(GHIssueState.OPEN).list();
            }
            Set<Integer> pullRequestMetadataKeys = new HashSet<>();
            for (GHPullRequest ghPullRequest : pullRequests) {
                checkInterrupt();
                int number = ghPullRequest.getNumber();
                if (includes != null && !wantBranches && !wantPRNumbers.contains(number)) {
                    continue;
                }
                boolean fork = !repo.getOwner().equals(ghPullRequest.getHead().getUser());
                if (wantPRs) {
                    listener.getLogger().format("%n    Checking pull request %s%n",
                            HyperlinkNote.encodeTo(ghPullRequest.getHtmlUrl().toString(), "#" + number));
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
                } else {
                    // we just wanted the list of origin branches with PR for the withBranches
                    if (!fork && (buildOriginPRMerge || buildOriginPRHead || buildOriginBranchWithPR)) {
                        originBranchesWithPR.add(ghPullRequest.getHead().getRef());
                    }
                    continue;
                }
                boolean trusted = collaboratorNames != null
                        && collaboratorNames.contains(ghPullRequest.getHead().getRepository().getOwnerName());
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
                    pullRequestMetadataCache.put(number,
                            new ObjectMetadataAction(
                                    ghPullRequest.getTitle(),
                                    ghPullRequest.getBody(),
                                    ghPullRequest.getHtmlUrl().toExternalForm()
                            )
                    );
                    pullRequestMetadataKeys.add(number);
                    PullRequestSCMHead head = new PullRequestSCMHead(ghPullRequest, branchName, merge);
                    if (includes != null && !includes.contains(head)) {
                        // don't waste rate limit testing a head we are not interested in
                        continue;
                    }
                    listener.getLogger().format("    Job name: %s%n", branchName);
                    if (criteria != null) {
                        // Would be more precise to check whether the merge of the base branch head with the PR branch head contains a given file, etc.,
                        // but this would be a lot more work, and is unlikely to differ from using refs/pull/123/merge:

                        SCMSourceCriteria.Probe probe = createProbe(head, null);
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
            if (includes == null) {
                // we did a full scan, so trim the cache entries
                this.pullRequestMetadataCache.keySet().retainAll(pullRequestMetadataKeys);
            }
        }

        if (wantBranches && (buildOriginBranch || buildOriginBranchWithPR)) {
            listener.getLogger().format("%n  Getting remote branches...%n");
            int branches = 0;
            Map<String, GHBranch> branchMap;
            if (includes != null && wantBranchCount == 1) {
                // Special case optimization. We only want one branch
                //
                // Here we can just get the only branch we are interested in and save API rate limit
                // if we needed more than one branch, we would have to make multiple API calls, one for each branch
                // and thus a single call to getBranch would be expected to be cheaper (note that if there
                // are multiple pages of branches and we are only interested in two branches then this assumption
                // would break down... but in general this will not be the expected case
                // hence we only optimize for the single branch case as that is expected to be common when validating
                // events
                BranchSCMHead head = null;
                for (SCMHead h : includes) {
                    if (h instanceof BranchSCMHead) {
                        head = (BranchSCMHead) h;
                        break;
                    }
                }
                GHBranch branch = null;
                try {
                    branch = head != null ? repo.getBranch(head.getName()) : null;
                } catch (FileNotFoundException ignore) {
                    // this exception implies that the head has been deleted
                    // a more generic exception would indicate an IO error
                }
                if (branch == null) {
                    branchMap = Collections.emptyMap();
                } else {
                    branchMap = Collections.singletonMap(branch.getName(), branch);
                }
            } else {
                branchMap = repo.getBranches();
            }

            for (Map.Entry<String, GHBranch> entry : branchMap.entrySet()) {
                checkInterrupt();
                final String branchName = entry.getKey();
                if (isExcluded(branchName)) {
                    continue;
                }
                SCMHead head = new BranchSCMHead(branchName);
                if (includes != null && !includes.contains(head)) {
                    // don't waste rate limit testing a head we are not interested in
                    continue;
                }
                boolean hasPR = originBranchesWithPR.contains(branchName);
                if (!buildOriginBranch && !hasPR) {
                    listener.getLogger().format("%n    Skipping branch %s since there is no corresponding PR%n", branchName);
                    continue;
                }
                if (!buildOriginBranchWithPR && hasPR) {
                    listener.getLogger().format("%n    Skipping branch %s since there is a corresponding PR%n", branchName);
                    continue;
                }
                listener.getLogger().format("%n    Checking branch %s%n", HyperlinkNote.encodeTo(repo.getHtmlUrl().toString() + "/tree/" + branchName, branchName));
                SCMRevision hash = new SCMRevisionImpl(head, entry.getValue().getSHA1());
                if (criteria != null) {
                    SCMSourceCriteria.Probe probe = createProbe(head, hash);
                    if (criteria.isHead(probe, listener)) {
                        listener.getLogger().format("    Met criteria%n");
                    } else {
                        listener.getLogger().format("    Does not meet criteria%n");
                        continue;
                    }
                }
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
     * {@inheritDoc}
     */
    @Override
    protected boolean isExcluded(String branchName) {
        return super.isExcluded(branchName); // override so that we can call this method from this SCMHeadEvent
    }

    @NonNull
    @Override
    protected SCMProbe createProbe(@NonNull SCMHead head, @CheckForNull final SCMRevision revision) throws IOException {
        StandardCredentials credentials = Connector.lookupScanCredentials(getOwner(), apiUri, scanCredentialsId);
        // Github client and validation
        GitHub github = Connector.connect(apiUri, credentials);
        String fullName = repoOwner + "/" + repository;
        try {
            github.checkApiUrlValidity();
        } catch (HttpException e) {
            throw new IOException(String.format("It seems %s is unreachable", apiUri == null ? GITHUB_URL : apiUri), e);
        }
        String credentialsName = credentials == null ? "anonymous access" : CredentialsNameProvider.name(credentials);
        if (credentials != null && !github.isCredentialValid()) {
            throw new AbortException(String.format("Invalid scan credentials %s to connect to %s, skipping",
                    credentialsName, apiUri == null ? GITHUB_URL : apiUri));
        }
        GHRepository repo;
        try {
            repo = github.getRepository(fullName);
        } catch (FileNotFoundException e) {
            throw new AbortException(String.format("Invalid scan credentials when using %s to connect to %s on %s",
                    credentialsName, fullName, apiUri == null ? GITHUB_URL : apiUri));
        }
        return new GitHubSCMProbe(repo, head, revision);
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
            repositoryUrl = repo.getHtmlUrl();
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
                baseHash = repo.getRef("heads/" + prhead.getTarget().getName()).getObject().getSha();
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
            GitSCM scm = (GitSCM) super.build(head, null);
            String repoUrl = repositoryUrl(getRepoOwner(), getRepository());
            if (repoUrl != null) {
                setBrowser(scm, repoUrl);
            }
            return scm;
        } else if (head instanceof PullRequestSCMHead) {
            if (revision instanceof PullRequestSCMRevision) {
                PullRequestSCMRevision prRev = (PullRequestSCMRevision) revision;
                // we rely on GitHub exposing the pull request revision on the target repository
                // TODO determine how we should name the checked out PR branch, as that affects the BRANCH_NAME env var
                SCMHead checkoutHead = head; // should probably have been head.getTarget() but historical
                GitSCM scm = (GitSCM) super.build(checkoutHead, new SCMRevisionImpl(checkoutHead, prRev.getPullHash()));
                if (((PullRequestSCMHead) head).isMerge()) {
                    scm.getExtensions().add(new MergeWith(
                            StringUtils.defaultIfBlank(
                                    ((PullRequestSCMHead) head).getTarget().getName(),
                                    "master?" // OK if not found, just informational anyway
                            ),
                            prRev.getBaseHash()));
                }
                String repoUrl = repositoryUrl(((PullRequestSCMHead) head).getSourceOwner(),
                        ((PullRequestSCMHead) head).getSourceRepo());
                if (repoUrl != null) {
                    setBrowser(scm, repoUrl);
                }
                return scm;
            } else {
                LOGGER.log(Level.WARNING, "Unexpected revision class {0} for {1}", new Object[]{
                        revision.getClass().getName(), head
                });
                GitSCM scm = (GitSCM) super.build(head, revision);
                String repoUrl = repositoryUrl(getRepoOwner(), getRepository());
                if (repoUrl != null) {
                    setBrowser(scm, repoUrl);
                }
                return scm;
            }
        } else {
            GitSCM scm = (GitSCM) super.build(head, /* casting just as an assertion */(SCMRevisionImpl) revision);
            String repoUrl = repositoryUrl(getRepoOwner(), getRepository());
            if (repoUrl != null) {
                setBrowser(scm, repoUrl);
            }
            return scm;
        }
    }

    // TODO remove and replace with scm.setBrowser(repoUrl) directly once baseline Git plugin 3.0.2+
    private void setBrowser(GitSCM scm, String repoUrl) {
        try {
            scm.setBrowser(new GithubWeb(repoUrl));
        } catch (NoSuchMethodError e) {
            Level level;
            long now = System.currentTimeMillis();
            long next = jenkins41244Warning.get();
            if (now >= next) {
                long newNext = now + TimeUnit.MINUTES.toMillis(5);
                if (jenkins41244Warning.compareAndSet(next, newNext)) {
                    level = Level.WARNING;
                } else {
                    level = Level.FINE;
                }
            } else  {
                level = Level.FINE;
            }
            LOGGER.log(level, "JENKINS-41244: GitHub Branch Source cannot set browser url with currently "
                    + "installed version of Git plugin", e);
        }
    }

    /**
     * Tries as best as possible to guess the repository HTML url to use with {@link GithubWeb}.
     * @param owner the owner.
     * @param repo the repository.
     * @return the HTML url of the repository or {@code null} if we could not determine the answer.
     */
    @CheckForNull
    private String repositoryUrl(String owner, String repo) {
        if (repositoryUrl != null) {
            if (repoOwner.equals(owner) && repository.equals(repo)) {
                return repositoryUrl.toExternalForm();
            }
            // hack!
            return repositoryUrl.toExternalForm().replace(repoOwner+"/"+repository, owner+"/"+repo);
        }
        if (StringUtils.isBlank(apiUri)) {
            return "https://github.com/"+ owner+"/"+repo;
        }
        if (StringUtils.endsWith(StringUtils.removeEnd(apiUri, "/"), "/api/v3")) {
            return StringUtils.removeEnd(StringUtils.removeEnd(apiUri, "/"), "api/v3") + owner + "/" + repo;
        }
        return null;
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
    public SCMRevision getTrustedRevision(SCMRevision revision, TaskListener listener)
            throws IOException, InterruptedException {
        if (revision instanceof PullRequestSCMRevision) {
            /*
             * Evaluates whether this pull request is coming from a trusted source.
             * Quickest is to check whether the author of the PR
             * <a href="https://developer.github.com/v3/repos/collaborators/#check-if-a-user-is-a-collaborator">is a
             * collaborator of the repository</a>.
             * By checking <a href="https://developer.github.com/v3/repos/collaborators/#list-collaborators">all
             * collaborators</a>
             * it is possible to further ascertain if they are in a team which was specifically granted push permission,
             * but this is potentially expensive as there might be multiple pages of collaborators to retrieve.
             * TODO since the GitHub API wrapper currently supports neither, we list all collaborator names and check
             * for membership, paying the performance penalty without the benefit of the accuracy.
             */

            if (collaboratorNames == null) {
                listener.getLogger().format("Connecting to %s to obtain list of collaborators for %s/%s%n",
                        apiUri == null ? GITHUB_URL : apiUri, repoOwner, repository);
                StandardCredentials credentials = Connector.lookupScanCredentials(getOwner(), apiUri, scanCredentialsId);
                // Github client and validation
                GitHub github = Connector.connect(apiUri, credentials);
                try {
                    github.checkApiUrlValidity();
                } catch (HttpException e) {
                    listener.getLogger().format("It seems %s is unreachable, assuming no trusted collaborators%n",
                            apiUri == null ? GITHUB_URL : apiUri);
                    collaboratorNames = Collections.singleton(repoOwner);
                }
                if (collaboratorNames == null) {
                    // Input data validation
                    String credentialsName =
                            credentials == null ? "anonymous access" : CredentialsNameProvider.name(credentials);
                    if (credentials != null && !github.isCredentialValid()) {
                        listener.getLogger().format("Invalid scan credentials %s to connect to %s, "
                                        + "assuming no trusted collaborators%n",
                                credentialsName, apiUri == null ? GITHUB_URL : apiUri);
                        collaboratorNames = Collections.singleton(repoOwner);
                    } else {
                        if (!github.isAnonymous()) {
                            listener.getLogger()
                                    .format("Connecting to %s using %s%n", apiUri == null ? GITHUB_URL : apiUri,
                                            credentialsName);
                        } else {
                            listener.getLogger().format("Connecting to %s with no credentials, anonymous access%n",
                                    apiUri == null ? GITHUB_URL : apiUri);
                        }

                        // Input data validation
                        if (repository == null || repository.isEmpty()) {
                            collaboratorNames = Collections.singleton(repoOwner);
                        } else {
                            String fullName = repoOwner + "/" + repository;
                            final GHRepository repo;
                            try {
                                repo = github.getRepository(fullName);
                                repositoryUrl = repo.getHtmlUrl();
                                collaboratorNames = new HashSet<>(repo.getCollaboratorNames());
                            } catch (FileNotFoundException e) {
                                listener.getLogger().format("Invalid scan credentials %s to connect to %s, "
                                                + "assuming no trusted collaborators%n",
                                        credentialsName,
                                        apiUri == null ? GITHUB_URL : apiUri);
                                collaboratorNames = Collections.singleton(repoOwner);
                            }
                        }
                    }
                }
            }
            PullRequestSCMHead head = (PullRequestSCMHead) revision.getHead();
            if (!collaboratorNames.contains(head.getSourceOwner())) {
                PullRequestSCMRevision rev = (PullRequestSCMRevision) revision;
                listener.getLogger().format("Loading trusted files from base branch %s at %s rather than %s%n",
                        head.getTarget().getName(), rev.getBaseHash(), rev.getPullHash());
                return new SCMRevisionImpl(head.getTarget(), rev.getBaseHash());
            }
        }
        return revision;
    }

    /**
     * {@inheritDoc}
     */
    protected boolean isCategoryEnabled(@NonNull SCMHeadCategory category) {
        if (category instanceof ChangeRequestSCMHeadCategory) {
            // only display change requests if this source is enabled for change requests
            return super.isCategoryEnabled(category) && (
                    Boolean.TRUE.equals(buildForkPRHead)
                            || Boolean.TRUE.equals(buildForkPRMerge)
                            || Boolean.TRUE.equals(buildOriginBranchWithPR)
                            || Boolean.TRUE.equals(buildOriginPRHead)
                            || Boolean.TRUE.equals(buildOriginPRMerge)
            );
        }
        return super.isCategoryEnabled(category);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    protected List<Action> retrieveActions(@NonNull SCMHead head,
                                           @CheckForNull SCMHeadEvent event,
                                           @NonNull TaskListener listener) throws IOException, InterruptedException {
        // TODO when we have support for trusted events, use the details from event if event was from trusted source
        List<Action> result = new ArrayList<>();
        SCMSourceOwner owner = getOwner();
        if (owner instanceof Actionable) {
            GitHubLink repoLink = ((Actionable) owner).getAction(GitHubLink.class);
            if (repoLink != null) {
                String url;
                ObjectMetadataAction metadataAction = null;
                if (head instanceof PullRequestSCMHead) {
                    // pull request to this repository
                    url = repoLink.getUrl() + "/pull/" + ((PullRequestSCMHead) head).getNumber();
                    metadataAction = pullRequestMetadataCache.get(((PullRequestSCMHead) head).getNumber());
                    if (metadataAction == null) {
                        // best effort
                        metadataAction = new ObjectMetadataAction(null, null, url);
                    }
                } else {
                    // branch in this repository
                    url = repoLink.getUrl() + "/tree/" + head.getName();
                    metadataAction = new ObjectMetadataAction(head.getName(), null, url);
                }
                result.add(new GitHubLink("icon-github-branch", url));
                result.add(metadataAction);
            }
            if (head instanceof BranchSCMHead) {
                for (GitHubDefaultBranch p : ((Actionable) owner).getActions(GitHubDefaultBranch.class)) {
                    if (StringUtils.equals(getRepoOwner(), p.getRepoOwner())
                            && StringUtils.equals(repository, p.getRepository())
                            && StringUtils.equals(p.getDefaultBranch(), head.getName())) {
                        result.add(new PrimaryInstanceMetadataAction());
                        break;
                    }
                }
            }
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    protected List<Action> retrieveActions(@CheckForNull SCMSourceEvent event,
                                           @NonNull TaskListener listener) throws IOException {
        // TODO when we have support for trusted events, use the details from event if event was from trusted source
        List<Action> result = new ArrayList<>();
        result.add(new GitHubRepoMetadataAction());
        StandardCredentials credentials = Connector.lookupScanCredentials(getOwner(), apiUri, scanCredentialsId);
        GitHub hub = Connector.connect(apiUri, credentials);
        try {
            hub.checkApiUrlValidity();
        } catch (HttpException e) {
            listener.getLogger().format("It seems %s is unreachable%n",
                    apiUri == null ? GITHUB_URL : apiUri);
            return result;
        }
        String credentialsName = credentials == null ? "anonymous access" : CredentialsNameProvider.name(credentials);
        if (credentials != null && !hub.isCredentialValid()) {
            throw new AbortException(String.format("Invalid scan credentials %s to connect to %s, skipping",
                    credentialsName, apiUri == null ? GITHUB_URL : apiUri));
        }
        if (!hub.isAnonymous()) {
            listener.getLogger().format("Connecting to %s using %s%n", apiUri == null ? GITHUB_URL : apiUri,
                    credentialsName);
        } else {
            listener.getLogger()
                    .format("Connecting to %s using anonymous access%n", apiUri == null ? GITHUB_URL : apiUri);
        }
        GHRepository repo;
        try {
            repo = hub.getRepository(getRepoOwner() + '/' + repository);
        repositoryUrl = repo.getHtmlUrl();
        } catch (FileNotFoundException e) {
            throw new AbortException(String.format("Invalid scan credentials when using %s to connect to %s/%s on %s",
                    credentialsName, repoOwner, repository, apiUri == null ? GITHUB_URL : apiUri));
        }
        result.add(new ObjectMetadataAction(null, repo.getDescription(), Util.fixEmpty(repo.getHomepage())));
        result.add(new GitHubLink("icon-github-repo", repo.getHtmlUrl()));
        if (StringUtils.isNotBlank(repo.getDefaultBranch())) {
            result.add(new GitHubDefaultBranch(getRepoOwner(), repository, repo.getDefaultBranch()));
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterSave() {
        SCMSourceOwner owner = getOwner();
        if (owner != null) {
            GitHubWebHook.get().registerHookFor(owner);
        }
    }

    @Extension
    public static class DescriptorImpl extends SCMSourceDescriptor {

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
            return Messages.GitHubSCMSource_DisplayName();
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
                                                       @QueryParameter String apiUri,
                                                       @QueryParameter String scanCredentialsId) {
            return Connector.checkScanCredentials(context, apiUri, scanCredentialsId);
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

        public boolean isApiUriSelectable() {
            return !GitHubConfiguration.get().getEndpoints().isEmpty();
        }

        public ListBoxModel doFillCheckoutCredentialsIdItems(@AncestorInPath SCMSourceOwner context, @QueryParameter String apiUri) {
            return Connector.listCheckoutCredentials(context, apiUri);
        }

        public ListBoxModel doFillScanCredentialsIdItems(@AncestorInPath SCMSourceOwner context, @QueryParameter String apiUri) {
            return Connector.listScanCredentials(context, apiUri);
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
                    LOGGER.log(Level.FINE, "as {0} looking for repositories in {1}", new Object[] {scanCredentialsId, repoOwner});
                    for (GHRepository repo : org.listRepositories(100)) {
                        LOGGER.log(Level.FINE, "as {0} found {1}/{2}", new Object[] {scanCredentialsId, repoOwner, repo.getName()});
                        result.add(repo.getName());
                    }
                    LOGGER.log(Level.FINE, "as {0} result of {1} is {2}", new Object[] {scanCredentialsId, repoOwner, result});
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
                    for (GHRepository repo : user.listRepositories(100)) {
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

        @NonNull
        @Override
        protected SCMHeadCategory[] createCategories() {
            return new SCMHeadCategory[]{
                    new UncategorizedSCMHeadCategory(Messages._GitHubSCMSource_UncategorizedCategory()),
                    new ChangeRequestSCMHeadCategory(Messages._GitHubSCMSource_ChangeRequestCategory())
                    // TODO add support for tags and maybe feature branch identification
            };
        }

    }

}
