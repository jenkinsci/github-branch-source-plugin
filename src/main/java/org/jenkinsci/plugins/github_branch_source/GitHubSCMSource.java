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
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
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
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHMyself;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRef;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static hudson.model.Items.XSTREAM2;

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
        GitHub github = Connector.connect(apiUri, credentials);
        try {
            if (credentials != null && !github.isCredentialValid()) {
                listener.getLogger().format("Invalid scan credentials, skipping%n");
                return;
            }
            if (!github.isAnonymous()) {
                listener.getLogger().format("Connecting to %s using %s%n", getDescriptor().getDisplayName(),
                        CredentialsNameProvider.name(credentials));
            } else {
                listener.getLogger().format("Connecting to %s using anonymous access%n", getDescriptor().getDisplayName());
            }
            String fullName = repoOwner + "/" + repository;
            final GHRepository repo = github.getRepository(fullName);
            listener.getLogger().format("Looking up %s%n", HyperlinkNote.encodeTo(repo.getHtmlUrl().toString(), fullName));
            doRetrieve(observer, listener, repo);
            listener.getLogger().format("%nDone examining %s%n%n", fullName);
        } catch (RateLimitExceededException rle) {
            listener.getLogger().format("%n%s%n%n", rle.getMessage());
            throw new InterruptedException();
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

        if (repo.isPrivate()) {
            listener.getLogger().format("%n  Getting remote pull requests...%n");
            int pullrequests = 0;
            for (GHPullRequest ghPullRequest : repo.getPullRequests(GHIssueState.OPEN)) {
                int number = ghPullRequest.getNumber();
                SCMHead head = new PullRequestSCMHead(number);
                final String branchName = head.getName();
                listener.getLogger().format("%n    Checking pull request %s%n", HyperlinkNote.encodeTo(ghPullRequest.getHtmlUrl().toString(), "#" + branchName));
                // FYI https://developer.github.com/v3/pulls/#response-1
                Boolean mergeable = ghPullRequest.getMergeable();
                if (!Boolean.TRUE.equals(mergeable)) {
                    listener.getLogger().format("    Not mergeable, skipping%n%n");
                    continue;
                }
                if (repo.getOwner().equals(ghPullRequest.getHead().getUser())) {
                    listener.getLogger().format("    Submitted from origin repository, skipping%n%n");
                    continue;
                }
                if (criteria != null) {
                    SCMSourceCriteria.Probe probe = getProbe(branchName, "pull request", "refs/pull/" + number + "/head", repo, listener);
                    if (criteria.isHead(probe, listener)) {
                        listener.getLogger().format("    Met criteria%n");
                    } else {
                        listener.getLogger().format("    Does not meet criteria%n");
                        continue;
                    }
                }
                SCMRevision hash = new SCMRevisionImpl(head, ghPullRequest.getHead().getSha());
                observer.observe(head, hash);
                if (!observer.isObserving()) {
                    return;
                }
                pullrequests++;
            }
            listener.getLogger().format("%n  %d pull requests were processed%n", pullrequests);
        } else {
            listener.getLogger().format("%n  Skipping pull requests for public repositories%n");
        }

    }

    /**
     * Gets the username associated with the scan credentials
     */
    public String getScanCredentialUsername() {
        StandardCredentials credentials = Connector.lookupScanCredentials(getOwner(), apiUri, scanCredentialsId);
        UsernamePasswordCredentials creds = (UsernamePasswordCredentials) credentials;
        if (credentials != null) {
            return creds.getUsername();
        }
        return null;
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
                    repo.getFileContent(path, ref);
                    listener.getLogger().format("      %s exists in this %s%n", path, thing);
                    return true;
                } catch (FileNotFoundException x) {
                    listener.getLogger().format("      %s does not exist in this %s%n", path, thing);
                    return false;
                }
            }
        };
    }

    @Override
    @CheckForNull
    protected SCMRevision retrieve(SCMHead head, TaskListener listener) throws IOException, InterruptedException {
        StandardCredentials credentials = Connector.lookupScanCredentials(getOwner(), apiUri, scanCredentialsId);
        if (credentials == null) {
            listener.getLogger().println("No scan credentials, skipping");
            return null;
        }
        listener.getLogger().format("Connecting to %s using %s%n", getDescriptor().getDisplayName(), CredentialsNameProvider.name(credentials));
        GitHub github = Connector.connect(apiUri, credentials);
        String fullName = repoOwner + "/" + repository;
        GHRepository repo = github.getRepository(fullName);
        return doRetrieve(head, listener, repo);
    }

    protected SCMRevision doRetrieve(SCMHead head, TaskListener listener, GHRepository repo) throws IOException, InterruptedException {
        GHRef ref;
        if (head instanceof PullRequestSCMHead) {
            int number = ((PullRequestSCMHead) head).getNumber();
            ref = repo.getRef("pull/" + number + "/merge");
        } else {
            ref = repo.getRef("heads/" + head.getName());
        }
        return new SCMRevisionImpl(head, ref.getObject().getSha());
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
        public FormValidation doCheckScanCredentialsId(@AncestorInPath SCMSourceOwner context,
                @QueryParameter String scanCredentialsId, @QueryParameter String apiUri) {
            if (!scanCredentialsId.isEmpty()) {
                StandardCredentials credentials = Connector.lookupScanCredentials(context, apiUri, scanCredentialsId);
                if (credentials == null) {
                    FormValidation.error("Invalid credentials");
                } else {
                    try {
                        GitHub connector = Connector.connect(apiUri, credentials);
                        if (connector.isCredentialValid()) {
                            return FormValidation.ok();
                        }
                    } catch (IOException e) {
                        // ignore, never thrown
                    }
                }
                return FormValidation.error("Invalid credentials");
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
            ListBoxModel result = new ListBoxModel();
            repoOwner = Util.fixEmptyAndTrim(repoOwner);
            if (repoOwner == null) {
                return result;
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
                        LOGGER.log(Level.WARNING, e.getMessage());
                    }
                    if (myself != null && repoOwner.equals(myself.getLogin())) {
                        for (String name : myself.getAllRepositories().keySet()) {
                            result.add(name);
                        }
                        return result;
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
                if (org != null && repoOwner.equals(org.getLogin())) {
                    for (GHRepository repo : org.listRepositories()) {
                        result.add(repo.getName());
                    }
                    return result;
                }

                GHUser user = null;
                try {
                    user = github.getUser(repoOwner);
                } catch (FileNotFoundException fnf) {
                    LOGGER.log(Level.FINE, "There is not any GH User named " + repoOwner);
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, e.getMessage());
                }
                if (user != null && repoOwner.equals(user.getLogin())) {
                    for (GHRepository repo : user.listRepositories()) {
                        result.add(repo.getName());
                    }
                    return result;
                }
            } catch (SSLHandshakeException he) {
                LOGGER.log(Level.SEVERE, he.getMessage());
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, e.getMessage());
            }
            return result;
        }

    }

}
