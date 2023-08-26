/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
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
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.Item;
import hudson.model.Queue;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.browser.GithubWeb;
import hudson.security.ACL;
import java.net.URL;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.plugins.git.GitSCMBuilder;
import jenkins.plugins.git.MergeWithGitSCMExtension;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.api.mixin.TagSCMHead;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.transport.RefSpec;
import org.jenkinsci.plugins.github.config.GitHubServerConfig;

/**
 * Builds a {@link GitSCM} for {@link GitHubSCMSource}.
 *
 * @since 2.2.0
 */
@SuppressFBWarnings("DMI_RANDOM_USED_ONLY_ONCE") // https://github.com/spotbugs/spotbugs/issues/1539
public class GitHubSCMBuilder extends GitSCMBuilder<GitHubSCMBuilder> {

    private static final Random ENTROPY = new Random();
    /** Singleton instance of {@link HttpsRepositoryUriResolver}. */
    static final HttpsRepositoryUriResolver HTTPS = new HttpsRepositoryUriResolver();
    /** Singleton instance of {@link SshRepositoryUriResolver}. */
    static final SshRepositoryUriResolver SSH = new SshRepositoryUriResolver();
    /** The GitHub API suffix for GitHub Server. */
    static final String API_V3 = "api/v3";
    /** The context within which credentials should be resolved. */
    @CheckForNull
    private final SCMSourceOwner context;
    /** The API URL */
    @NonNull
    private final String apiUri;
    /** The repository owner. */
    @NonNull
    private final String repoOwner;
    /** The repository name. */
    @NonNull
    private final String repository;
    /**
     * The definitive HTML user-facing URL of the repository (as provided by the GitHub API) if
     * available.
     */
    @CheckForNull
    private final URL repositoryUrl;
    /** The repository name. */
    @NonNull
    private RepositoryUriResolver uriResolver = GitHubSCMBuilder.HTTPS;

    /**
     * Constructor.
     *
     * @param source the {@link GitHubSCMSource}.
     * @param head the {@link SCMHead}
     * @param revision the (optional) {@link SCMRevision}
     */
    public GitHubSCMBuilder(
            @NonNull GitHubSCMSource source, @NonNull SCMHead head, @CheckForNull SCMRevision revision) {
        super(head, revision, /*dummy value*/ guessRemote(source), source.getCredentialsId());
        this.context = source.getOwner();
        apiUri = StringUtils.defaultIfBlank(source.getApiUri(), GitHubServerConfig.GITHUB_URL);
        repoOwner = source.getRepoOwner();
        repository = source.getRepository();
        repositoryUrl = source.getResolvedRepositoryUrl();
        // now configure the ref specs
        withoutRefSpecs();
        String repoUrl;
        if (head instanceof PullRequestSCMHead) {
            PullRequestSCMHead h = (PullRequestSCMHead) head;
            withRefSpec("+refs/pull/" + h.getId() + "/head:refs/remotes/@{remote}/" + head.getName());
            repoUrl = repositoryUrl(h.getSourceOwner(), h.getSourceRepo());
        } else if (head instanceof TagSCMHead) {
            withRefSpec("+refs/tags/" + head.getName() + ":refs/tags/" + head.getName());
            repoUrl = repositoryUrl(repoOwner, repository);
        } else {
            withRefSpec("+refs/heads/" + head.getName() + ":refs/remotes/@{remote}/" + head.getName());
            repoUrl = repositoryUrl(repoOwner, repository);
        }
        // pre-configure the browser
        if (repoUrl != null) {
            withBrowser(new GithubWeb(repoUrl));
        }
        withCredentials(credentialsId(), null);
    }

    /**
     * Tries to guess the HTTPS URL of the Git repository.
     *
     * @param source the source.
     * @return the (possibly incorrect) best guess at the Git repository URL.
     */
    private static String guessRemote(GitHubSCMSource source) {
        String apiUri = StringUtils.removeEnd(source.getApiUri(), "/");
        if (StringUtils.isBlank(apiUri) || GitHubServerConfig.GITHUB_URL.equals(apiUri)) {
            apiUri = "https://github.com";
        } else {
            apiUri = StringUtils.removeEnd(apiUri, "/" + API_V3);
        }
        return apiUri + "/" + source.getRepoOwner() + "/" + source.getRepository() + ".git";
    }

    /**
     * Tries as best as possible to guess the repository HTML url to use with {@link GithubWeb}.
     *
     * @param owner the owner.
     * @param repo the repository.
     * @return the HTML url of the repository or {@code null} if we could not determine the answer.
     */
    @CheckForNull
    public final String repositoryUrl(String owner, String repo) {
        if (repositoryUrl != null) {
            if (repoOwner.equals(owner) && repository.equals(repo)) {
                return repositoryUrl.toExternalForm();
            }
            // hack!
            return repositoryUrl.toExternalForm().replace(repoOwner + "/" + repository, owner + "/" + repo);
        }
        if (StringUtils.isBlank(apiUri) || GitHubServerConfig.GITHUB_URL.equals(apiUri)) {
            return "https://github.com/" + owner + "/" + repo;
        }
        if (StringUtils.endsWith(StringUtils.removeEnd(apiUri, "/"), "/" + API_V3)) {
            return StringUtils.removeEnd(StringUtils.removeEnd(apiUri, "/"), API_V3) + owner + "/" + repo;
        }
        return null;
    }

    /**
     * Returns a {@link RepositoryUriResolver} according to credentials configuration.
     *
     * @return a {@link RepositoryUriResolver}
     */
    @NonNull
    public final RepositoryUriResolver uriResolver() {
        return uriResolver;
    }

