/*
 * The MIT License
 *
 * Copyright 2015 CloudBees, Inc.
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
import hudson.Util;
import hudson.console.HyperlinkNote;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.net.ssl.SSLHandshakeException;

import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.SCMSourceDescriptor;
import jenkins.scm.api.SCMSourceOwner;
import org.kohsuke.github.GHMyself;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

public abstract class AbstractGitHubSCMSource extends AbstractGitSCMSource {

    private final String apiUri;

    /** Credentials for actual clone; may be SSH private key. */
    private final String checkoutCredentialsId;

    /** Credentials for GitHub API; currently only supports username/password (access token). */
    private final String scanCredentialsId;

    private final String repoOwner;

    private final String repository;

    private @Nonnull String includes = AbstractGitHubSCMSourceDescriptor.defaultIncludes;

    private @Nonnull String excludes = AbstractGitHubSCMSourceDescriptor.defaultExcludes;

    protected AbstractGitHubSCMSource(String id, String apiUri, String checkoutCredentialsId, String scanCredentialsId, String repoOwner, String repository) {
        super(id);
        this.apiUri = Util.fixEmpty(apiUri);
        this.repoOwner = repoOwner;
        this.repository = repository;
        this.scanCredentialsId = Util.fixEmpty(scanCredentialsId);
        this.checkoutCredentialsId = checkoutCredentialsId;
    }

    @Nonnull
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
        if (AbstractGitHubSCMSourceDescriptor.ANONYMOUS.equals(checkoutCredentialsId)) {
            return null;
        } else if (AbstractGitHubSCMSourceDescriptor.SAME.equals(checkoutCredentialsId)) {
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
    public AbstractGitHubSCMSourceDescriptor getDescriptor() {
        return (AbstractGitHubSCMSourceDescriptor) super.getDescriptor();
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
        if (credentials == null) {
            listener.getLogger().println("No scan credentials, skipping");
            return;
        }
        listener.getLogger().format("Connecting to %s using %s%n", getDescriptor().getDisplayName(), CredentialsNameProvider.name(credentials));
        GitHub github = Connector.connect(apiUri, credentials);
        String fullName = repoOwner + "/" + repository;
        /* TODO call GitHubBuilder withRateLimitHandler to notify listener so we do not get stuck without messages in something like
        java.lang.Thread.State: TIMED_WAITING (sleeping)
                at java.lang.Thread.sleep(Native Method)
                at org.kohsuke.github.RateLimitHandler$1.onError(RateLimitHandler.java:38)
                at org.kohsuke.github.Requester.handleApiError(Requester.java:486)
                at org.kohsuke.github.Requester._to(Requester.java:245)
                at org.kohsuke.github.Requester.to(Requester.java:191)
                at org.kohsuke.github.GitHub.getRepository(GitHub.java:320)
        */
        final GHRepository repo = github.getRepository(fullName);
        listener.getLogger().format("Looking up %s%n", HyperlinkNote.encodeTo(repo.getHtmlUrl().toString(), fullName));
        doRetrieve(observer, listener, repo);
        listener.getLogger().format("%nDone examining %s%n%n", fullName);
    }

    protected abstract void doRetrieve(SCMHeadObserver observer, TaskListener listener, GHRepository repo) throws IOException, InterruptedException;

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

    protected /*abstract*/ SCMRevision doRetrieve(SCMHead head, TaskListener listener, GHRepository repo) throws IOException, InterruptedException {
        listener.error("Please implement " + getClass().getName() + ".doRetrieve(SCMHead, TaskListener, GHRepository)");
        SCMHeadObserver.Selector selector = SCMHeadObserver.select(head);
        doRetrieve(selector, listener, repo);
        return selector.result();
    }

    public static abstract class AbstractGitHubSCMSourceDescriptor extends SCMSourceDescriptor {

        private static final Logger LOGGER = Logger.getLogger(AbstractGitHubSCMSourceDescriptor.class.getName());

        public static final String defaultIncludes = "*";
        public static final String defaultExcludes = "";
        public static final String ANONYMOUS = "ANONYMOUS";
        public static final String SAME = "SAME";

        public FormValidation doCheckScanCredentialsId(@QueryParameter String value) {
            if (!value.isEmpty()) {
                return FormValidation.ok();
            } else {
                return FormValidation.warning("Credentials are required");
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

        public ListBoxModel doFillRepositoryItems(@AncestorInPath SCMSourceOwner context,
                                                  @QueryParameter String apiUri,
                                                  @QueryParameter String scanCredentialsId,
                                                  @QueryParameter String repoOwner) {
            ListBoxModel result = new ListBoxModel();
            repoOwner = Util.fixEmptyAndTrim(repoOwner);
            if (repoOwner == null) {
                return result;
            }
            try {
                GitHub github = Connector.connect(apiUri, Connector.lookupScanCredentials(context, apiUri, scanCredentialsId));

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
