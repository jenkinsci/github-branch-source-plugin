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
import org.kohsuke.github.GHRef;
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
import static org.jenkinsci.plugins.github.config.GitHubServerConfig.GITHUB_URL;

public class GitHubSCMSource extends AbstractGitSCMSource {

    private final String apiUri;

    /** Credentials for actual clone; may be SSH private key. */
    private final String checkoutCredentialsId;

    /** Credentials for GitHub API; currently only supports username/password (personal access token). */
    private final String scanCredentialsId;

    private final String repoOwner;

    private final String repository;

    private @Nonnull String includes = DescriptorImpl.defaultIncludes;

    private @Nonnull String excludes = DescriptorImpl.defaultExcludes;

    @DataBoundConstructor
    public GitHubSCMSource(String id, String apiUri, String checkoutCredentialsId, String scanCredentialsId, String repoOwner, String repository) {
        super(id);
        this.apiUri = Util.fixEmpty(apiUri);
        this.repoOwner = repoOwner;
        this.repository = repository;
        this.scanCredentialsId = Util.fixEmpty(scanCredentialsId);
        this.checkoutCredentialsId = checkoutCredentialsId;
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
        return new ArrayList<RefSpec>(Arrays.asList(new RefSpec("+refs/heads/*:refs/remotes/origin/*"),
                new RefSpec("+refs/pull/*/merge:refs/remotes/origin/pr/*")));
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

        listener.getLogger().format("%n  Getting remote branches...%n");
        int branches = 0;
        for (Map.Entry<String,GHBranch> entry : repo.getBranches().entrySet()) {
            final String branchName = entry.getKey();
            if (isExcluded(branchName)) {
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

        listener.getLogger().format("%n  Getting remote pull requests...%n");
        int pullrequests = 0;
        for (GHPullRequest ghPullRequest : repo.getPullRequests(GHIssueState.OPEN)) {
            PullRequestSCMHead head = new PullRequestSCMHead(ghPullRequest);
            final String branchName = head.getName();
            listener.getLogger().format("%n    Checking pull request %s%n", HyperlinkNote.encodeTo(ghPullRequest.getHtmlUrl().toString(), "#" + branchName));
            if (criteria != null) {
                SCMSourceCriteria.Probe probe = getProbe(branchName, "pull request", "refs/pull/" + head.getNumber() + "/head", repo, listener);
                if (criteria.isHead(probe, listener)) {
                    // FYI https://developer.github.com/v3/pulls/#response-1
                    Boolean mergeable = ghPullRequest.getMergeable();
                    if (Boolean.FALSE.equals(mergeable)) {
                        listener.getLogger().format("      Not mergeable, but it will be included%n");
                    }
                    listener.getLogger().format("    Met criteria%n");
                } else {
                    listener.getLogger().format("    Does not meet criteria%n");
                    continue;
                }
            }
            String trustedBase = trustedReplacement(repo, ghPullRequest);
            SCMRevision hash;
            if (trustedBase == null) {
                hash = new SCMRevisionImpl(head, ghPullRequest.getHead().getSha());
            } else {
                listener.getLogger().format("    (not from a trusted source)%n");
                hash = new UntrustedPullRequestSCMRevision(head, ghPullRequest.getHead().getSha(), trustedBase);
            }
            observer.observe(head, hash);
            if (!observer.isObserving()) {
                return;
            }
            pullrequests++;
        }
        listener.getLogger().format("%n  %d pull requests were processed%n", pullrequests);

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
        GHRef ref;
        if (head instanceof PullRequestSCMHead) {
            int number = ((PullRequestSCMHead) head).getNumber();
            ref = repo.getRef("pull/" + number + "/merge");
            // getPullRequests makes an extra API call, but we need its current .base.sha
            String trustedBase = trustedReplacement(repo, repo.getPullRequest(number));
            if (trustedBase != null) {
                return new UntrustedPullRequestSCMRevision(head, ref.getObject().getSha(), trustedBase);
            }
        } else {
            ref = repo.getRef("heads/" + head.getName());
        }
        return new SCMRevisionImpl(head, ref.getObject().getSha());
    }

    @Override
    public SCMRevision getTrustedRevision(SCMRevision revision, TaskListener listener) throws IOException, InterruptedException {
        if (revision instanceof UntrustedPullRequestSCMRevision) {
            PullRequestSCMHead head = (PullRequestSCMHead) revision.getHead();
            UntrustedPullRequestSCMRevision rev = (UntrustedPullRequestSCMRevision) revision;
            listener.getLogger().println("Loading trusted files from target branch at " + rev.baseHash + " rather than " + rev.getHash());
            return new SCMRevisionImpl(head, rev.baseHash);
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
     * @return the base revision, for an untrusted PR; null for a trusted PR
     * @see <a href="https://developer.github.com/v3/pulls/#get-a-single-pull-request">PR metadata</a>
     * @see <a href="http://stackoverflow.com/questions/15096331/github-api-how-to-find-the-branches-of-a-pull-request#comment54931031_15096596">base revision oddity</a>
     */
    private @CheckForNull String trustedReplacement(@Nonnull GHRepository repo, @Nonnull GHPullRequest ghPullRequest) throws IOException {
        if (repo.getCollaboratorNames().contains(ghPullRequest.getUser().getLogin())) {
            return null;
        } else {
            return ghPullRequest.getBase().getSha();
        }
    }

    @Extension public static class DescriptorImpl extends SCMSourceDescriptor {

        private static final Logger LOGGER = Logger.getLogger(DescriptorImpl.class.getName());

        public static final String defaultIncludes = "*";
        public static final String defaultExcludes = "";
        public static final String ANONYMOUS = "ANONYMOUS";
        public static final String SAME = "SAME";

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
                        LOGGER.log(Level.WARNING, "Exception validating credentials " + CredentialsNameProvider.name(credentials) + " on " + apiUri);
                        return FormValidation.error("Exception validating credentials");
                    }
                }
            } else {
                return FormValidation.warning("Credentials are recommended");
            }
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
                        LOGGER.log(Level.WARNING, "Exception retrieving the repositories of the owner " + repoOwner +
                                " on " + apiUri + " with credentials " + CredentialsNameProvider.name(credentials));
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
                    LOGGER.log(Level.FINE, "There is not any GH Organization named " + repoOwner);
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
                    LOGGER.log(Level.FINE, "There is not any GH User named " + repoOwner);
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