    /**
     * Configures the {@link IdCredentials#getId()} of the {@link Credentials} to use when connecting
     * to the {@link #remote()}
     *
     * @param credentialsId the {@link IdCredentials#getId()} of the {@link Credentials} to use when
     *     connecting to the {@link #remote()} or {@code null} to let the git client choose between
     *     providing its own credentials or connecting anonymously.
     * @param uriResolver the {@link RepositoryUriResolver} of the {@link Credentials} to use or
     *     {@code null} to detect the the protocol based on the credentialsId. Defaults to HTTP if
     *     credentials are {@code null}. Enables support for blank SSH credentials.
     * @return {@code this} for method chaining.
     */
    @NonNull
    public GitHubSCMBuilder withCredentials(String credentialsId, RepositoryUriResolver uriResolver) {
        if (uriResolver == null) {
            uriResolver = uriResolver(context, apiUri, credentialsId);
        }

        this.uriResolver = uriResolver;
        return withCredentials(credentialsId);
    }

    /**
     * Returns a {@link RepositoryUriResolver} according to credentials configuration.
     *
     * @param context the context within which to resolve the credentials.
     * @param apiUri the API url
     * @param credentialsId the credentials.
     * @return a {@link RepositoryUriResolver}
     */
    @NonNull
    public static RepositoryUriResolver uriResolver(
            @CheckForNull Item context, @NonNull String apiUri, @CheckForNull String credentialsId) {
        if (credentialsId == null) {
            return HTTPS;
        } else {
            StandardCredentials credentials = CredentialsMatchers.firstOrNull(
                    CredentialsProvider.lookupCredentials(
                            StandardCredentials.class,
                            context,
                            context instanceof Queue.Task
                                    ? ((Queue.Task) context).getDefaultAuthentication()
                                    : ACL.SYSTEM,
                            URIRequirementBuilder.create()
                                    .withHostname(RepositoryUriResolver.hostnameFromApiUri(apiUri))
                                    .build()),
                    CredentialsMatchers.allOf(
                            CredentialsMatchers.withId(credentialsId),
                            CredentialsMatchers.instanceOf(StandardCredentials.class)));
            if (credentials instanceof SSHUserPrivateKey) {
                return SSH;
            } else {
                // Defaults to HTTP/HTTPS
                return HTTPS;
            }
        }
    }

    /**
     * Updates the {@link GitSCMBuilder#withRemote(String)} based on the current {@link #head()} and
     * {@link #revision()}.
     *
     * <p>Will be called automatically by {@link #build()} but exposed in case the correct remote is
     * required after changing the {@link #withCredentials(String)}.
     *
     * @return {@code this} for method chaining.
     */
    @NonNull
    public final GitHubSCMBuilder withGitHubRemote() {
        withRemote(uriResolver().getRepositoryUri(apiUri, repoOwner, repository));
        final SCMHead h = head();
        String repoUrl;
        if (h instanceof PullRequestSCMHead) {
            final PullRequestSCMHead head = (PullRequestSCMHead) h;
            repoUrl = repositoryUrl(head.getSourceOwner(), head.getSourceRepo());
        } else {
            repoUrl = repositoryUrl(repoOwner, repository);
        }
        if (repoUrl != null) {
            withBrowser(new GithubWeb(repoUrl));
        }
        return this;
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public GitSCM build() {
        final SCMHead h = head();
        final SCMRevision r = revision();
        try {
            withGitHubRemote();

            if (h instanceof PullRequestSCMHead) {
                PullRequestSCMHead head = (PullRequestSCMHead) h;
                if (head.isMerge()) {
                    // add the target branch to ensure that the revision we want to merge is also available
                    String name = head.getTarget().getName();
                    String localName = "remotes/" + remoteName() + "/" + name;
                    Set<String> localNames = new HashSet<>();
                    boolean match = false;
                    String targetSrc = Constants.R_HEADS + name;
                    String targetDst = Constants.R_REMOTES + remoteName() + "/" + name;
                    for (RefSpec b : asRefSpecs()) {
                        String dst = b.getDestination();
                        assert dst.startsWith(Constants.R_REFS) : "All git references must start with refs/";
                        if (targetSrc.equals(b.getSource())) {
                            if (targetDst.equals(dst)) {
                                match = true;
                            } else {
                                // pick up the configured destination name
                                localName = dst.substring(Constants.R_REFS.length());
                                match = true;
                            }
                        } else {
                            localNames.add(dst.substring(Constants.R_REFS.length()));
                        }
                    }
                    if (!match) {
                        if (localNames.contains(localName)) {
                            // conflict with intended name
                            localName = "remotes/" + remoteName() + "/upstream-" + name;
                        }
                        if (localNames.contains(localName)) {
                            // conflict with intended alternative name
                            localName = "remotes/" + remoteName() + "/pr-" + head.getNumber() + "-upstream-" + name;
                        }
                        if (localNames.contains(localName)) {
                            while (localNames.contains(localName)) {
                                localName = "remotes/"
                                        + remoteName()
                                        + "/pr-"
                                        + head.getNumber()
                                        + "-upstream-"
                                        + name
                                        + "-"
                                        + Integer.toHexString(ENTROPY.nextInt(Integer.MAX_VALUE));
                            }
                        }
                        withRefSpec("+refs/heads/" + name + ":refs/" + localName);
                    }
                    withExtension(new MergeWithGitSCMExtension(
                            localName,
                            r instanceof PullRequestSCMRevision ? ((PullRequestSCMRevision) r).getBaseHash() : null));
                }
                if (r instanceof PullRequestSCMRevision) {
                    PullRequestSCMRevision rev = (PullRequestSCMRevision) r;
                    withRevision(new AbstractGitSCMSource.SCMRevisionImpl(head, rev.getPullHash()));
                }
            }
            return super.build();
        } finally {
            withHead(h);
            withRevision(r);
        }
    }
}
