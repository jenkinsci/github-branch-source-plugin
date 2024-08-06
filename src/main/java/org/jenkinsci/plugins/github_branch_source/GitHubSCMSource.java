/*
 * The MIT License
 *
 * Copyright 2015-2017 CloudBees, Inc.
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

import static hudson.Functions.isWindows;
import static hudson.model.Items.XSTREAM2;
import static org.apache.commons.lang.StringUtils.isBlank;
import static org.apache.commons.lang.StringUtils.removeEnd;
import static org.jenkinsci.plugins.github_branch_source.Connector.isCredentialValid;
import static org.jenkinsci.plugins.github_branch_source.GitHubSCMBuilder.API_V3;

import com.cloudbees.jenkins.GitHubWebHook;
import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.AbortException;
import hudson.Extension;
import hudson.Functions;
import hudson.RestrictedSince;
import hudson.Util;
import hudson.console.HyperlinkNote;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Action;
import hudson.model.Actionable;
import hudson.model.Item;
import hudson.model.TaskListener;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.scm.SCM;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.LogTaskListener;
import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectStreamException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletResponse;
import jenkins.model.Jenkins;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.plugins.git.GitTagSCMRevision;
import jenkins.plugins.git.MergeWithGitSCMExtension;
import jenkins.plugins.git.traits.GitBrowserSCMSourceTrait;
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
import jenkins.scm.api.metadata.ContributorMetadataAction;
import jenkins.scm.api.metadata.ObjectMetadataAction;
import jenkins.scm.api.metadata.PrimaryInstanceMetadataAction;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import jenkins.scm.api.trait.SCMSourceRequest;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMTrait;
import jenkins.scm.api.trait.SCMTraitDescriptor;
import jenkins.scm.impl.ChangeRequestSCMHeadCategory;
import jenkins.scm.impl.TagSCMHeadCategory;
import jenkins.scm.impl.UncategorizedSCMHeadCategory;
import jenkins.scm.impl.form.NamedArrayList;
import jenkins.scm.impl.trait.Discovery;
import jenkins.scm.impl.trait.Selection;
import jenkins.scm.impl.trait.WildcardSCMHeadFilterTrait;
import jenkins.util.SystemProperties;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.lib.Constants;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.github.config.GitHubServerConfig;
import org.jenkinsci.plugins.structs.describable.CustomDescribableModel;
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHException;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHMyself;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHPermissionType;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRef;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTagObject;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.HttpException;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.interceptor.RequirePOST;

public class GitHubSCMSource extends AbstractGitSCMSource {

    public static final String VALID_GITHUB_REPO_NAME = "^[0-9A-Za-z._-]+$";
    public static final String VALID_GITHUB_USER_NAME =
            "^(?=[A-Za-z0-9-_.]{1,39}$)([A-Za-z0-9]((?:[A-Za-z0-9]+|[-.](?=[A-Za-z0-9]+))*)(_(?:[A-Za-z0-9]+))?)";
    public static final String VALID_GIT_SHA1 = "^[a-fA-F0-9]{40}$";
    public static final String GITHUB_URL = GitHubServerConfig.GITHUB_URL;
    public static final String GITHUB_COM = "github.com";
    private static final Logger LOGGER = Logger.getLogger(GitHubSCMSource.class.getName());
    private static final String R_PULL = Constants.R_REFS + "pull/";
    /** How long to delay events received from GitHub in order to allow the API caches to sync. */
    private static /*mostly final*/ int eventDelaySeconds =
            Math.min(300, Math.max(0, Integer.getInteger(GitHubSCMSource.class.getName() + ".eventDelaySeconds", 5)));
    /**
     * How big (in megabytes) an on-disk cache to keep of GitHub API responses. Cache is per repo, per
     * credentials.
     */
    private static /*mostly final*/ int cacheSize = Math.min(
            1024,
            Math.max(0, Integer.getInteger(GitHubSCMSource.class.getName() + ".cacheSize", isWindows() ? 0 : 20)));
    /**
     * Lock to guard access to the {@link #pullRequestSourceMap} field and prevent concurrent GitHub
     * queries during a 1.x to 2.2.0+ upgrade.
     *
     * @since 2.2.0
     */
    private static final Object pullRequestSourceMapLock = new Object();

    /** Number of times we will retry asking GitHub for the mergeable status of a PR. */
    private static /* mostly final */ int mergeableStatusRetries = SystemProperties.getInteger(
            GitHubSCMSource.class.getName() + ".mergeableStatusRetries", Integer.valueOf(4));

    //////////////////////////////////////////////////////////////////////
    // Configuration fields
    //////////////////////////////////////////////////////////////////////

    /** The GitHub end-point. Defaults to {@link #GITHUB_URL}. */
    @NonNull
    private String apiUri;

    /**
     * Credentials for GitHub API; currently only supports username/password (personal access token).
     *
     * @since 2.2.0
     */
    @CheckForNull
    private String credentialsId;

    /** The repository owner. */
    @NonNull
    private final String repoOwner;

    /** The repository */
    @NonNull
    private final String repository;

    /** HTTPS URL for the repository, if specified by the user. */
    @CheckForNull
    private final String repositoryUrl;

    /**
     * The behaviours to apply to this source.
     *
     * @since 2.2.0
     */
    @NonNull
    private List<SCMSourceTrait> traits;

    //////////////////////////////////////////////////////////////////////
    // Legacy Configuration fields
    //////////////////////////////////////////////////////////////////////

    /** Legacy field. */
    @Deprecated
    private transient String scanCredentialsId;
    /** Legacy field. */
    @Deprecated
    private transient String checkoutCredentialsId;
    /** Legacy field. */
    @Deprecated
    private String includes;
    /** Legacy field. */
    @Deprecated
    private String excludes;
    /** Legacy field. */
    @Deprecated
    private transient Boolean buildOriginBranch;
    /** Legacy field. */
    @Deprecated
    private transient Boolean buildOriginBranchWithPR;
    /** Legacy field. */
    @Deprecated
    private transient Boolean buildOriginPRMerge;
    /** Legacy field. */
    @Deprecated
    private transient Boolean buildOriginPRHead;
    /** Legacy field. */
    @Deprecated
    private transient Boolean buildForkPRMerge;
    /** Legacy field. */
    @Deprecated
    private transient Boolean buildForkPRHead;

    //////////////////////////////////////////////////////////////////////
    // Run-time cached state
    //////////////////////////////////////////////////////////////////////

    /**
     * Cache of the official repository HTML URL as reported by {@link GitHub#getRepository(String)}.
     */
    @CheckForNull
    private transient URL resolvedRepositoryUrl;
    /** The collaborator names used to determine if pull requests are from trusted authors */
    @CheckForNull
    private transient Set<String> collaboratorNames;
    /** Cache of details of the repository. */
    @CheckForNull
    private transient GHRepository ghRepository;

    /** The cache of {@link ObjectMetadataAction} instances for each open PR. */
    @NonNull
    private transient /*effectively final*/ Map<Integer, ObjectMetadataAction> pullRequestMetadataCache;
    /** The cache of {@link ObjectMetadataAction} instances for each open PR. */
    @NonNull
    private transient /*effectively final*/ Map<Integer, ContributorMetadataAction> pullRequestContributorCache;

    /**
     * Used during upgrade from 1.x to 2.2.0+ only.
     *
     * @see #retrievePullRequestSource(int)
     * @see PullRequestSCMHead.FixMetadata
     * @see PullRequestSCMHead.FixMetadataMigration
     * @since 2.2.0
     */
    @CheckForNull // normally null except during a migration from 1.x
    private transient /*effectively final*/ Map<Integer, PullRequestSource> pullRequestSourceMap;

    /**
     * Constructor, defaults to {@link #GITHUB_URL} as the end-point, and anonymous access, does not
     * default any {@link SCMSourceTrait} behaviours.
     *
     * @param repoOwner the repository owner.
     * @param repository the repository name.
     * @param repositoryUrl HTML URL for the repository. If specified, takes precedence over repoOwner
     *     and repository.
     * @param configuredByUrl Whether to use repositoryUrl or repoOwner/repository for configuration.
     * @throws IllegalArgumentException if repositoryUrl is specified but invalid.
     * @since 2.2.0
     */
    // configuredByUrl is used to decide which radioBlock in the UI the user had selected when they
    // submitted the form.
    @DataBoundConstructor
    public GitHubSCMSource(String repoOwner, String repository, String repositoryUrl, boolean configuredByUrl) {
        if (!configuredByUrl) {
            this.apiUri = GITHUB_URL;
            this.repoOwner = repoOwner;
            this.repository = repository;
            this.repositoryUrl = null;
        } else {
            GitHubRepositoryInfo info = GitHubRepositoryInfo.forRepositoryUrl(repositoryUrl);
            this.apiUri = info.getApiUri();
            this.repoOwner = info.getRepoOwner();
            this.repository = info.getRepository();
            this.repositoryUrl = info.getRepositoryUrl();
        }
        pullRequestMetadataCache = new ConcurrentHashMap<>();
        pullRequestContributorCache = new ConcurrentHashMap<>();
        this.traits = new ArrayList<>();
    }

    /**
     * Legacy constructor.
     *
     * @param repoOwner the repository owner.
     * @param repository the repository name.
     * @since 2.2.0
     */
    @Deprecated
    public GitHubSCMSource(String repoOwner, String repository) {
        this(repoOwner, repository, null, false);
    }

    /**
     * Legacy constructor.
     *
     * @param id the source id.
     * @param apiUri the GitHub endpoint.
     * @param checkoutCredentialsId the checkout credentials id or {@link DescriptorImpl#SAME} or
     *     {@link DescriptorImpl#ANONYMOUS}.
     * @param scanCredentialsId the scan credentials id or {@code null}.
     * @param repoOwner the repository owner.
     * @param repository the repository name.
     */
    @Deprecated
    public GitHubSCMSource(
            @CheckForNull String id,
            @CheckForNull String apiUri,
            @NonNull String checkoutCredentialsId,
            @CheckForNull String scanCredentialsId,
            @NonNull String repoOwner,
            @NonNull String repository) {
        this(repoOwner, repository, null, false);
        setId(id);
        setApiUri(apiUri);
        setCredentialsId(scanCredentialsId);
        // legacy constructor means legacy defaults
        this.traits = new ArrayList<>();
        this.traits.add(new BranchDiscoveryTrait(true, true));
        this.traits.add(new ForkPullRequestDiscoveryTrait(
                EnumSet.of(ChangeRequestCheckoutStrategy.MERGE), new ForkPullRequestDiscoveryTrait.TrustPermission()));
        if (!DescriptorImpl.SAME.equals(checkoutCredentialsId)) {
            traits.add(new SSHCheckoutTrait(checkoutCredentialsId));
        }
    }

    @Restricted(NoExternalUse.class)
    public boolean isConfiguredByUrl() {
        return repositoryUrl != null;
    }

    /**
     * Returns the GitHub API end-point.
     *
     * @return the GitHub API end-point.
     */
    @NonNull
    public String getApiUri() {
        return apiUri;
    }

    /**
     * Sets the GitHub API end-point.
     *
     * @param apiUri the GitHub API end-point or {@code null} if {@link #GITHUB_URL}.
     * @since 2.2.0
     */
    @DataBoundSetter
    public void setApiUri(@CheckForNull String apiUri) {
        // JENKINS-58862
        // If repositoryUrl is set, we don't want to set it again.
        if (this.repositoryUrl != null) {
            return;
        }
        apiUri = GitHubConfiguration.normalizeApiUri(Util.fixEmptyAndTrim(apiUri));
        if (apiUri == null) {
            apiUri = GITHUB_URL;
        }
        this.apiUri = apiUri;
    }

    /**
     * Forces the apiUri to a specific value. FOR TESTING ONLY.
     *
     * @param apiUri the api uri
     */
    void forceApiUri(@NonNull String apiUri) {
        this.apiUri = apiUri;
    }

    /**
     * Gets the credentials used to access the GitHub REST API (also used as the default credentials
     * for checking out sources.
     *
     * @return the credentials used to access the GitHub REST API or {@code null} to access
     *     anonymously
     */
    @Override
    @CheckForNull
    public String getCredentialsId() {
        return credentialsId;
    }

    /**
     * Sets the credentials used to access the GitHub REST API (also used as the default credentials
     * for checking out sources.
     *
     * @param credentialsId the credentials used to access the GitHub REST API or {@code null} to
     *     access anonymously
     * @since 2.2.0
     */
    @DataBoundSetter
    public void setCredentialsId(@CheckForNull String credentialsId) {
        this.credentialsId = Util.fixEmpty(credentialsId);
    }

    /**
     * Gets the repository owner.
     *
     * @return the repository owner.
     */
    @Exported
    @NonNull
    public String getRepoOwner() {
        return repoOwner;
    }

    /**
     * Gets the repository name.
     *
     * @return the repository name.
     */
    @Exported
    @NonNull
    public String getRepository() {
        return repository;
    }

    /**
     * Gets the repository URL as specified by the user.
     *
     * @return the repository URL as specified by the user.
     */
    @Restricted(NoExternalUse.class)
    @NonNull // Always returns a value so that users can always use the URL-based configuration when
    // reconfiguring.
    public String getRepositoryUrl() {
        if (repositoryUrl != null) {
            return repositoryUrl;
        } else {
            if (GITHUB_URL.equals(apiUri)) return "https://github.com/" + repoOwner + '/' + repository;
            else return String.format("%s%s/%s", removeEnd(apiUri, API_V3), repoOwner, repository);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @since 2.2.0
     */
    @Override
    public List<SCMSourceTrait> getTraits() {
        return traits;
    }

    /**
     * Sets the behaviours that are applied to this {@link GitHubSCMSource}.
     *
     * @param traits the behaviours that are to be applied.
     */
    @DataBoundSetter
    public void setTraits(@CheckForNull List<SCMSourceTrait> traits) {
        this.traits = new ArrayList<>(Util.fixNull(traits));
    }

    /** Use defaults for old settings. */
    @SuppressWarnings("ConstantConditions")
    @SuppressFBWarnings(
            value = "RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE",
            justification = "Only non-null after we set them here!")
    private Object readResolve() {
        if (scanCredentialsId != null) {
            credentialsId = scanCredentialsId;
        }
        if (pullRequestMetadataCache == null) {
            pullRequestMetadataCache = new ConcurrentHashMap<>();
        }
        if (pullRequestContributorCache == null) {
            pullRequestContributorCache = new ConcurrentHashMap<>();
        }
        if (traits == null) {
            boolean buildOriginBranch = this.buildOriginBranch == null || this.buildOriginBranch;
            boolean buildOriginBranchWithPR = this.buildOriginBranchWithPR == null || this.buildOriginBranchWithPR;
            boolean buildOriginPRMerge = this.buildOriginPRMerge != null && this.buildOriginPRMerge;
            boolean buildOriginPRHead = this.buildOriginPRHead != null && this.buildOriginPRHead;
            boolean buildForkPRMerge = this.buildForkPRMerge == null || this.buildForkPRMerge;
            boolean buildForkPRHead = this.buildForkPRHead != null && this.buildForkPRHead;
            List<SCMSourceTrait> traits = new ArrayList<>();
            if (buildOriginBranch || buildOriginBranchWithPR) {
                traits.add(new BranchDiscoveryTrait(buildOriginBranch, buildOriginBranchWithPR));
            }
            if (buildOriginPRMerge || buildOriginPRHead) {
                EnumSet<ChangeRequestCheckoutStrategy> s = EnumSet.noneOf(ChangeRequestCheckoutStrategy.class);
                if (buildOriginPRMerge) {
                    s.add(ChangeRequestCheckoutStrategy.MERGE);
                }
                if (buildOriginPRHead) {
                    s.add(ChangeRequestCheckoutStrategy.HEAD);
                }
                traits.add(new OriginPullRequestDiscoveryTrait(s));
            }
            if (buildForkPRMerge || buildForkPRHead) {
                EnumSet<ChangeRequestCheckoutStrategy> s = EnumSet.noneOf(ChangeRequestCheckoutStrategy.class);
                if (buildForkPRMerge) {
                    s.add(ChangeRequestCheckoutStrategy.MERGE);
                }
                if (buildForkPRHead) {
                    s.add(ChangeRequestCheckoutStrategy.HEAD);
                }
                traits.add(new ForkPullRequestDiscoveryTrait(s, new ForkPullRequestDiscoveryTrait.TrustPermission()));
            }
            if (!"*".equals(includes) || !"".equals(excludes)) {
                traits.add(new WildcardSCMHeadFilterTrait(includes, excludes));
            }
            if (checkoutCredentialsId != null
                    && !DescriptorImpl.SAME.equals(checkoutCredentialsId)
                    && !checkoutCredentialsId.equals(scanCredentialsId)) {
                traits.add(new SSHCheckoutTrait(checkoutCredentialsId));
            }
            this.traits = traits;
        }
        if (isBlank(apiUri)) {
            setApiUri(GITHUB_URL);
        } else if (!StringUtils.equals(apiUri, GitHubConfiguration.normalizeApiUri(apiUri))) {
            setApiUri(apiUri);
        }
        return this;
    }

    /**
     * Returns how long to delay events received from GitHub in order to allow the API caches to sync.
     *
     * @return how long to delay events received from GitHub in order to allow the API caches to sync.
     */
    public static int getEventDelaySeconds() {
        return eventDelaySeconds;
    }

    /**
     * Sets how long to delay events received from GitHub in order to allow the API caches to sync.
     *
     * @param eventDelaySeconds number of seconds to delay, will be restricted into a value within the
     *     range {@code [0,300]} inclusive
     */
    @Restricted(NoExternalUse.class) // to allow configuration from system groovy console
    public static void setEventDelaySeconds(int eventDelaySeconds) {
        GitHubSCMSource.eventDelaySeconds = Math.min(300, Math.max(0, eventDelaySeconds));
    }

    /**
     * Returns how many megabytes of on-disk cache to maintain per GitHub API URL per credentials.
     *
     * @return how many megabytes of on-disk cache to maintain per GitHub API URL per credentials.
     */
    public static int getCacheSize() {
        return cacheSize;
    }

    /**
     * Sets how long to delay events received from GitHub in order to allow the API caches to sync.
     *
     * @param cacheSize how many megabytes of on-disk cache to maintain per GitHub API URL per
     *     credentials, will be restricted into a value within the range {@code [0,1024]} inclusive.
     */
    @Restricted(NoExternalUse.class) // to allow configuration from system groovy console
    public static void setCacheSize(int cacheSize) {
        GitHubSCMSource.cacheSize = Math.min(1024, Math.max(0, cacheSize));
    }

    /** {@inheritDoc} */
    @Override
    public String getRemote() {
        return GitHubSCMBuilder.uriResolver(getOwner(), apiUri, credentialsId)
                .getRepositoryUri(apiUri, repoOwner, repository);
    }

    /** {@inheritDoc} */
    @Override
    public String getPronoun() {
        return Messages.GitHubSCMSource_Pronoun();
    }

    /**
     * Returns a {@link RepositoryUriResolver} according to credentials configuration.
     *
     * @return a {@link RepositoryUriResolver}
     * @deprecated use {@link GitHubSCMBuilder#uriResolver()} or {@link
     *     GitHubSCMBuilder#uriResolver(Item, String, String)}.
     */
    @Deprecated
    @Restricted(DoNotUse.class)
    @RestrictedSince("2.2.0")
    public RepositoryUriResolver getUriResolver() {
        return GitHubSCMBuilder.uriResolver(getOwner(), apiUri, credentialsId);
    }

    @Restricted(NoExternalUse.class)
    @RestrictedSince("2.2.0")
    @Deprecated
    @CheckForNull
    public String getScanCredentialsId() {
        return credentialsId;
    }

    @Restricted(DoNotUse.class)
    @RestrictedSince("2.2.0")
    @Deprecated
    public void setScanCredentialsId(@CheckForNull String credentialsId) {
        this.credentialsId = credentialsId;
    }

    @Restricted(DoNotUse.class)
    @RestrictedSince("2.2.0")
    @Deprecated
    @CheckForNull
    public String getCheckoutCredentialsId() {
        for (SCMSourceTrait trait : traits) {
            if (trait instanceof SSHCheckoutTrait) {
                return StringUtils.defaultString(
                        ((SSHCheckoutTrait) trait).getCredentialsId(), GitHubSCMSource.DescriptorImpl.ANONYMOUS);
            }
        }
        return DescriptorImpl.SAME;
    }

    @Deprecated
    @Restricted(DoNotUse.class)
    @RestrictedSince("2.2.0")
    @DataBoundSetter
    public void setIncludes(@NonNull String includes) {
        for (int i = 0; i < traits.size(); i++) {
            SCMSourceTrait trait = traits.get(i);
            if (trait instanceof WildcardSCMHeadFilterTrait) {
                WildcardSCMHeadFilterTrait existing = (WildcardSCMHeadFilterTrait) trait;
                if ("*".equals(includes) && "".equals(existing.getExcludes())) {
                    traits.remove(i);
                } else {
                    traits.set(i, new WildcardSCMHeadFilterTrait(includes, existing.getExcludes()));
                }
                return;
            }
        }
        if (!"*".equals(includes)) {
            traits.add(new WildcardSCMHeadFilterTrait(includes, ""));
        }
    }

    @Deprecated
    @Restricted(NoExternalUse.class)
    @RestrictedSince("2.2.0")
    @DataBoundSetter
    public void setExcludes(@NonNull String excludes) {
        for (int i = 0; i < traits.size(); i++) {
            SCMSourceTrait trait = traits.get(i);
            if (trait instanceof WildcardSCMHeadFilterTrait) {
                WildcardSCMHeadFilterTrait existing = (WildcardSCMHeadFilterTrait) trait;
                if ("*".equals(existing.getIncludes()) && "".equals(excludes)) {
                    traits.remove(i);
                } else {
                    traits.set(i, new WildcardSCMHeadFilterTrait(existing.getIncludes(), excludes));
                }
                return;
            }
        }
        if (!"".equals(excludes)) {
            traits.add(new WildcardSCMHeadFilterTrait("*", excludes));
        }
    }

    @Deprecated
    @Restricted(DoNotUse.class)
    @RestrictedSince("2.2.0")
    public boolean getBuildOriginBranch() {
        for (SCMTrait<?> trait : traits) {
            if (trait instanceof BranchDiscoveryTrait) {
                return ((BranchDiscoveryTrait) trait).isBuildBranch();
            }
        }
        return false;
    }

    @Deprecated
    @Restricted(DoNotUse.class)
    @RestrictedSince("2.2.0")
    @DataBoundSetter
    public void setBuildOriginBranch(boolean buildOriginBranch) {
        for (int i = 0; i < traits.size(); i++) {
            SCMTrait<?> trait = traits.get(i);
            if (trait instanceof BranchDiscoveryTrait) {
                BranchDiscoveryTrait previous = (BranchDiscoveryTrait) trait;
                if (buildOriginBranch || previous.isBuildBranchesWithPR()) {
                    traits.set(i, new BranchDiscoveryTrait(buildOriginBranch, previous.isBuildBranchesWithPR()));
                } else {
                    traits.remove(i);
                }
                return;
            }
        }
        if (buildOriginBranch) {
            traits.add(new BranchDiscoveryTrait(buildOriginBranch, false));
        }
    }

    @Deprecated
    @Restricted(DoNotUse.class)
    @RestrictedSince("2.2.0")
    public boolean getBuildOriginBranchWithPR() {
        for (SCMTrait<?> trait : traits) {
            if (trait instanceof BranchDiscoveryTrait) {
                return ((BranchDiscoveryTrait) trait).isBuildBranchesWithPR();
            }
        }
        return false;
    }

    @Deprecated
    @Restricted(DoNotUse.class)
    @RestrictedSince("2.2.0")
    @DataBoundSetter
    public void setBuildOriginBranchWithPR(boolean buildOriginBranchWithPR) {
        for (int i = 0; i < traits.size(); i++) {
            SCMTrait<?> trait = traits.get(i);
            if (trait instanceof BranchDiscoveryTrait) {
                BranchDiscoveryTrait previous = (BranchDiscoveryTrait) trait;
                if (buildOriginBranchWithPR || previous.isBuildBranch()) {
                    traits.set(i, new BranchDiscoveryTrait(previous.isBuildBranch(), buildOriginBranchWithPR));
                } else {
                    traits.remove(i);
                }
                return;
            }
        }
        if (buildOriginBranchWithPR) {
            traits.add(new BranchDiscoveryTrait(false, buildOriginBranchWithPR));
        }
    }

    @Deprecated
    @Restricted(DoNotUse.class)
    @RestrictedSince("2.2.0")
    public boolean getBuildOriginPRMerge() {
        for (SCMTrait<?> trait : traits) {
            if (trait instanceof OriginPullRequestDiscoveryTrait) {
                return ((OriginPullRequestDiscoveryTrait) trait)
                        .getStrategies()
                        .contains(ChangeRequestCheckoutStrategy.MERGE);
            }
        }
        return false;
    }

    @Deprecated
    @Restricted(DoNotUse.class)
    @RestrictedSince("2.2.0")
    @DataBoundSetter
    public void setBuildOriginPRMerge(boolean buildOriginPRMerge) {
        for (int i = 0; i < traits.size(); i++) {
            SCMTrait<?> trait = traits.get(i);
            if (trait instanceof OriginPullRequestDiscoveryTrait) {
                Set<ChangeRequestCheckoutStrategy> s = ((OriginPullRequestDiscoveryTrait) trait).getStrategies();
                if (buildOriginPRMerge) {
                    s.add(ChangeRequestCheckoutStrategy.MERGE);
                } else {
                    s.remove(ChangeRequestCheckoutStrategy.MERGE);
                }
                traits.set(i, new OriginPullRequestDiscoveryTrait(s));
                return;
            }
        }
        if (buildOriginPRMerge) {
            traits.add(new OriginPullRequestDiscoveryTrait(EnumSet.of(ChangeRequestCheckoutStrategy.MERGE)));
        }
    }

    @Deprecated
    @Restricted(DoNotUse.class)
    @RestrictedSince("2.2.0")
    public boolean getBuildOriginPRHead() {
        for (SCMTrait<?> trait : traits) {
            if (trait instanceof OriginPullRequestDiscoveryTrait) {
                return ((OriginPullRequestDiscoveryTrait) trait)
                        .getStrategies()
                        .contains(ChangeRequestCheckoutStrategy.HEAD);
            }
        }
        return false;
    }

    @Deprecated
    @Restricted(DoNotUse.class)
    @RestrictedSince("2.2.0")
    @DataBoundSetter
    public void setBuildOriginPRHead(boolean buildOriginPRHead) {
        for (int i = 0; i < traits.size(); i++) {
            SCMTrait<?> trait = traits.get(i);
            if (trait instanceof OriginPullRequestDiscoveryTrait) {
                Set<ChangeRequestCheckoutStrategy> s = ((OriginPullRequestDiscoveryTrait) trait).getStrategies();
                if (buildOriginPRHead) {
                    s.add(ChangeRequestCheckoutStrategy.HEAD);
                } else {
                    s.remove(ChangeRequestCheckoutStrategy.HEAD);
                }
                traits.set(i, new OriginPullRequestDiscoveryTrait(s));
                return;
            }
        }
        if (buildOriginPRHead) {
            traits.add(new OriginPullRequestDiscoveryTrait(EnumSet.of(ChangeRequestCheckoutStrategy.HEAD)));
        }
    }

    @Deprecated
    @Restricted(DoNotUse.class)
    @RestrictedSince("2.2.0")
    public boolean getBuildForkPRMerge() {
        for (SCMTrait<?> trait : traits) {
            if (trait instanceof ForkPullRequestDiscoveryTrait) {
                return ((ForkPullRequestDiscoveryTrait) trait)
                        .getStrategies()
                        .contains(ChangeRequestCheckoutStrategy.MERGE);
            }
        }
        return false;
    }

    @Deprecated
    @Restricted(DoNotUse.class)
    @RestrictedSince("2.2.0")
    @DataBoundSetter
    public void setBuildForkPRMerge(boolean buildForkPRMerge) {
        for (int i = 0; i < traits.size(); i++) {
            SCMTrait<?> trait = traits.get(i);
            if (trait instanceof ForkPullRequestDiscoveryTrait) {
                ForkPullRequestDiscoveryTrait forkTrait = (ForkPullRequestDiscoveryTrait) trait;
                Set<ChangeRequestCheckoutStrategy> s = forkTrait.getStrategies();
                if (buildForkPRMerge) {
                    s.add(ChangeRequestCheckoutStrategy.MERGE);
                } else {
                    s.remove(ChangeRequestCheckoutStrategy.MERGE);
                }
                traits.set(i, new ForkPullRequestDiscoveryTrait(s, forkTrait.getTrust()));
                return;
            }
        }
        if (buildForkPRMerge) {
            traits.add(new ForkPullRequestDiscoveryTrait(
                    EnumSet.of(ChangeRequestCheckoutStrategy.MERGE),
                    new ForkPullRequestDiscoveryTrait.TrustPermission()));
        }
    }

    @Deprecated
    @Restricted(DoNotUse.class)
    @RestrictedSince("2.2.0")
    public boolean getBuildForkPRHead() {
        for (SCMTrait<?> trait : traits) {
            if (trait instanceof ForkPullRequestDiscoveryTrait) {
                return ((ForkPullRequestDiscoveryTrait) trait)
                        .getStrategies()
                        .contains(ChangeRequestCheckoutStrategy.HEAD);
            }
        }
        return false;
    }

    @Deprecated
    @Restricted(DoNotUse.class)
    @RestrictedSince("2.2.0")
    @DataBoundSetter
    public void setBuildForkPRHead(boolean buildForkPRHead) {
        for (int i = 0; i < traits.size(); i++) {
            SCMTrait<?> trait = traits.get(i);
            if (trait instanceof ForkPullRequestDiscoveryTrait) {
                ForkPullRequestDiscoveryTrait forkTrait = (ForkPullRequestDiscoveryTrait) trait;
                Set<ChangeRequestCheckoutStrategy> s = forkTrait.getStrategies();
                if (buildForkPRHead) {
                    s.add(ChangeRequestCheckoutStrategy.HEAD);
                } else {
                    s.remove(ChangeRequestCheckoutStrategy.HEAD);
                }
                traits.set(i, new ForkPullRequestDiscoveryTrait(s, forkTrait.getTrust()));
                return;
            }
        }
        if (buildForkPRHead) {
            traits.add(new ForkPullRequestDiscoveryTrait(
                    EnumSet.of(ChangeRequestCheckoutStrategy.HEAD),
                    new ForkPullRequestDiscoveryTrait.TrustPermission()));
        }
    }

    /**
     * Simple method to iterate a set of {@link SCMHeadObserver#getIncludes()} branches/tags/pr that
     * will be possible observed and to check if at least one element is an instance of a provided
     * class.
     *
     * @param observer {@link SCMHeadObserver} with an include list that are possible going to be
     *     observed.
     * @param t Class type to compare the set elements to.
     * @return true if the observer includes list contains at least one element with the provided
     *     class type.
     */
    public boolean checkObserverIncludesType(@NonNull SCMHeadObserver observer, @NonNull Class t) {
        Set<SCMHead> includes = observer.getIncludes();
        if (includes != null) {
            for (SCMHead head : includes) {
                if (t.isInstance(head)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Method to verify if the conditions to retrieve information regarding a SCMHead class are met.
     *
     * @param observer {@link SCMHeadObserver} with the events to be observed.
     * @param event {@link SCMHeadEvent} with the event triggered.
     * @param t Class type of analyzed SCMHead.
     * @return true if a retrieve should be executed form a given SCMHead Class.
     */
    public boolean shouldRetrieve(
            @NonNull SCMHeadObserver observer, @CheckForNull SCMHeadEvent<?> event, @NonNull Class t) {

        // JENKINS-65071
        // Observer has information about the events to analyze. To avoid unnecessary processing
        // and GitHub API requests,
        // it is necessary to check if this event contains a set of {@link SCMHead} instances of a
        // type.
        // When we open or close a Pull request we don't need a TAG examination because the event
        // doesn't have any TAG. So, we only trigger a
        // examination if the observer has any include event of each type BranchSCMHead,
        // PullRequestSCMHead or GitHubTagSCMHead.
        // But when a project scan is triggered we don't have any event so a full examination
        // should happen.

        if (event == null) {
            return true;
        }

        return checkObserverIncludesType(observer, t);
    }

    @Override
    protected final void retrieve(
            @CheckForNull SCMSourceCriteria criteria,
            @NonNull SCMHeadObserver observer,
            @CheckForNull SCMHeadEvent<?> event,
            @NonNull final TaskListener listener)
            throws IOException, InterruptedException {
        StandardCredentials credentials =
                Connector.lookupScanCredentials((Item) getOwner(), apiUri, credentialsId, repoOwner);
        // Github client and validation
        final GitHub github = Connector.connect(apiUri, credentials);
        try {
            Connector.configureLocalRateLimitChecker(listener, github);

            try {
                // Input data validation
                Connector.checkConnectionValidity(apiUri, listener, credentials, github);

                // Input data validation
                if (isBlank(repository)) {
                    throw new AbortException("No repository selected, skipping");
                }

                String fullName = repoOwner + "/" + repository;
                ghRepository = github.getRepository(fullName);
                final GHRepository ghRepository = this.ghRepository;
                listener.getLogger()
                        .format(
                                "Examining %s%n",
                                HyperlinkNote.encodeTo(ghRepository.getHtmlUrl().toString(), fullName));
                resolvedRepositoryUrl = ghRepository.getHtmlUrl();
                try (final GitHubSCMSourceRequest request = new GitHubSCMSourceContext(criteria, observer)
                        .withTraits(traits)
                        .newRequest(this, listener)) {
                    // populate the request with its data sources
                    request.setGitHub(github);
                    request.setRepository(ghRepository);
                    if (request.isFetchPRs()) {
                        request.setPullRequests(new LazyPullRequests(request, ghRepository));
                    }
                    if (request.isFetchBranches()) {
                        request.setBranches(new LazyBranches(request, ghRepository));
                    }
                    if (request.isFetchTags()) {
                        request.setTags(new LazyTags(request, ghRepository));
                    }
                    request.setCollaboratorNames(
                            new LazyContributorNames(request, listener, github, ghRepository, credentials));
                    request.setPermissionsSource(new GitHubPermissionsSource() {
                        @Override
                        public GHPermissionType fetch(String username) throws IOException, InterruptedException {
                            return ghRepository.getPermission(username);
                        }
                    });

                    if (request.isFetchBranches()
                            && !request.isComplete()
                            && this.shouldRetrieve(observer, event, BranchSCMHead.class)) {
                        listener.getLogger().format("%n  Checking branches...%n");
                        int count = 0;
                        for (final GHBranch branch : request.getBranches()) {
                            count++;
                            String branchName = branch.getName();
                            listener.getLogger()
                                    .format(
                                            "%n    Checking branch %s%n",
                                            HyperlinkNote.encodeTo(
                                                    resolvedRepositoryUrl + "/tree/" + branchName, branchName));
                            BranchSCMHead head = new BranchSCMHead(branchName);
                            if (request.process(
                                    head,
                                    new SCMRevisionImpl(head, branch.getSHA1()),
                                    new SCMSourceRequest.ProbeLambda<BranchSCMHead, SCMRevisionImpl>() {
                                        @NonNull
                                        @Override
                                        public SCMSourceCriteria.Probe create(
                                                @NonNull BranchSCMHead head, @Nullable SCMRevisionImpl revisionInfo)
                                                throws IOException, InterruptedException {
                                            return new GitHubSCMProbe(
                                                    apiUri, credentials, ghRepository, head, revisionInfo);
                                        }
                                    },
                                    new CriteriaWitness(listener))) {
                                listener.getLogger()
                                        .format("%n  %d branches were processed (query completed)%n", count);
                                break;
                            }
                        }
                        listener.getLogger().format("%n  %d branches were processed%n", count);
                    }
                    if (request.isFetchPRs()
                            && !request.isComplete()
                            && this.shouldRetrieve(observer, event, PullRequestSCMHead.class)) {
                        listener.getLogger().format("%n  Checking pull-requests...%n");
                        int count = 0;
                        int errorCount = 0;
                        Map<Boolean, Set<ChangeRequestCheckoutStrategy>> strategies = request.getPRStrategies();

                        // JENKINS-56996
                        // PRs are one the most error prone areas for scans
                        // Branches and tags are contained only the current repo, PRs go across forks
                        // FileNotFoundException can occur in a number of situations
                        // When this happens, it is not ideal behavior but it is better to let the PR be
                        // orphaned
                        // and the orphan strategy control the result than for this error to stop scanning
                        // (For Org scanning this is particularly important.)
                        // If some more general IO exception is thrown, we will still fail.

                        validatePullRequests(request);
                        for (final GHPullRequest pr : request.getPullRequests()) {
                            int number = pr.getNumber();
                            try {
                                retrievePullRequest(
                                        apiUri, credentials, ghRepository, pr, strategies, request, listener);
                            } catch (FileNotFoundException e) {
                                listener.getLogger().format("%n  Error while processing pull request %d%n", number);
                                Functions.printStackTrace(e, listener.getLogger());
                                errorCount++;
                            }
                            count++;
                        }
                        listener.getLogger().format("%n  %d pull requests were processed%n", count);
                        if (errorCount > 0) {
                            listener.getLogger()
                                    .format("%n  %d pull requests encountered errors and were orphaned.%n", count);
                        }
                    }
                    if (request.isFetchTags()
                            && !request.isComplete()
                            && this.shouldRetrieve(observer, event, GitHubTagSCMHead.class)) {
                        listener.getLogger().format("%n  Checking tags...%n");
                        int count = 0;
                        for (final GHRef tag : request.getTags()) {
                            String tagName = tag.getRef();
                            if (!tagName.startsWith(Constants.R_TAGS)) {
                                // should never happen, but if it does we should skip
                                continue;
                            }
                            tagName = tagName.substring(Constants.R_TAGS.length());
                            count++;
                            listener.getLogger()
                                    .format(
                                            "%n    Checking tag %s%n",
                                            HyperlinkNote.encodeTo(
                                                    resolvedRepositoryUrl + "/tree/" + tagName, tagName));
                            long tagDate = 0L;
                            String sha = tag.getObject().getSha();
                            if ("tag".equalsIgnoreCase(tag.getObject().getType())) {
                                // annotated tag object
                                try {
                                    GHTagObject tagObject =
                                            request.getRepository().getTagObject(sha);
                                    tagDate = tagObject.getTagger().getDate().getTime();
                                    // we want the sha of the tagged commit not the tag object
                                    sha = tagObject.getObject().getSha();
                                } catch (IOException e) {
                                    // ignore, if the tag doesn't exist, the probe will handle that correctly
                                    // we just need enough of a date value to allow for probing
                                }
                            } else {
                                try {
                                    GHCommit commit = request.getRepository().getCommit(sha);
                                    tagDate = commit.getCommitDate().getTime();
                                } catch (IOException e) {
                                    // ignore, if the tag doesn't exist, the probe will handle that correctly
                                    // we just need enough of a date value to allow for probing
                                }
                            }
                            GitHubTagSCMHead head = new GitHubTagSCMHead(tagName, tagDate);
                            if (request.process(
                                    head,
                                    new GitTagSCMRevision(head, sha),
                                    new SCMSourceRequest.ProbeLambda<GitHubTagSCMHead, GitTagSCMRevision>() {
                                        @NonNull
                                        @Override
                                        public SCMSourceCriteria.Probe create(
                                                @NonNull GitHubTagSCMHead head,
                                                @Nullable GitTagSCMRevision revisionInfo)
                                                throws IOException, InterruptedException {
                                            return new GitHubSCMProbe(
                                                    apiUri, credentials, ghRepository, head, revisionInfo);
                                        }
                                    },
                                    new CriteriaWitness(listener))) {
                                listener.getLogger().format("%n  %d tags were processed (query completed)%n", count);
                                break;
                            }
                        }
                        listener.getLogger().format("%n  %d tags were processed%n", count);
                    }
                }
                listener.getLogger().format("%nFinished examining %s%n%n", fullName);
            } catch (WrappedException e) {
                try {
                    e.unwrap();
                } catch (RateLimitExceededException rle) {
                    throw new AbortException(rle.getMessage());
                }
            }
        } finally {
            Connector.release(github);
        }
    }

    private static void validatePullRequests(GitHubSCMSourceRequest request) {
        // JENKINS-56996
        // This method handles the case where there would be an error
        // while finding a user inside the PR iterator.
        // Once this is done future iterations over PR use a cached list.
        // We could do this at the same time as processing each PR, but
        // this is clearer and safer.
        Iterator<GHPullRequest> iterator = request.getPullRequests().iterator();
        while (iterator.hasNext()) {
            try {
                try {
                    iterator.next();
                } catch (NoSuchElementException e) {
                    break;
                } catch (WrappedException wrapped) {
                    wrapped.unwrap();
                }
            } catch (FileNotFoundException e) {
                // File not found exceptions are ignorable
            } catch (IOException | InterruptedException e) {
                throw new WrappedException(e);
            }
        }
    }

    private static void retrievePullRequest(
            final String apiUri,
            final StandardCredentials credentials,
            @NonNull final GHRepository ghRepository,
            @NonNull final GHPullRequest pr,
            @NonNull final Map<Boolean, Set<ChangeRequestCheckoutStrategy>> strategies,
            @NonNull final GitHubSCMSourceRequest request,
            @NonNull final TaskListener listener)
            throws IOException, InterruptedException {

        int number = pr.getNumber();
        listener.getLogger()
                .format(
                        "%n    Checking pull request %s%n",
                        HyperlinkNote.encodeTo(pr.getHtmlUrl().toString(), "#" + number));
        boolean fork = !ghRepository.getOwner().equals(pr.getHead().getUser());
        if (strategies.get(fork).isEmpty()) {
            if (fork) {
                listener.getLogger().format("    Submitted from fork, skipping%n%n");
            } else {
                listener.getLogger().format("    Submitted from origin repository, skipping%n%n");
            }
            return;
        }
        for (final ChangeRequestCheckoutStrategy strategy : strategies.get(fork)) {
            final String branchName;
            if (strategies.get(fork).size() == 1) {
                branchName = "PR-" + number;
            } else {
                branchName = "PR-" + number + "-" + strategy.name().toLowerCase(Locale.ENGLISH);
            }

            // PR details only needed for merge PRs
            if (strategy == ChangeRequestCheckoutStrategy.MERGE) {
                // The probe github will be closed along with the probe.
                final GitHub gitHub = Connector.connect(apiUri, credentials);
                try {
                    ensureDetailedGHPullRequest(pr, listener, gitHub, ghRepository);
                } finally {
                    Connector.release(gitHub);
                }
            }

            if (request.process(
                    new PullRequestSCMHead(pr, branchName, strategy == ChangeRequestCheckoutStrategy.MERGE),
                    null,
                    new SCMSourceRequest.ProbeLambda<PullRequestSCMHead, Void>() {
                        @NonNull
                        @Override
                        public SCMSourceCriteria.Probe create(
                                @NonNull PullRequestSCMHead head, @Nullable Void revisionInfo)
                                throws IOException, InterruptedException {
                            boolean trusted = request.isTrusted(head);
                            if (!trusted) {
                                listener.getLogger().format("    (not from a trusted source)%n");
                            }
                            return new GitHubSCMProbe(
                                    apiUri, credentials, ghRepository, trusted ? head : head.getTarget(), null);
                        }
                    },
                    new SCMSourceRequest.LazyRevisionLambda<PullRequestSCMHead, SCMRevision, Void>() {
                        @NonNull
                        @Override
                        public SCMRevision create(@NonNull PullRequestSCMHead head, @Nullable Void ignored)
                                throws IOException, InterruptedException {

                            return createPullRequestSCMRevision(pr, head, listener, ghRepository);
                        }
                    },
                    new MergabilityWitness(pr, strategy, listener),
                    new CriteriaWitness(listener))) {
                listener.getLogger().format("%n  Pull request %d processed (query completed)%n", number);
            }
        }
    }

    @NonNull
    @Override
    protected Set<String> retrieveRevisions(@NonNull TaskListener listener, Item retrieveContext)
            throws IOException, InterruptedException {
        StandardCredentials credentials =
                Connector.lookupScanCredentials(retrieveContext, apiUri, credentialsId, repoOwner);
        // Github client and validation
        final GitHub github = Connector.connect(apiUri, credentials);
        try {
            Connector.configureLocalRateLimitChecker(listener, github);
            Set<String> result = new TreeSet<>();

            try {
                // Input data validation
                Connector.checkConnectionValidity(apiUri, listener, credentials, github);

                // Input data validation
                if (isBlank(repository)) {
                    throw new AbortException("No repository selected, skipping");
                }

                String fullName = repoOwner + "/" + repository;
                ghRepository = github.getRepository(fullName);
                final GHRepository ghRepository = this.ghRepository;
                listener.getLogger()
                        .format(
                                "Listing %s%n",
                                HyperlinkNote.encodeTo(ghRepository.getHtmlUrl().toString(), fullName));
                resolvedRepositoryUrl = ghRepository.getHtmlUrl();
                GitHubSCMSourceContext context =
                        new GitHubSCMSourceContext(null, SCMHeadObserver.none()).withTraits(traits);
                boolean wantBranches = context.wantBranches();
                boolean wantTags = context.wantTags();
                boolean wantPRs = context.wantPRs();
                boolean wantSinglePRs = context.forkPRStrategies().size() == 1
                        || context.originPRStrategies().size() == 1;
                boolean wantMultiPRs = context.forkPRStrategies().size() > 1
                        || context.originPRStrategies().size() > 1;
                Set<ChangeRequestCheckoutStrategy> strategies = new TreeSet<>();
                strategies.addAll(context.forkPRStrategies());
                strategies.addAll(context.originPRStrategies());
                for (GHRef ref : ghRepository.listRefs()) {
                    String name = ref.getRef();
                    if (name.startsWith(Constants.R_HEADS) && wantBranches) {
                        String branchName = name.substring(Constants.R_HEADS.length());
                        listener.getLogger()
                                .format(
                                        "%n  Found branch %s%n",
                                        HyperlinkNote.encodeTo(
                                                resolvedRepositoryUrl + "/tree/" + branchName, branchName));
                        result.add(branchName);
                        continue;
                    }
                    if (name.startsWith(R_PULL) && wantPRs) {
                        int index = name.indexOf('/', R_PULL.length());
                        if (index != -1) {
                            String number = name.substring(R_PULL.length(), index);
                            listener.getLogger()
                                    .format(
                                            "%n  Found pull request %s%n",
                                            HyperlinkNote.encodeTo(
                                                    resolvedRepositoryUrl + "/pull/" + number, "#" + number));
                            // we are allowed to return "invalid" names so if the user has configured, say
                            // origin as single strategy and fork as multiple strategies
                            // we will return PR-5, PR-5-merge and PR-5-head in the result set
                            // and leave it up to the call to retrieve to determine exactly
                            // whether the name is actually valid and resolve the correct SCMHead type
                            //
                            // this allows this method to avoid an API call for every PR in order to
                            // determine if the PR is an origin or a fork PR and allows us to just
                            // use the single (set) of calls to get all refs
                            if (wantSinglePRs) {
                                result.add("PR-" + number);
                            }
                            if (wantMultiPRs) {
                                for (ChangeRequestCheckoutStrategy strategy : strategies) {
                                    result.add("PR-" + number + "-"
                                            + strategy.name().toLowerCase(Locale.ENGLISH));
                                }
                            }
                        }
                        continue;
                    }
                    if (name.startsWith(Constants.R_TAGS) && wantTags) {
                        String tagName = name.substring(Constants.R_TAGS.length());
                        listener.getLogger()
                                .format(
                                        "%n  Found tag %s%n",
                                        HyperlinkNote.encodeTo(resolvedRepositoryUrl + "/tree/" + tagName, tagName));
                        result.add(tagName);
                        continue;
                    }
                }
                listener.getLogger().format("%nFinished listing %s%n%n", fullName);
            } catch (WrappedException e) {
                try {
                    e.unwrap();
                } catch (RateLimitExceededException rle) {
                    throw new AbortException(rle.getMessage());
                }
            }
            return result;
        } finally {
            Connector.release(github);
        }
    }

    @Override
    protected SCMRevision retrieve(@NonNull String headName, @NonNull TaskListener listener, Item retrieveContext)
            throws IOException, InterruptedException {
        StandardCredentials credentials =
                Connector.lookupScanCredentials(retrieveContext, apiUri, credentialsId, repoOwner);
        // Github client and validation
        final GitHub github = Connector.connect(apiUri, credentials);
        try {
            Connector.configureLocalRateLimitChecker(listener, github);
            // Input data validation
            if (isBlank(repository)) {
                throw new AbortException("No repository selected, skipping");
            }

            String fullName = repoOwner + "/" + repository;
            ghRepository = github.getRepository(fullName);
            final GHRepository ghRepository = this.ghRepository;
            listener.getLogger()
                    .format(
                            "Examining %s%n",
                            HyperlinkNote.encodeTo(ghRepository.getHtmlUrl().toString(), fullName));
            GitHubSCMSourceContext context =
                    new GitHubSCMSourceContext(null, SCMHeadObserver.none()).withTraits(traits);
            Matcher prMatcher = Pattern.compile("^PR-(\\d+)(?:-(.*))?$").matcher(headName);
            if (prMatcher.matches()) {
                // it's a looking very much like a PR
                int number = Integer.parseInt(prMatcher.group(1));
                listener.getLogger().format("Attempting to resolve %s as pull request %d%n", headName, number);
                try {
                    GHPullRequest pr = ghRepository.getPullRequest(number);
                    if (pr != null) {
                        boolean fork =
                                !ghRepository.getOwner().equals(pr.getHead().getUser());
                        Set<ChangeRequestCheckoutStrategy> strategies;
                        if (context.wantPRs()) {
                            strategies = fork ? context.forkPRStrategies() : context.originPRStrategies();
                        } else {
                            // if not configured, we go with merge
                            strategies = EnumSet.of(ChangeRequestCheckoutStrategy.MERGE);
                        }
                        ChangeRequestCheckoutStrategy strategy;
                        if (prMatcher.group(2) == null) {
                            if (strategies.size() == 1) {
                                strategy = strategies.iterator().next();
                            } else {
                                // invalid name
                                listener.getLogger()
                                        .format(
                                                "Resolved %s as pull request %d but indeterminate checkout strategy, "
                                                        + "please try %s or %s%n",
                                                headName,
                                                number,
                                                headName + "-" + ChangeRequestCheckoutStrategy.HEAD.name(),
                                                headName + "-" + ChangeRequestCheckoutStrategy.MERGE.name());
                                return null;
                            }
                        } else {
                            strategy = null;
                            for (ChangeRequestCheckoutStrategy s : strategies) {
                                if (s.name().toLowerCase(Locale.ENGLISH).equals(prMatcher.group(2))) {
                                    strategy = s;
                                    break;
                                }
                            }
                            if (strategy == null) {
                                // invalid name;
                                listener.getLogger()
                                        .format(
                                                "Resolved %s as pull request %d but unknown checkout strategy %s, "
                                                        + "please try %s or %s%n",
                                                headName,
                                                number,
                                                prMatcher.group(2),
                                                headName + "-" + ChangeRequestCheckoutStrategy.HEAD.name(),
                                                headName + "-" + ChangeRequestCheckoutStrategy.MERGE.name());
                                return null;
                            }
                        }
                        PullRequestSCMHead head =
                                new PullRequestSCMHead(pr, headName, strategy == ChangeRequestCheckoutStrategy.MERGE);
                        if (head.isMerge()) {
                            ensureDetailedGHPullRequest(pr, listener, github, ghRepository);
                        }
                        PullRequestSCMRevision prRev = createPullRequestSCMRevision(pr, head, listener, ghRepository);

                        switch (strategy) {
                            case MERGE:
                                try {
                                    prRev.validateMergeHash();
                                } catch (AbortException e) {
                                    listener.getLogger()
                                            .format(
                                                    "Resolved %s as pull request %d: %s.%n%n",
                                                    headName, number, e.getMessage());
                                    return null;
                                }
                                listener.getLogger()
                                        .format(
                                                "Resolved %s as pull request %d at revision %s merged onto %s as %s%n",
                                                headName,
                                                number,
                                                prRev.getPullHash(),
                                                prRev.getBaseHash(),
                                                prRev.getMergeHash());
                                break;
                            default:
                                listener.getLogger()
                                        .format(
                                                "Resolved %s as pull request %d at revision %s%n",
                                                headName, number, prRev.getPullHash());
                                break;
                        }
                        return prRev;
                    } else {
                        listener.getLogger().format("Could not resolve %s as pull request %d%n", headName, number);
                    }
                } catch (FileNotFoundException e) {
                    // maybe some ****er created a branch or a tag called PR-_
                    listener.getLogger().format("Could not resolve %s as pull request %d%n", headName, number);
                }
            }
            try {
                listener.getLogger().format("Attempting to resolve %s as a branch%n", headName);
                GHBranch branch = ghRepository.getBranch(headName);
                if (branch != null) {
                    listener.getLogger()
                            .format(
                                    "Resolved %s as branch %s at revision %s%n",
                                    headName, branch.getName(), branch.getSHA1());
                    return new SCMRevisionImpl(new BranchSCMHead(headName), branch.getSHA1());
                }
            } catch (FileNotFoundException e) {
                // maybe it's a tag
            }
            try {
                listener.getLogger().format("Attempting to resolve %s as a tag%n", headName);
                GHRef tag = ghRepository.getRef("tags/" + headName);
                if (tag != null) {
                    long tagDate = 0L;
                    String tagSha = tag.getObject().getSha();
                    if ("tag".equalsIgnoreCase(tag.getObject().getType())) {
                        // annotated tag object
                        try {
                            GHTagObject tagObject = ghRepository.getTagObject(tagSha);
                            tagDate = tagObject.getTagger().getDate().getTime();
                        } catch (IOException e) {
                            // ignore, if the tag doesn't exist, the probe will handle that correctly
                            // we just need enough of a date value to allow for probing
                        }
                    } else {
                        try {
                            GHCommit commit = ghRepository.getCommit(tagSha);
                            tagDate = commit.getCommitDate().getTime();
                        } catch (IOException e) {
                            // ignore, if the tag doesn't exist, the probe will handle that correctly
                            // we just need enough of a date value to allow for probing
                        }
                    }
                    listener.getLogger().format("Resolved %s as tag %s at revision %s%n", headName, headName, tagSha);
                    return new GitTagSCMRevision(new GitHubTagSCMHead(headName, tagDate), tagSha);
                }
            } catch (FileNotFoundException e) {
                // ok it doesn't exist
            }
            listener.error("Could not resolve %s", headName);

            // TODO try and resolve as a revision, but right now we'd need to know what branch the
            // revision belonged to
            // once GitSCMSource has support for arbitrary refs, we could just use that... but given that
            // GitHubSCMBuilder constructs the refspec based on the branch name, without a specific
            // "arbitrary ref"
            // SCMHead subclass we cannot do anything here
            return null;
        } finally {
            Connector.release(github);
        }
    }

    @NonNull
    private Set<String> updateCollaboratorNames(
            @NonNull TaskListener listener,
            @CheckForNull StandardCredentials credentials,
            @NonNull GHRepository ghRepository)
            throws IOException {
        if (credentials == null && (apiUri == null || GITHUB_URL.equals(apiUri))) {
            // anonymous access to GitHub will never get list of collaborators and will
            // burn an API call, so no point in even trying
            listener.getLogger().println("Anonymous cannot query list of collaborators, assuming none");
            return collaboratorNames = Collections.emptySet();
        } else {
            try {
                return collaboratorNames = new HashSet<>(ghRepository.getCollaboratorNames());
            } catch (FileNotFoundException e) {
                // not permitted
                listener.getLogger().println("Not permitted to query list of collaborators, assuming none");
                return collaboratorNames = Collections.emptySet();
            } catch (HttpException e) {
                if (e.getResponseCode() == HttpServletResponse.SC_UNAUTHORIZED
                        || e.getResponseCode() == HttpServletResponse.SC_NOT_FOUND) {
                    listener.getLogger().println("Not permitted to query list of collaborators, assuming none");
                    return collaboratorNames = Collections.emptySet();
                } else {
                    throw e;
                }
            }
        }
    }

    private static class WrappedException extends RuntimeException {

        public WrappedException(Throwable cause) {
            super(cause);
        }

        public void unwrap() throws IOException, InterruptedException {
            Throwable cause = getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            if (cause instanceof InterruptedException) {
                throw (InterruptedException) cause;
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw this;
        }
    }

    @NonNull
    @Override
    protected SCMProbe createProbe(@NonNull SCMHead head, @CheckForNull final SCMRevision revision) throws IOException {
        StandardCredentials credentials =
                Connector.lookupScanCredentials((Item) getOwner(), apiUri, credentialsId, repoOwner);
        // Github client and validation
        GitHub github = Connector.connect(apiUri, credentials);
        try {
            String fullName = repoOwner + "/" + repository;
            final GHRepository repo = github.getRepository(fullName);
            return new GitHubSCMProbe(apiUri, credentials, repo, head, revision);
        } catch (IOException | RuntimeException | Error e) {
            throw e;
        } finally {
            Connector.release(github);
        }
    }

    @Override
    @CheckForNull
    protected SCMRevision retrieve(SCMHead head, TaskListener listener) throws IOException, InterruptedException {
        StandardCredentials credentials =
                Connector.lookupScanCredentials((Item) getOwner(), apiUri, credentialsId, repoOwner);

        // Github client and validation
        GitHub github = Connector.connect(apiUri, credentials);
        try {
            try {
                Connector.checkConnectionValidity(apiUri, listener, credentials, github);
                Connector.configureLocalRateLimitChecker(listener, github);
                String fullName = repoOwner + "/" + repository;
                ghRepository = github.getRepository(fullName);
                final GHRepository ghRepository = this.ghRepository;
                resolvedRepositoryUrl = ghRepository.getHtmlUrl();
                if (head instanceof PullRequestSCMHead) {
                    PullRequestSCMHead prhead = (PullRequestSCMHead) head;
                    GHPullRequest pr = ghRepository.getPullRequest(prhead.getNumber());
                    if (prhead.isMerge()) {
                        ensureDetailedGHPullRequest(pr, listener, github, ghRepository);
                    }
                    PullRequestSCMRevision prRev = createPullRequestSCMRevision(pr, prhead, listener, ghRepository);
                    prRev.validateMergeHash();
                    return prRev;
                } else if (head instanceof GitHubTagSCMHead) {
                    GitHubTagSCMHead tagHead = (GitHubTagSCMHead) head;
                    GHRef tag = ghRepository.getRef("tags/" + tagHead.getName());
                    String sha = tag.getObject().getSha();
                    if ("tag".equalsIgnoreCase(tag.getObject().getType())) {
                        // annotated tag object
                        GHTagObject tagObject = ghRepository.getTagObject(sha);
                        // we want the sha of the tagged commit not the tag object
                        sha = tagObject.getObject().getSha();
                    }
                    return new GitTagSCMRevision(tagHead, sha);
                } else {
                    return new SCMRevisionImpl(
                            head,
                            ghRepository
                                    .getRef("heads/" + head.getName())
                                    .getObject()
                                    .getSha());
                }
            } catch (RateLimitExceededException rle) {
                throw new AbortException(rle.getMessage());
            }
        } finally {
            Connector.release(github);
        }
    }

    private static PullRequestSCMRevision createPullRequestSCMRevision(
            GHPullRequest pr, PullRequestSCMHead prhead, TaskListener listener, GHRepository ghRepository)
            throws IOException, InterruptedException {
        String baseHash = pr.getBase().getSha();
        String prHeadHash = pr.getHead().getSha();
        String mergeHash = null;

        if (prhead.isMerge()) {
            if (Boolean.FALSE.equals(pr.getMergeable())) {
                mergeHash = PullRequestSCMRevision.NOT_MERGEABLE_HASH;
            } else if (Boolean.TRUE.equals(pr.getMergeable())) {
                String proposedMergeHash = pr.getMergeCommitSha();
                GHCommit commit = null;
                try {
                    commit = ghRepository.getCommit(proposedMergeHash);
                } catch (FileNotFoundException e) {
                    listener.getLogger()
                            .format(
                                    "Pull request %s : github merge_commit_sha not found (%s). Close and reopen the PR to reset its merge hash.%n",
                                    pr.getNumber(), proposedMergeHash);
                } catch (IOException e) {
                    throw new AbortException(
                            "Error while retrieving pull request " + pr.getNumber() + " merge hash : " + e.toString());
                }

                if (commit != null) {
                    List<String> parents = commit.getParentSHA1s();
                    // Merge commits always merge against the most recent base commit they can detect.
                    if (parents.size() != 2) {
                        listener.getLogger()
                                .format(
                                        "WARNING: Invalid github merge_commit_sha for pull request %s : merge commit %s with parents - %s.%n",
                                        pr.getNumber(), proposedMergeHash, StringUtils.join(parents, "+"));
                    } else if (!parents.contains(prHeadHash)) {
                        // This is maintains the existing behavior from pre-2.5.x when the merge_commit_sha is
                        // out of sync from the requested prHead
                        listener.getLogger()
                                .format(
                                        "WARNING: Invalid  github merge_commit_sha for pull request %s : Head commit %s does match merge commit %s with parents - %s.%n",
                                        pr.getNumber(), prHeadHash, proposedMergeHash, StringUtils.join(parents, "+"));
                    } else {
                        // We found a merge_commit_sha with 2 parents and one matches the prHeadHash
                        // Use the other parent hash as the base. This keeps the merge hash in sync with head
                        // and base.
                        // It is possible that head or base hash will not exist in their branch by the time we
                        // build
                        // This is be true (and cause a failure) regardless of how we determine the commits.
                        mergeHash = proposedMergeHash;
                        baseHash = prHeadHash.equals(parents.get(0)) ? parents.get(1) : parents.get(0);
                    }
                }
            }

            // Merge PR jobs always merge against the most recent base branch commit they can detect.
            // For an invalid merge_commit_sha, we need to query for most recent base commit separately
            if (mergeHash == null) {
                baseHash = ghRepository
                        .getRef("heads/" + pr.getBase().getRef())
                        .getObject()
                        .getSha();
            }
        }

        return new PullRequestSCMRevision(prhead, baseHash, prHeadHash, mergeHash);
    }

    private static void ensureDetailedGHPullRequest(
            GHPullRequest pr, TaskListener listener, GitHub github, GHRepository ghRepository)
            throws IOException, InterruptedException {
        final long sleep = 1000;
        int retryCountdown = mergeableStatusRetries;

        while (pr.getMergeable() == null && retryCountdown > 1) {
            listener.getLogger()
                    .format(
                            "Waiting for GitHub to create a merge commit for pull request %d.  Retrying %d more times...%n",
                            pr.getNumber(), --retryCountdown);
            Thread.sleep(sleep);
        }
    }

    @Override
    public SCM build(SCMHead head, SCMRevision revision) {
        return new GitHubSCMBuilder(this, head, revision).withTraits(traits).build();
    }

    @CheckForNull
    /*package*/ URL getResolvedRepositoryUrl() {
        return resolvedRepositoryUrl;
    }

    @Deprecated // TODO remove once migration from 1.x is no longer supported
    PullRequestSource retrievePullRequestSource(int number) {
        // we use a big honking great lock to prevent concurrent requests to github during job loading
        Map<Integer, PullRequestSource> pullRequestSourceMap;
        synchronized (pullRequestSourceMapLock) {
            pullRequestSourceMap = this.pullRequestSourceMap;
            if (pullRequestSourceMap == null) {
                this.pullRequestSourceMap = pullRequestSourceMap = new HashMap<>();
                if (StringUtils.isNotBlank(repository)) {
                    String fullName = repoOwner + "/" + repository;
                    LOGGER.log(Level.INFO, "Getting remote pull requests from {0}", fullName);
                    StandardCredentials credentials =
                            Connector.lookupScanCredentials((Item) getOwner(), apiUri, credentialsId, repoOwner);
                    LogTaskListener listener = new LogTaskListener(LOGGER, Level.INFO);
                    try {
                        GitHub github = Connector.connect(apiUri, credentials);
                        try {
                            Connector.configureLocalRateLimitChecker(listener, github);
                            ghRepository = github.getRepository(fullName);
                            LOGGER.log(Level.INFO, "Got remote pull requests from {0}", fullName);
                            int n = 0;
                            for (GHPullRequest pr : ghRepository
                                    .queryPullRequests()
                                    .state(GHIssueState.OPEN)
                                    .list()) {
                                GHRepository repository = pr.getHead().getRepository();
                                // JENKINS-41246 repository may be null for deleted forks
                                pullRequestSourceMap.put(
                                        pr.getNumber(),
                                        new PullRequestSource(
                                                repository == null ? null : repository.getOwnerName(),
                                                repository == null ? null : repository.getName(),
                                                pr.getHead().getRef()));
                                n++;
                            }
                        } finally {
                            Connector.release(github);
                        }
                    } catch (IOException | InterruptedException e) {
                        LOGGER.log(
                                Level.WARNING,
                                "Could not get all pull requests from " + fullName + ", there may be rebuilds",
                                e);
                    }
                }
            }
            return pullRequestSourceMap.get(number);
        }
    }

    /**
     * Retained to migrate legacy configuration.
     *
     * @deprecated use {@link MergeWithGitSCMExtension}.
     */
    @Restricted(NoExternalUse.class)
    @RestrictedSince("2.2.0")
    @Deprecated
    private static class MergeWith extends GitSCMExtension {
        private final String baseName;
        private final String baseHash;

        private MergeWith(String baseName, String baseHash) {
            this.baseName = baseName;
            this.baseHash = baseHash;
        }

        private Object readResolve() throws ObjectStreamException {
            return new MergeWithGitSCMExtension("remotes/origin/" + baseName, baseHash);
        }
    }

    @Override
    public SCMRevision getTrustedRevision(SCMRevision revision, final TaskListener listener)
            throws IOException, InterruptedException {
        if (revision instanceof PullRequestSCMRevision) {
            PullRequestSCMHead head = (PullRequestSCMHead) revision.getHead();

            try (GitHubSCMSourceRequest request = new GitHubSCMSourceContext(null, SCMHeadObserver.none())
                    .withTraits(traits)
                    .newRequest(this, listener)) {
                if (collaboratorNames != null) {
                    request.setCollaboratorNames(collaboratorNames);
                } else {
                    request.setCollaboratorNames(new DeferredContributorNames(request, listener));
                }
                request.setPermissionsSource(new DeferredPermissionsSource(listener));
                if (request.isTrusted(head)) {
                    return revision;
                }
            } catch (WrappedException wrapped) {
                try {
                    wrapped.unwrap();
                } catch (HttpException e) {
                    listener.getLogger()
                            .format("It seems %s is unreachable, assuming no trusted collaborators%n", apiUri);
                    collaboratorNames = Collections.singleton(repoOwner);
                }
            }
            PullRequestSCMRevision rev = (PullRequestSCMRevision) revision;
            listener.getLogger()
                    .format(
                            "Loading trusted files from base branch %s at %s rather than %s%n",
                            head.getTarget().getName(), rev.getBaseHash(), rev.getPullHash());
            return new SCMRevisionImpl(head.getTarget(), rev.getBaseHash());
        }
        return revision;
    }

    /** {@inheritDoc} */
    protected boolean isCategoryEnabled(@NonNull SCMHeadCategory category) {
        for (SCMSourceTrait trait : traits) {
            if (trait.isCategoryEnabled(category)) {
                return true;
            }
        }
        return false;
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    protected List<Action> retrieveActions(
            @NonNull SCMHead head, @CheckForNull SCMHeadEvent event, @NonNull TaskListener listener)
            throws IOException, InterruptedException {
        // TODO when we have support for trusted events, use the details from event if event was from
        // trusted source
        List<Action> result = new ArrayList<>();
        SCMSourceOwner owner = getOwner();
        if (owner instanceof Actionable) {
            GitHubLink repoLink = ((Actionable) owner).getAction(GitHubLink.class);
            if (repoLink != null) {
                String url;
                ObjectMetadataAction metadataAction;
                if (head instanceof PullRequestSCMHead) {
                    // pull request to this repository
                    int number = ((PullRequestSCMHead) head).getNumber();
                    url = repoLink.getUrl() + "/pull/" + number;
                    metadataAction = pullRequestMetadataCache.get(number);
                    if (metadataAction == null) {
                        // best effort
                        metadataAction = new ObjectMetadataAction(null, null, url);
                    }
                    ContributorMetadataAction contributor = pullRequestContributorCache.get(number);
                    if (contributor != null) {
                        result.add(contributor);
                    }
                } else {
                    // branch in this repository
                    url = repoLink.getUrl() + "/tree/" + head.getName();
                    metadataAction = new ObjectMetadataAction(head.getName(), null, url);
                }
                result.add(new GitHubLink(url));
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

    /** {@inheritDoc} */
    @NonNull
    @Override
    protected List<Action> retrieveActions(@CheckForNull SCMSourceEvent event, @NonNull TaskListener listener)
            throws IOException {
        // TODO when we have support for trusted events, use the details from event if event was from
        // trusted source
        List<Action> result = new ArrayList<>();
        result.add(new GitHubRepoMetadataAction());
        String repository = this.repository;

        StandardCredentials credentials =
                Connector.lookupScanCredentials((Item) getOwner(), apiUri, credentialsId, repoOwner);
        GitHub hub = Connector.connect(apiUri, credentials);
        try {
            Connector.checkConnectionValidity(apiUri, listener, credentials, hub);
            try {
                ghRepository = hub.getRepository(getRepoOwner() + '/' + repository);
                resolvedRepositoryUrl = ghRepository.getHtmlUrl();
            } catch (FileNotFoundException e) {
                throw new AbortException(String.format(
                        "Invalid scan credentials when using %s to connect to %s/%s on %s",
                        credentials == null ? "anonymous access" : CredentialsNameProvider.name(credentials),
                        repoOwner,
                        repository,
                        apiUri));
            }
            result.add(new ObjectMetadataAction(
                    null, ghRepository.getDescription(), Util.fixEmpty(ghRepository.getHomepage())));
            result.add(new GitHubLink(ghRepository.getHtmlUrl()));
            if (StringUtils.isNotBlank(ghRepository.getDefaultBranch())) {
                result.add(new GitHubDefaultBranch(getRepoOwner(), repository, ghRepository.getDefaultBranch()));
            }
            return result;
        } finally {
            Connector.release(hub);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void afterSave() {
        SCMSourceOwner owner = getOwner();
        if (owner != null) {
            GitHubWebHook.get().registerHookFor(owner);
        }
    }

    @Symbol("github")
    @Extension
    public static class DescriptorImpl extends SCMSourceDescriptor implements CustomDescribableModel {

        @Deprecated
        @Restricted(DoNotUse.class)
        @RestrictedSince("2.2.0")
        public static final String defaultIncludes = "*";

        @Deprecated
        @Restricted(DoNotUse.class)
        @RestrictedSince("2.2.0")
        public static final String defaultExcludes = "";

        public static final String ANONYMOUS = "ANONYMOUS";
        public static final String SAME = "SAME";
        // Prior to JENKINS-33161 the unconditional behavior was to build fork PRs plus origin branches,
        // and try to build a merge revision for PRs.
        @Deprecated
        @Restricted(DoNotUse.class)
        @RestrictedSince("2.2.0")
        public static final boolean defaultBuildOriginBranch = true;

        @Deprecated
        @Restricted(DoNotUse.class)
        @RestrictedSince("2.2.0")
        public static final boolean defaultBuildOriginBranchWithPR = true;

        @Deprecated
        @Restricted(DoNotUse.class)
        @RestrictedSince("2.2.0")
        public static final boolean defaultBuildOriginPRMerge = false;

        @Deprecated
        @Restricted(DoNotUse.class)
        @RestrictedSince("2.2.0")
        public static final boolean defaultBuildOriginPRHead = false;

        @Deprecated
        @Restricted(DoNotUse.class)
        @RestrictedSince("2.2.0")
        public static final boolean defaultBuildForkPRMerge = true;

        @Deprecated
        @Restricted(DoNotUse.class)
        @RestrictedSince("2.2.0")
        public static final boolean defaultBuildForkPRHead = false;

        @Initializer(before = InitMilestone.PLUGINS_STARTED)
        public static void addAliases() {
            XSTREAM2.addCompatibilityAlias(
                    "org.jenkinsci.plugins.github_branch_source.OriginGitHubSCMSource", GitHubSCMSource.class);
        }

        @Override
        public String getDisplayName() {
            return Messages.GitHubSCMSource_DisplayName();
        }

        @NonNull
        public Map<String, Object> customInstantiate(@NonNull Map<String, Object> arguments) {
            Map<String, Object> arguments2 = new TreeMap<>(arguments);
            arguments2.remove("repositoryUrl");
            arguments2.remove("configuredByUrl");
            return arguments2;
        }

        @NonNull
        public UninstantiatedDescribable customUninstantiate(@NonNull UninstantiatedDescribable ud) {
            Map<String, Object> scmArguments = new TreeMap<>(ud.getArguments());
            scmArguments.remove("repositoryUrl");
            scmArguments.remove("configuredByUrl");
            return ud.withArguments(scmArguments);
        }

        public ListBoxModel doFillCredentialsIdItems(
                @CheckForNull @AncestorInPath Item context,
                @QueryParameter String apiUri,
                @QueryParameter String credentialsId) {
            if (context == null
                    ? !Jenkins.get().hasPermission(Jenkins.MANAGE)
                    : !context.hasPermission(Item.EXTENDED_READ)) {
                return new StandardListBoxModel().includeCurrentValue(credentialsId);
            }
            return Connector.listScanCredentials(context, apiUri);
        }

        @RequirePOST
        @Restricted(NoExternalUse.class)
        public FormValidation doCheckCredentialsId(
                @CheckForNull @AncestorInPath Item context,
                @QueryParameter String apiUri,
                @QueryParameter String repoOwner,
                @QueryParameter String value,
                @QueryParameter boolean configuredByUrl) {

            if (!configuredByUrl) {
                return Connector.checkScanCredentials(context, apiUri, value, repoOwner);
            } else if (value.isEmpty()) {
                return FormValidation.warning("Credentials are recommended");
            } else {
                // Using the URL-based configuration, that has its own "Validate" button
                return FormValidation.ok();
            }
        }

        @RequirePOST
        @Restricted(NoExternalUse.class)
        public FormValidation doValidateRepositoryUrlAndCredentials(
                @CheckForNull @AncestorInPath Item context,
                @QueryParameter String repositoryUrl,
                @QueryParameter String credentialsId,
                @QueryParameter String repoOwner) {
            if (context == null && !Jenkins.get().hasPermission(Jenkins.MANAGE)
                    || context != null && !context.hasPermission(Item.EXTENDED_READ)) {
                return FormValidation.error(
                        "Unable to validate repository information"); // not supposed to be seeing this form
            }
            if (context != null && !context.hasPermission(CredentialsProvider.USE_ITEM)) {
                return FormValidation.error(
                        "Unable to validate repository information"); // not permitted to try connecting with
                // these credentials
            }
            GitHubRepositoryInfo info;

            try {
                info = GitHubRepositoryInfo.forRepositoryUrl(repositoryUrl);
            } catch (IllegalArgumentException e) {
                return FormValidation.error(e, e.getMessage());
            }

            StandardCredentials credentials =
                    Connector.lookupScanCredentials(context, info.getApiUri(), credentialsId, repoOwner);
            StringBuilder sb = new StringBuilder();
            try {
                GitHub github = Connector.connect(info.getApiUri(), credentials);
                try {
                    if (github.isCredentialValid()) {
                        sb.append("Credentials ok.");
                    }

                    GHRepository repo = github.getRepository(info.getRepoOwner() + "/" + info.getRepository());
                    if (repo != null) {
                        sb.append(" Connected to ");
                        sb.append(repo.getHtmlUrl());
                        sb.append(".");
                    }
                } finally {
                    Connector.release(github);
                }
            } catch (IOException e) {
                return FormValidation.error(e, "Error validating repository information. " + sb.toString());
            }
            return FormValidation.ok(sb.toString());
        }

        @Restricted(NoExternalUse.class)
        public FormValidation doCheckIncludes(@QueryParameter String value) {
            if (value.isEmpty()) {
                return FormValidation.warning(Messages.GitHubSCMSource_did_you_mean_to_use_to_match_all_branches());
            }
            return FormValidation.ok();
        }

        @RequirePOST
        @Restricted(NoExternalUse.class)
        public FormValidation doCheckScanCredentialsId(
                @CheckForNull @AncestorInPath Item context,
                @QueryParameter String apiUri,
                @QueryParameter String scanCredentialsId,
                @QueryParameter String repoOwner,
                @QueryParameter boolean configuredByUrl) {
            return doCheckCredentialsId(context, apiUri, scanCredentialsId, repoOwner, configuredByUrl);
        }

        @Restricted(NoExternalUse.class)
        public FormValidation doCheckBuildOriginBranchWithPR(
                @QueryParameter boolean buildOriginBranch,
                @QueryParameter boolean buildOriginBranchWithPR,
                @QueryParameter boolean buildOriginPRMerge,
                @QueryParameter boolean buildOriginPRHead,
                @QueryParameter boolean buildForkPRMerge,
                @QueryParameter boolean buildForkPRHead) {
            if (buildOriginBranch
                    && !buildOriginBranchWithPR
                    && !buildOriginPRMerge
                    && !buildOriginPRHead
                    && !buildForkPRMerge
                    && !buildForkPRHead) {
                // TODO in principle we could make doRetrieve populate originBranchesWithPR without actually
                // including any PRs, but it would be more work and probably never wanted anyway.
                return FormValidation.warning("If you are not building any PRs, all origin branches will be built.");
            }
            return FormValidation.ok();
        }

        @Restricted(NoExternalUse.class)
        public FormValidation doCheckBuildOriginPRHead(
                @QueryParameter boolean buildOriginBranchWithPR,
                @QueryParameter boolean buildOriginPRMerge,
                @QueryParameter boolean buildOriginPRHead) {
            if (buildOriginBranchWithPR && buildOriginPRHead) {
                return FormValidation.warning(
                        "Redundant to build an origin PR both as a branch and as an unmerged PR.");
            }
            if (buildOriginPRMerge && buildOriginPRHead) {
                return FormValidation.ok(
                        "Merged vs. unmerged PRs will be distinguished in the job name (*-merge vs. *-head).");
            }
            return FormValidation.ok();
        }

        @Restricted(NoExternalUse.class)
        public FormValidation
                doCheckBuildForkPRHead /* web method name controls UI position of message; we want this at the bottom */(
                        @QueryParameter boolean buildOriginBranch,
                        @QueryParameter boolean buildOriginBranchWithPR,
                        @QueryParameter boolean buildOriginPRMerge,
                        @QueryParameter boolean buildOriginPRHead,
                        @QueryParameter boolean buildForkPRMerge,
                        @QueryParameter boolean buildForkPRHead) {
            if (!buildOriginBranch
                    && !buildOriginBranchWithPR
                    && !buildOriginPRMerge
                    && !buildOriginPRHead
                    && !buildForkPRMerge
                    && !buildForkPRHead) {
                return FormValidation.warning("You need to build something!");
            }
            if (buildForkPRMerge && buildForkPRHead) {
                return FormValidation.ok(
                        "Merged vs. unmerged PRs will be distinguished in the job name (*-merge vs. *-head).");
            }
            return FormValidation.ok();
        }

        public ListBoxModel doFillApiUriItems() {
            ListBoxModel result = new ListBoxModel();
            result.add("GitHub", "");
            for (Endpoint e : GitHubConfiguration.get().getEndpoints()) {
                result.add(
                        e.getName() == null ? e.getApiUri() : e.getName() + " (" + e.getApiUri() + ")", e.getApiUri());
            }
            return result;
        }

        public boolean isApiUriSelectable() {
            return !GitHubConfiguration.get().getEndpoints().isEmpty();
        }

        @RequirePOST
        public ListBoxModel doFillOrganizationItems(
                @CheckForNull @AncestorInPath Item context,
                @QueryParameter String apiUri,
                @QueryParameter String credentialsId,
                @QueryParameter String repoOwner)
                throws IOException {
            if (credentialsId == null) {
                return new ListBoxModel();
            }
            if (context == null && !Jenkins.get().hasPermission(Jenkins.MANAGE)
                    || context != null && !context.hasPermission(Item.EXTENDED_READ)) {
                return new ListBoxModel(); // not supposed to be seeing this form
            }
            if (context != null && !context.hasPermission(CredentialsProvider.USE_ITEM)) {
                return new ListBoxModel(); // not permitted to try connecting with these credentials
            }
            try {
                StandardCredentials credentials =
                        Connector.lookupScanCredentials(context, apiUri, credentialsId, repoOwner);
                GitHub github = Connector.connect(apiUri, credentials);
                try {
                    if (!github.isAnonymous()) {
                        ListBoxModel model = new ListBoxModel();
                        for (Map.Entry<String, GHOrganization> entry :
                                github.getMyOrganizations().entrySet()) {
                            model.add(entry.getKey(), entry.getValue().getAvatarUrl());
                        }
                        return model;
                    }
                } finally {
                    Connector.release(github);
                }
            } catch (FillErrorResponse e) {
                throw e;
            } catch (Throwable e) {
                LOGGER.log(Level.SEVERE, e.getMessage(), e);
                throw new FillErrorResponse(e.getMessage(), false);
            }
            throw new FillErrorResponse(Messages.GitHubSCMSource_CouldNotConnectionGithub(credentialsId), true);
        }

        @RequirePOST
        public ListBoxModel doFillRepositoryItems(
                @CheckForNull @AncestorInPath Item context,
                @QueryParameter String apiUri,
                @QueryParameter String credentialsId,
                @QueryParameter String repoOwner,
                @QueryParameter boolean configuredByUrl)
                throws IOException {
            if (configuredByUrl) {
                return new ListBoxModel(); // Using the URL-based configuration, don't scan for
                // repositories.
            }
            repoOwner = Util.fixEmptyAndTrim(repoOwner);
            if (repoOwner == null) {
                return new ListBoxModel();
            }
            if (context == null && !Jenkins.get().hasPermission(Jenkins.MANAGE)
                    || context != null && !context.hasPermission(Item.EXTENDED_READ)) {
                return new ListBoxModel(); // not supposed to be seeing this form
            }
            if (context != null && !context.hasPermission(CredentialsProvider.USE_ITEM)) {
                return new ListBoxModel(); // not permitted to try connecting with these credentials
            }
            try {
                StandardCredentials credentials =
                        Connector.lookupScanCredentials(context, apiUri, credentialsId, repoOwner);
                GitHub github = Connector.connect(apiUri, credentials);
                try {

                    if (!github.isAnonymous()) {
                        GHMyself myself;
                        try {
                            myself = github.getMyself();
                        } catch (IllegalStateException e) {
                            LOGGER.log(Level.WARNING, e.getMessage(), e);
                            throw new FillErrorResponse(e.getMessage(), false);
                        } catch (IOException e) {
                            LogRecord lr = new LogRecord(
                                    Level.WARNING,
                                    "Exception retrieving the repositories of the owner {0} on {1} with credentials {2}");
                            lr.setThrown(e);
                            lr.setParameters(new Object[] {
                                repoOwner,
                                apiUri,
                                credentials == null ? "anonymous access" : CredentialsNameProvider.name(credentials)
                            });
                            LOGGER.log(lr);
                            throw new FillErrorResponse(e.getMessage(), false);
                        }
                        if (myself != null && repoOwner.equalsIgnoreCase(myself.getLogin())) {
                            Set<String> result = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
                            for (GHRepository repo : myself.listRepositories(100, GHMyself.RepositoryListFilter.ALL)) {
                                result.add(repo.getName());
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
                        LogRecord lr = new LogRecord(
                                Level.WARNING,
                                "Exception retrieving the repositories of the organization {0} on {1} with credentials {2}");
                        lr.setThrown(e);
                        lr.setParameters(new Object[] {
                            repoOwner,
                            apiUri,
                            credentials == null ? "anonymous access" : CredentialsNameProvider.name(credentials)
                        });
                        LOGGER.log(lr);
                        throw new FillErrorResponse(e.getMessage(), false);
                    }
                    if (org != null && repoOwner.equalsIgnoreCase(org.getLogin())) {
                        Set<String> result = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
                        LOGGER.log(Level.FINE, "as {0} looking for repositories in {1}", new Object[] {
                            credentialsId, repoOwner
                        });
                        for (GHRepository repo : org.listRepositories(100)) {
                            LOGGER.log(Level.FINE, "as {0} found {1}/{2}", new Object[] {
                                credentialsId, repoOwner, repo.getName()
                            });
                            result.add(repo.getName());
                        }
                        LOGGER.log(Level.FINE, "as {0} result of {1} is {2}", new Object[] {
                            credentialsId, repoOwner, result
                        });
                        return nameAndValueModel(result);
                    }

                    GHUser user = null;
                    try {
                        user = github.getUser(repoOwner);
                    } catch (FileNotFoundException fnf) {
                        LOGGER.log(Level.FINE, "There is not any GH User named {0}", repoOwner);
                    } catch (IOException e) {
                        LogRecord lr = new LogRecord(
                                Level.WARNING,
                                "Exception retrieving the repositories of the user {0} on {1} with credentials {2}");
                        lr.setThrown(e);
                        lr.setParameters(new Object[] {
                            repoOwner,
                            apiUri,
                            credentials == null ? "anonymous access" : CredentialsNameProvider.name(credentials)
                        });
                        LOGGER.log(lr);
                        throw new FillErrorResponse(e.getMessage(), false);
                    }
                    if (user != null && repoOwner.equalsIgnoreCase(user.getLogin())) {
                        Set<String> result = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
                        for (GHRepository repo : user.listRepositories(100)) {
                            result.add(repo.getName());
                        }
                        return nameAndValueModel(result);
                    }
                } finally {
                    Connector.release(github);
                }
            } catch (FillErrorResponse e) {
                throw e;
            } catch (Throwable e) {
                LOGGER.log(Level.SEVERE, e.getMessage(), e);
                throw new FillErrorResponse(e.getMessage(), false);
            }
            throw new FillErrorResponse(Messages.GitHubSCMSource_NoMatchingOwner(repoOwner), true);
        }
        /**
         * Creates a list box model from a list of values. ({@link
         * ListBoxModel#ListBoxModel(Collection)} takes {@link hudson.util.ListBoxModel.Option}s, not
         * {@link String}s, and those are not {@link Comparable}.)
         */
        private static ListBoxModel nameAndValueModel(Collection<String> items) {
            ListBoxModel model = new ListBoxModel();
            for (String item : items) {
                model.add(item);
            }
            return model;
        }

        public List<NamedArrayList<? extends SCMTraitDescriptor<?>>> getTraitsDescriptorLists() {
            List<SCMTraitDescriptor<?>> all = new ArrayList<>();
            all.addAll(SCMSourceTrait._for(this, GitHubSCMSourceContext.class, null));
            all.addAll(SCMSourceTrait._for(this, null, GitHubSCMBuilder.class));
            Set<SCMTraitDescriptor<?>> dedup = new HashSet<>();
            for (Iterator<SCMTraitDescriptor<?>> iterator = all.iterator(); iterator.hasNext(); ) {
                SCMTraitDescriptor<?> d = iterator.next();
                if (dedup.contains(d) || d instanceof GitBrowserSCMSourceTrait.DescriptorImpl) {
                    // remove any we have seen already and ban the browser configuration as it will always be
                    // github
                    iterator.remove();
                } else {
                    dedup.add(d);
                }
            }
            List<NamedArrayList<? extends SCMTraitDescriptor<?>>> result = new ArrayList<>();
            NamedArrayList.select(
                    all,
                    Messages.GitHubSCMNavigator_withinRepository(),
                    NamedArrayList.anyOf(
                            NamedArrayList.withAnnotation(Discovery.class),
                            NamedArrayList.withAnnotation(Selection.class)),
                    true,
                    result);
            NamedArrayList.select(all, Messages.GitHubSCMNavigator_general(), null, true, result);
            return result;
        }

        public List<SCMSourceTrait> getTraitsDefaults() {
            return Arrays.asList( // TODO finalize
                    new BranchDiscoveryTrait(true, false),
                    new OriginPullRequestDiscoveryTrait(EnumSet.of(ChangeRequestCheckoutStrategy.HEAD)),
                    new ForkPullRequestDiscoveryTrait(
                            EnumSet.of(ChangeRequestCheckoutStrategy.HEAD),
                            new ForkPullRequestDiscoveryTrait.TrustPermission()));
        }

        @NonNull
        @Override
        protected SCMHeadCategory[] createCategories() {
            return new SCMHeadCategory[] {
                new UncategorizedSCMHeadCategory(Messages._GitHubSCMSource_UncategorizedCategory()),
                new ChangeRequestSCMHeadCategory(Messages._GitHubSCMSource_ChangeRequestCategory()),
                new TagSCMHeadCategory(Messages._GitHubSCMSource_TagCategory())
            };
        }
    }

    @Restricted(NoExternalUse.class)
    class LazyPullRequests extends LazyIterable<GHPullRequest> implements Closeable {
        private final GitHubSCMSourceRequest request;
        private final GHRepository repo;
        private Set<Integer> pullRequestMetadataKeys = new HashSet<>();
        private boolean fullScanRequested = false;
        private boolean iterationCompleted = false;

        public LazyPullRequests(GitHubSCMSourceRequest request, GHRepository repo) {
            this.request = request;
            this.repo = repo;
        }

        @Override
        protected Iterable<GHPullRequest> create() {
            try {
                Set<Integer> prs = request.getRequestedPullRequestNumbers();
                if (prs != null && prs.size() == 1) {
                    Integer number = prs.iterator().next();
                    request.listener().getLogger().format("%n  Getting remote pull request #%d...%n", number);
                    GHPullRequest pullRequest = repo.getPullRequest(number);
                    if (pullRequest.getState() != GHIssueState.OPEN) {
                        return Collections.emptyList();
                    }
                    return new CacheUpdatingIterable(Collections.singletonList(pullRequest));
                }
                Set<String> branchNames = request.getRequestedOriginBranchNames();
                if (branchNames != null && branchNames.size() == 1) { // TODO flag to check PRs are all origin PRs
                    // if we were including multiple PRs and they are not all from the same origin branch
                    // then branchNames would have a size > 1 therefore if the size is 1 we must only
                    // be after PRs that come from this named branch
                    String branchName = branchNames.iterator().next();
                    request.listener()
                            .getLogger()
                            .format("%n  Getting remote pull requests from branch %s...%n", branchName);
                    return new CacheUpdatingIterable(repo.queryPullRequests()
                            .state(GHIssueState.OPEN)
                            .head(repo.getOwnerName() + ":" + branchName)
                            .list());
                }
                request.listener().getLogger().format("%n  Getting remote pull requests...%n");
                fullScanRequested = true;
                return new CacheUpdatingIterable(LazyPullRequests.this
                        .repo
                        .queryPullRequests()
                        .state(GHIssueState.OPEN)
                        .list());
            } catch (IOException e) {
                throw new GitHubSCMSource.WrappedException(e);
            }
        }

        @Override
        public void close() throws IOException {
            if (fullScanRequested && iterationCompleted) {
                // we needed a full scan and the scan was completed, so trim the cache entries
                pullRequestMetadataCache.keySet().retainAll(pullRequestMetadataKeys);
                pullRequestContributorCache.keySet().retainAll(pullRequestMetadataKeys);
                if (Jenkins.get().getInitLevel().compareTo(InitMilestone.JOB_LOADED) > 0) {
                    // synchronization should be cheap as only writers would be looking for this just to
                    // write null
                    synchronized (pullRequestSourceMapLock) {
                        pullRequestSourceMap = null; // all data has to have been migrated
                    }
                }
            }
        }

        private class CacheUpdatingIterable extends SinglePassIterable<GHPullRequest> {
            /**
             * A map of all fully populated {@link GHUser} entries we have fetched, keyed by {@link
             * GHUser#getLogin()}.
             */
            private Map<String, GHUser> users = new HashMap<>();

            CacheUpdatingIterable(Iterable<GHPullRequest> delegate) {
                super(delegate);
            }

            @Override
            public void observe(GHPullRequest pr) {
                int number = pr.getNumber();
                GHUser user = null;
                try {
                    user = pr.getUser();
                    if (users.containsKey(user.getLogin())) {
                        // looked up this user already
                        user = users.get(user.getLogin());
                    }
                    ContributorMetadataAction contributor =
                            new ContributorMetadataAction(user.getLogin(), user.getName(), user.getEmail());
                    pullRequestContributorCache.put(number, contributor);
                    // store the populated user record now that we have it
                    users.put(user.getLogin(), user);
                } catch (FileNotFoundException e) {
                    // If file not found for user, warn but keep going
                    request.listener()
                            .getLogger()
                            .format(
                                    "%n  Could not find user %s for pull request %d.%n",
                                    user == null ? "null" : user.getLogin(), number);
                    throw new WrappedException(e);
                } catch (IOException e) {
                    throw new WrappedException(e);
                }

                pullRequestMetadataCache.put(
                        number,
                        new ObjectMetadataAction(
                                pr.getTitle(), pr.getBody(), pr.getHtmlUrl().toExternalForm()));
                pullRequestMetadataKeys.add(number);
            }

            @Override
            public void completed() {
                // we have completed a full iteration of the PRs from the delegate
                iterationCompleted = true;
            }
        }
    }

    @Restricted(NoExternalUse.class)
    static class LazyBranches extends LazyIterable<GHBranch> {
        private final GitHubSCMSourceRequest request;
        private final GHRepository repo;

        public LazyBranches(GitHubSCMSourceRequest request, GHRepository repo) {
            this.request = request;
            this.repo = repo;
        }

        @Override
        protected Iterable<GHBranch> create() {
            try {
                Set<String> branchNames = request.getRequestedOriginBranchNames();
                if (branchNames != null && branchNames.size() == 1) {
                    String branchName = branchNames.iterator().next();
                    request.listener().getLogger().format("%n  Getting remote branch %s...%n", branchName);
                    try {
                        GHBranch branch = repo.getBranch(branchName);
                        return Collections.singletonList(branch);
                    } catch (FileNotFoundException e) {
                        // branch does not currently exist
                        return Collections.emptyList();
                    }
                }
                request.listener().getLogger().format("%n  Getting remote branches...%n");
                // local optimization: always try the default branch first in any search
                List<GHBranch> values = new ArrayList<>(repo.getBranches().values());
                final String defaultBranch = StringUtils.defaultIfBlank(repo.getDefaultBranch(), "master");
                Collections.sort(values, new Comparator<GHBranch>() {
                    @Override
                    public int compare(GHBranch o1, GHBranch o2) {
                        if (defaultBranch.equals(o1.getName())) {
                            return -1;
                        }
                        if (defaultBranch.equals(o2.getName())) {
                            return 1;
                        }
                        return 0;
                    }
                });
                return values;
            } catch (IOException e) {
                throw new GitHubSCMSource.WrappedException(e);
            }
        }
    }

    @Restricted(NoExternalUse.class)
    static class LazyTags extends LazyIterable<GHRef> {
        private final GitHubSCMSourceRequest request;
        private final GHRepository repo;

        public LazyTags(GitHubSCMSourceRequest request, GHRepository repo) {
            this.request = request;
            this.repo = repo;
        }

        @Override
        protected Iterable<GHRef> create() {
            try {
                final Set<String> tagNames = request.getRequestedTagNames();
                if (tagNames != null && tagNames.size() == 1) {
                    String tagName = tagNames.iterator().next();
                    request.listener().getLogger().format("%n  Getting remote tag %s...%n", tagName);
                    try {
                        // Do not blow up if the tag is not present
                        GHRef tag = repo.getRef("tags/" + tagName);
                        return Collections.singletonList(tag);
                    } catch (FileNotFoundException e) {
                        // branch does not currently exist
                        return Collections.emptyList();
                    } catch (Error e) {
                        if (e.getCause() instanceof GHFileNotFoundException) {
                            return Collections.emptyList();
                        }
                        throw e;
                    }
                }
                request.listener().getLogger().format("%n  Getting remote tags...%n");
                // GitHub will give a 404 if the repository does not have any tags
                // we could rework the code that iterates to expect the 404, but that
                // would mean leaking the strange behaviour in every trait that consults the list
                // of tags. (And GitHub API is probably correct in throwing the GHFileNotFoundException
                // from a PagedIterable, so we don't want to fix that)
                //
                // Instead we just return a wrapped iterator that does the right thing.
                final Iterable<GHRef> iterable = repo.listRefs("tags");
                return new Iterable<GHRef>() {
                    @Override
                    public Iterator<GHRef> iterator() {
                        final Iterator<GHRef> iterator;
                        try {
                            iterator = iterable.iterator();
                        } catch (Error e) {
                            if (e.getCause() instanceof GHFileNotFoundException) {
                                return Collections.emptyIterator();
                            }
                            throw e;
                        }
                        return new Iterator<GHRef>() {
                            boolean hadAtLeastOne;
                            boolean hasNone;

                            @Override
                            public boolean hasNext() {
                                try {
                                    boolean hasNext = iterator.hasNext();
                                    hadAtLeastOne = hadAtLeastOne || hasNext;
                                    return hasNext;
                                } catch (Error e) {
                                    // pre https://github.com/kohsuke/github-api/commit
                                    // /a17ce04552ddd3f6bd8210c740184e6c7ad13ae4
                                    // we at least got the cause, even if wrapped in an Error
                                    if (e.getCause() instanceof GHFileNotFoundException) {
                                        return false;
                                    }
                                    throw e;
                                } catch (GHException e) {
                                    // JENKINS-52397 I have no clue why https://github.com/kohsuke/github-api/commit
                                    // /a17ce04552ddd3f6bd8210c740184e6c7ad13ae4 does what it does, but it makes
                                    // it rather difficult to distinguish between a network outage and the file
                                    // not found.
                                    if (hadAtLeastOne) {
                                        throw e;
                                    }
                                    try {
                                        hasNone = hasNone || repo.getRefs("tags").length == 0;
                                        if (hasNone) return false;
                                        throw e;
                                    } catch (FileNotFoundException e1) {
                                        hasNone = true;
                                        return false;
                                    } catch (IOException e1) {
                                        e.addSuppressed(e1);
                                        throw e;
                                    }
                                }
                            }

                            @Override
                            public GHRef next() {
                                if (!hasNext()) {
                                    throw new NoSuchElementException();
                                }
                                return iterator.next();
                            }

                            @Override
                            public void remove() {
                                throw new UnsupportedOperationException("remove");
                            }
                        };
                    }
                };
            } catch (IOException e) {
                throw new GitHubSCMSource.WrappedException(e);
            }
        }
    }

    private static class CriteriaWitness implements SCMSourceRequest.Witness {
        private final TaskListener listener;

        public CriteriaWitness(TaskListener listener) {
            this.listener = listener;
        }

        @Override
        public void record(@NonNull SCMHead head, SCMRevision revision, boolean isMatch) {
            if (isMatch) {
                listener.getLogger().format("    Met criteria%n");
            } else {
                listener.getLogger().format("    Does not meet criteria%n");
            }
        }
    }

    private static class MergabilityWitness
            implements SCMSourceRequest.Witness<PullRequestSCMHead, PullRequestSCMRevision> {
        private final GHPullRequest pr;
        private final ChangeRequestCheckoutStrategy strategy;
        private final TaskListener listener;

        public MergabilityWitness(GHPullRequest pr, ChangeRequestCheckoutStrategy strategy, TaskListener listener) {
            this.pr = pr;
            this.strategy = strategy;
            this.listener = listener;
        }

        @Override
        public void record(@NonNull PullRequestSCMHead head, PullRequestSCMRevision revision, boolean isMatch) {
            if (isMatch) {
                Boolean mergeable;
                try {
                    mergeable = pr.getMergeable();
                } catch (IOException e) {
                    throw new GitHubSCMSource.WrappedException(e);
                }
                if (Boolean.FALSE.equals(mergeable)) {
                    switch (strategy) {
                        case MERGE:
                            listener.getLogger().format("      Not mergeable, build likely to fail%n");
                            break;
                        default:
                            listener.getLogger().format("      Not mergeable, but will be built anyway%n");
                            break;
                    }
                }
            }
        }
    }

    private class LazyContributorNames extends LazySet<String> {
        private final GitHubSCMSourceRequest request;
        private final TaskListener listener;
        private final GitHub github;
        private final GHRepository repo;
        private final StandardCredentials credentials;

        public LazyContributorNames(
                GitHubSCMSourceRequest request,
                TaskListener listener,
                GitHub github,
                GHRepository repo,
                StandardCredentials credentials) {
            this.request = request;
            this.listener = listener;
            this.github = github;
            this.repo = repo;
            this.credentials = credentials;
        }

        /** {@inheritDoc} */
        @NonNull
        @Override
        protected Set<String> create() {
            try {
                return updateCollaboratorNames(listener, credentials, repo);
            } catch (IOException e) {
                throw new WrappedException(e);
            }
        }
    }

    private class DeferredContributorNames extends LazySet<String> {
        private final GitHubSCMSourceRequest request;
        private final TaskListener listener;

        public DeferredContributorNames(GitHubSCMSourceRequest request, TaskListener listener) {
            this.request = request;
            this.listener = listener;
        }

        /** {@inheritDoc} */
        @NonNull
        @Override
        protected Set<String> create() {
            if (collaboratorNames != null) {
                return collaboratorNames;
            }
            listener.getLogger()
                    .format(
                            "Connecting to %s to obtain list of collaborators for %s/%s%n",
                            apiUri, repoOwner, repository);
            StandardCredentials credentials =
                    Connector.lookupScanCredentials((Item) getOwner(), apiUri, credentialsId, repoOwner);
            // Github client and validation
            try {
                GitHub github = Connector.connect(apiUri, credentials);
                try {
                    Connector.configureLocalRateLimitChecker(listener, github);

                    // Input data validation
                    Connector.checkConnectionValidity(apiUri, listener, credentials, github);
                    // Input data validation
                    String credentialsName =
                            credentials == null ? "anonymous access" : CredentialsNameProvider.name(credentials);
                    if (credentials != null && !isCredentialValid(github)) {
                        listener.getLogger()
                                .format(
                                        "Invalid scan credentials %s to connect to %s, "
                                                + "assuming no trusted collaborators%n",
                                        credentialsName, apiUri);
                        collaboratorNames = Collections.singleton(repoOwner);
                    } else {
                        if (!github.isAnonymous()) {
                            listener.getLogger().format("Connecting to %s using %s%n", apiUri, credentialsName);
                        } else {
                            listener.getLogger()
                                    .format("Connecting to %s with no credentials, anonymous access%n", apiUri);
                        }

                        // Input data validation
                        if (isBlank(getRepository())) {
                            collaboratorNames = Collections.singleton(repoOwner);
                        } else {
                            String fullName = repoOwner + "/" + repository;
                            ghRepository = github.getRepository(fullName);
                            resolvedRepositoryUrl = ghRepository.getHtmlUrl();
                            return new LazyContributorNames(request, listener, github, ghRepository, credentials);
                        }
                    }
                    return collaboratorNames;
                } finally {
                    Connector.release(github);
                }
            } catch (IOException | InterruptedException e) {
                throw new WrappedException(e);
            }
        }
    }

    private class DeferredPermissionsSource extends GitHubPermissionsSource implements Closeable {

        private final TaskListener listener;
        private GitHub github;
        private GHRepository repo;

        public DeferredPermissionsSource(TaskListener listener) {
            this.listener = listener;
        }

        @Override
        public GHPermissionType fetch(String username) throws IOException, InterruptedException {
            if (repo == null) {
                listener.getLogger()
                        .format(
                                "Connecting to %s to check permissions of obtain list of %s for %s/%s%n",
                                apiUri, username, repoOwner, repository);
                StandardCredentials credentials =
                        Connector.lookupScanCredentials((Item) getOwner(), apiUri, credentialsId, repoOwner);
                github = Connector.connect(apiUri, credentials);
                String fullName = repoOwner + "/" + repository;
                repo = github.getRepository(fullName);
            }
            return repo.getPermission(username);
        }

        @Override
        public void close() throws IOException {
            if (github != null) {
                Connector.release(github);
                github = null;
                repo = null;
            }
        }
    }
}
