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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
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
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.lib.Constants;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.github.config.GitHubServerConfig;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHMyself;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHPermissionType;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRef;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTag;
import org.kohsuke.github.GHTagObject;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.HttpException;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import static hudson.model.Items.XSTREAM2;
import org.kohsuke.stapler.interceptor.RequirePOST;

public class GitHubSCMSource extends AbstractGitSCMSource {

    public static final String VALID_GITHUB_REPO_NAME = "^[0-9A-Za-z._-]+$";
    public static final String VALID_GITHUB_USER_NAME = "^[A-Za-z0-9](?:[A-Za-z0-9]|-(?=[A-Za-z0-9])){0,38}$";
    public static final String VALID_GIT_SHA1 = "^[a-fA-F0-9]{40}$";
    public static final String GITHUB_URL = GitHubServerConfig.GITHUB_URL;
    private static final Logger LOGGER = Logger.getLogger(GitHubSCMSource.class.getName());
    private static final String R_PULL = Constants.R_REFS + "pull/";
    /**
     * How long to delay events received from GitHub in order to allow the API caches to sync.
     */
    private static /*mostly final*/ int eventDelaySeconds =
            Math.min(300, Math.max(0, Integer.getInteger(GitHubSCMSource.class.getName() + ".eventDelaySeconds", 5)));
    /**
     * Lock to guard access to the {@link #pullRequestSourceMap} field and prevent concurrent GitHub queries during
     * a 1.x to 2.2.0+ upgrade.
     *
     * @since 2.2.0
     */
    private static final Object pullRequestSourceMapLock = new Object();

    //////////////////////////////////////////////////////////////////////
    // Configuration fields
    //////////////////////////////////////////////////////////////////////

    /**
     * The GitHub end-point or {@code null} if {@link #GITHUB_URL} is implied.
     */
    @CheckForNull // TODO migrate to non-null with configuration of GITHUB_URL by default
    private String apiUri;

    /**
     * Credentials for GitHub API; currently only supports username/password (personal access token).
     * @since 2.2.0
     */
    @CheckForNull
    private String credentialsId;

    /**
     * The repository owner.
     */
    @NonNull
    private final String repoOwner;

    /**
     * The repository
     */
    @NonNull
    private final String repository;

    /**
     * The behaviours to apply to this source.
     * @since 2.2.0
     */
    @NonNull
    private List<SCMSourceTrait> traits;

    //////////////////////////////////////////////////////////////////////
    // Legacy Configuration fields
    //////////////////////////////////////////////////////////////////////

    /**
     * Legacy field.
     */
    @Deprecated
    private transient String scanCredentialsId;
    /**
     * Legacy field.
     */
    @Deprecated
    private transient String checkoutCredentialsId;
    /**
     * Legacy field.
     */
    @Deprecated
    private String includes;
    /**
     * Legacy field.
     */
    @Deprecated
    private String excludes;
    /**
     * Legacy field.
     */
    @Deprecated
    private transient Boolean buildOriginBranch;
    /**
     * Legacy field.
     */
    @Deprecated
    private transient Boolean buildOriginBranchWithPR;
    /**
     * Legacy field.
     */
    @Deprecated
    private transient Boolean buildOriginPRMerge;
    /**
     * Legacy field.
     */
    @Deprecated
    private transient Boolean buildOriginPRHead;
    /**
     * Legacy field.
     */
    @Deprecated
    private transient Boolean buildForkPRMerge;
    /**
     * Legacy field.
     */
    @Deprecated
    private transient Boolean buildForkPRHead;

    //////////////////////////////////////////////////////////////////////
    // Run-time cached state
    //////////////////////////////////////////////////////////////////////

    /**
     * Cache of the official repository HTML URL as reported by {@link GitHub#getRepository(String)}.
     */
    @CheckForNull
    private transient URL repositoryUrl;
    /**
     * The collaborator names used to determine if pull requests are from trusted authors
     */
    @CheckForNull
    private transient Set<String> collaboratorNames;
    /**
     * Cache of details of the repository.
     */
    @CheckForNull
    private transient GHRepository ghRepository;

    /**
     * The cache of {@link ObjectMetadataAction} instances for each open PR.
     */
    @NonNull
    private transient /*effectively final*/ Map<Integer,ObjectMetadataAction> pullRequestMetadataCache;
    /**
     * The cache of {@link ObjectMetadataAction} instances for each open PR.
     */
    @NonNull
    private transient /*effectively final*/ Map<Integer,ContributorMetadataAction> pullRequestContributorCache;

    /**
     * Used during upgrade from 1.x to 2.2.0+ only.
     *
     * @see #retrievePullRequestSource(int)
     * @see PullRequestSCMHead.FixMetadata
     * @see PullRequestSCMHead.FixMetadataMigration
     * @since 2.2.0
     */
    @CheckForNull // normally null except during a migration from 1.x
    private transient /*effectively final*/ Map<Integer,PullRequestSource> pullRequestSourceMap;

    /**
     * Constructor, defaults to {@link #GITHUB_URL} as the end-point, and anonymous access, does not default any
     * {@link SCMSourceTrait} behaviours.
     *
     * @param repoOwner the repository owner.
     * @param repository the repository name.
     * @since 2.2.0
     */
    @DataBoundConstructor
    public GitHubSCMSource(@NonNull String repoOwner, @NonNull String repository) {
        this.repoOwner = repoOwner;
        this.repository = repository;
        pullRequestMetadataCache = new ConcurrentHashMap<>();
        pullRequestContributorCache = new ConcurrentHashMap<>();
        this.traits = new ArrayList<>();
    }

    /**
     * Legacy constructor.
     * @param id the source id.
     * @param apiUri the GitHub endpoint.
     * @param checkoutCredentialsId the checkout credentials id or {@link DescriptorImpl#SAME} or
     * {@link DescriptorImpl#ANONYMOUS}.
     * @param scanCredentialsId the scan credentials id or {@code null}.
     * @param repoOwner the repository owner.
     * @param repository the repository name.
     */
    @Deprecated
    public GitHubSCMSource(@CheckForNull String id, @CheckForNull String apiUri, @NonNull String checkoutCredentialsId,
                           @CheckForNull String scanCredentialsId, @NonNull String repoOwner,
                           @NonNull String repository) {
        this(repoOwner, repository);
        setId(id);
        setApiUri(apiUri);
        setCredentialsId(scanCredentialsId);
        // legacy constructor means legacy defaults
        this.traits = new ArrayList<>();
        this.traits.add(new BranchDiscoveryTrait(true, true));
        this.traits.add(new ForkPullRequestDiscoveryTrait(EnumSet.of(ChangeRequestCheckoutStrategy.MERGE), new ForkPullRequestDiscoveryTrait.TrustContributors()));
        if (!DescriptorImpl.SAME.equals(checkoutCredentialsId)) {
            traits.add(new SSHCheckoutTrait(checkoutCredentialsId));
        }
    }

    /**
     * Returns the GitHub API end-point or {@code null} if {@link #GITHUB_URL}.
     *
     * @return the GitHub API end-point or {@code null} if {@link #GITHUB_URL}.
     */
    @CheckForNull // TODO switch to NonNull
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
        this.apiUri = GitHubConfiguration.normalizeApiUri(Util.fixEmptyAndTrim(apiUri));
    }

    /**
     * Gets the credentials used to access the GitHub REST API (also used as the default credentials for checking out
     * sources.
     * @return the credentials used to access the GitHub REST API or {@code null} to access anonymously
     */
    @Override
    @CheckForNull
    public String getCredentialsId() {
        return credentialsId;
    }

    /**
     * Sets the credentials used to access the GitHub REST API (also used as the default credentials for checking out
     * sources.
     *
     * @param credentialsId the credentials used to access the GitHub REST API or {@code null} to access anonymously
     * @since 2.2.0
     */
    @DataBoundSetter
    public void setCredentialsId(@CheckForNull String credentialsId) {
        this.credentialsId = Util.fixEmpty(credentialsId);
    }

    /**
     * Gets the repository owner.
     * @return the repository owner.
     */
    @NonNull
    public String getRepoOwner() {
        return repoOwner;
    }

    /**
     * Gets the repository name.
     * @return the repository name.
     */
    @NonNull
    public String getRepository() {
        return repository;
    }

    /**
     * {@inheritDoc}
     * @since 2.2.0
     */
    @Override
    public List<SCMSourceTrait> getTraits() {
        return traits;
    }

    /**
     * Sets the behaviours that are applied to this {@link GitHubSCMSource}.
     * @param traits the behaviours that are to be applied.
     */
    @DataBoundSetter
    public void setTraits(@CheckForNull List<SCMSourceTrait> traits) {
        this.traits = new ArrayList<>(Util.fixNull(traits));
    }

    /**
     * Use defaults for old settings.
     */
    @SuppressWarnings("ConstantConditions")
    @SuppressFBWarnings(value="RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE", justification="Only non-null after we set them here!")
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
                traits.add(new ForkPullRequestDiscoveryTrait(s, new ForkPullRequestDiscoveryTrait.TrustContributors()));
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
        if (!StringUtils.equals(apiUri, GitHubConfiguration.normalizeApiUri(apiUri))) {
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
     * @param eventDelaySeconds number of seconds to delay, will be restricted into a value within the range
     *                          {@code [0,300]} inclusive
     */
    @Restricted(NoExternalUse.class) // to allow configuration from system groovy console
    public static void setEventDelaySeconds(int eventDelaySeconds) {
        GitHubSCMSource.eventDelaySeconds = Math.min(300, Math.max(0, eventDelaySeconds));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getRemote() {
        String apiUri = StringUtils.defaultIfBlank(this.apiUri, GitHubServerConfig.GITHUB_URL);
        return GitHubSCMBuilder.uriResolver(getOwner(), apiUri, credentialsId)
                .getRepositoryUri(apiUri, repoOwner, repository);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getPronoun() {
        return Messages.GitHubSCMSource_Pronoun();
    }

    /**
     * Returns a {@link RepositoryUriResolver} according to credentials configuration.
     *
     * @return a {@link RepositoryUriResolver}
     * @deprecated use {@link GitHubSCMBuilder#uriResolver()} or {@link GitHubSCMBuilder#uriResolver(Item, String, String)}.
     */
    @Deprecated
    @Restricted(DoNotUse.class)
    @RestrictedSince("2.2.0")
    public RepositoryUriResolver getUriResolver() {
        return GitHubSCMBuilder.uriResolver(
                getOwner(),
                StringUtils.defaultIfBlank(apiUri, GitHubServerConfig.GITHUB_URL),
                credentialsId
        );
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
                        ((SSHCheckoutTrait) trait).getCredentialsId(),
                        GitHubSCMSource.DescriptorImpl.ANONYMOUS
                );
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
                return ((OriginPullRequestDiscoveryTrait) trait).getStrategies()
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
                return ((OriginPullRequestDiscoveryTrait) trait).getStrategies()
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
                return ((ForkPullRequestDiscoveryTrait) trait).getStrategies()
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
            traits.add(new ForkPullRequestDiscoveryTrait(EnumSet.of(ChangeRequestCheckoutStrategy.MERGE),
                    new ForkPullRequestDiscoveryTrait.TrustContributors()));
        }
    }

    @Deprecated
    @Restricted(DoNotUse.class)
    @RestrictedSince("2.2.0")
    public boolean getBuildForkPRHead() {
        for (SCMTrait<?> trait : traits) {
            if (trait instanceof ForkPullRequestDiscoveryTrait) {
                return ((ForkPullRequestDiscoveryTrait) trait).getStrategies()
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
            traits.add(new ForkPullRequestDiscoveryTrait(EnumSet.of(ChangeRequestCheckoutStrategy.HEAD),
                    new ForkPullRequestDiscoveryTrait.TrustContributors()));
        }
    }

    @Override
    protected final void retrieve(@CheckForNull SCMSourceCriteria criteria,
                                  @NonNull SCMHeadObserver observer,
                                  @CheckForNull SCMHeadEvent<?> event,
                                  @NonNull final TaskListener listener) throws IOException, InterruptedException {
        StandardCredentials credentials = Connector.lookupScanCredentials((Item)getOwner(), apiUri, credentialsId);
        // Github client and validation
        final GitHub github = Connector.connect(apiUri, credentials);
        try {
            checkApiUrlValidity(github);
            Connector.checkApiRateLimit(listener, github);

            try {
                // Input data validation
                Connector.checkConnectionValidity(apiUri, listener, credentials, github);

                // Input data validation
                if (StringUtils.isBlank(repository)) {
                    throw new AbortException("No repository selected, skipping");
                }

                String fullName = repoOwner + "/" + repository;
                ghRepository = github.getRepository(fullName);
                final GHRepository ghRepository = this.ghRepository;
                listener.getLogger().format("Examining %s%n",
                        HyperlinkNote.encodeTo(ghRepository.getHtmlUrl().toString(), fullName));
                repositoryUrl = ghRepository.getHtmlUrl();
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
                    request.setCollaboratorNames(new LazyContributorNames(request, listener, github, ghRepository, credentials));
                    request.setPermissionsSource(new GitHubPermissionsSource() {
                        @Override
                        public GHPermissionType fetch(String username) throws IOException, InterruptedException {
                            return ghRepository.getPermission(username);
                        }
                    });

                    if (request.isFetchBranches() && !request.isComplete()) {
                        listener.getLogger().format("%n  Checking branches...%n");
                        int count = 0;
                        for (final GHBranch branch : request.getBranches()) {
                            count++;
                            String branchName = branch.getName();
                            listener.getLogger().format("%n    Checking branch %s%n", HyperlinkNote
                                    .encodeTo(repositoryUrl + "/tree/" + branchName, branchName));
                            BranchSCMHead head = new BranchSCMHead(branchName);
                            if (request.process(head, new SCMRevisionImpl(head, branch.getSHA1()),
                                    new SCMSourceRequest.ProbeLambda<BranchSCMHead, SCMRevisionImpl>() {
                                        @NonNull
                                        @Override
                                        public SCMSourceCriteria.Probe create(@NonNull BranchSCMHead head,
                                                                              @Nullable SCMRevisionImpl revisionInfo)
                                                throws IOException, InterruptedException {
                                            return new GitHubSCMProbe(github, ghRepository, head, revisionInfo);
                                        }
                                    }, new CriteriaWitness(listener))) {
                                listener.getLogger().format("%n  %d branches were processed (query completed)%n", count);
                                break;
                            } else {
                                request.checkApiRateLimit();
                            }
                        }
                        listener.getLogger().format("%n  %d branches were processed%n", count);
                    }
                    if (request.isFetchPRs() && !request.isComplete()) {
                        listener.getLogger().format("%n  Checking pull-requests...%n");
                        int count = 0;
                        Map<Boolean, Set<ChangeRequestCheckoutStrategy>> strategies = request.getPRStrategies();
                        PRs: for (final GHPullRequest pr : request.getPullRequests()) {
                            int number = pr.getNumber();
                            boolean fork = !ghRepository.getOwner().equals(pr.getHead().getUser());
                            listener.getLogger().format("%n    Checking pull request %s%n",
                                    HyperlinkNote.encodeTo(pr.getHtmlUrl().toString(), "#" + number));
                            if (strategies.get(fork).isEmpty()) {
                                if (fork) {
                                    listener.getLogger().format("    Submitted from fork, skipping%n%n");
                                } else {
                                    listener.getLogger().format("    Submitted from origin repository, skipping%n%n");
                                }
                                continue;
                            }
                            for (final ChangeRequestCheckoutStrategy strategy : strategies.get(fork)) {
                                final String branchName;
                                if (strategies.get(fork).size() == 1) {
                                    branchName = "PR-" + number;
                                } else {
                                    branchName = "PR-" + number + "-" + strategy.name().toLowerCase(Locale.ENGLISH);
                                }
                                count++;
                                if (request.process(new PullRequestSCMHead(
                                                pr, branchName, strategy == ChangeRequestCheckoutStrategy.MERGE
                                        ),
                                        null,
                                        new SCMSourceRequest.ProbeLambda<PullRequestSCMHead, Void>() {
                                            @NonNull
                                            @Override
                                            public SCMSourceCriteria.Probe create(@NonNull PullRequestSCMHead head,
                                                                                  @Nullable Void revisionInfo)
                                                    throws IOException, InterruptedException {
                                                boolean trusted = request.isTrusted(head);
                                                if (!trusted) {
                                                    listener.getLogger().format("    (not from a trusted source)%n");
                                                }
                                                return new GitHubSCMProbe(github, ghRepository,
                                                        trusted ? head : head.getTarget(), null);
                                            }
                                        },
                                        new SCMSourceRequest.LazyRevisionLambda<PullRequestSCMHead, SCMRevision, Void>() {
                                            @NonNull
                                            @Override
                                            public SCMRevision create(@NonNull PullRequestSCMHead head,
                                                                      @Nullable Void ignored)
                                                    throws IOException, InterruptedException {
                                                switch (strategy) {
                                                    case MERGE:
                                                        request.checkApiRateLimit();
                                                        GHRef mergeRef = ghRepository.getRef(
                                                                "heads/" + pr.getBase().getRef()
                                                        );
                                                        return new PullRequestSCMRevision(head,
                                                                mergeRef.getObject().getSha(),
                                                                pr.getHead().getSha());
                                                    default:
                                                        return new PullRequestSCMRevision(head, pr.getBase().getSha(),
                                                                pr.getHead().getSha());
                                                }
                                            }
                                        },
                                        new MergabilityWitness(pr, strategy, listener),
                                        new CriteriaWitness(listener)
                                )) {
                                    listener.getLogger().format(
                                            "%n  %d pull requests were processed (query completed)%n",
                                            count
                                    );
                                    break PRs;
                                } else {
                                    request.checkApiRateLimit();
                                }
                            }
                        }
                        listener.getLogger().format("%n  %d pull requests were processed%n", count);
                    }
                    if (request.isFetchTags() && !request.isComplete()) {
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
                            listener.getLogger().format("%n    Checking tag %s%n", HyperlinkNote
                                    .encodeTo(repositoryUrl + "/tree/" + tagName, tagName));
                            long tagDate = 0L;
                            String sha = tag.getObject().getSha();
                            if ("tag".equalsIgnoreCase(tag.getObject().getType())) {
                                // annotated tag object
                                try {
                                    GHTagObject tagObject = request.getRepository().getTagObject(sha);
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
                            if (request.process(head, new GitTagSCMRevision(head, sha),
                                    new SCMSourceRequest.ProbeLambda<GitHubTagSCMHead, GitTagSCMRevision>() {
                                        @NonNull
                                        @Override
                                        public SCMSourceCriteria.Probe create(@NonNull GitHubTagSCMHead head,
                                                                              @Nullable GitTagSCMRevision revisionInfo)
                                                throws IOException, InterruptedException {
                                            return new GitHubSCMProbe(github, ghRepository, head, revisionInfo);
                                        }
                                    }, new CriteriaWitness(listener))) {
                                listener.getLogger()
                                        .format("%n  %d tags were processed (query completed)%n", count);
                                break;
                            } else {
                                request.checkApiRateLimit();
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

    @NonNull
    @Override
    protected Set<String> retrieveRevisions(@NonNull TaskListener listener) throws IOException, InterruptedException {
        StandardCredentials credentials = Connector.lookupScanCredentials((Item) getOwner(), apiUri, credentialsId);
        // Github client and validation
        final GitHub github = Connector.connect(apiUri, credentials);
        try {
            checkApiUrlValidity(github);
            Connector.checkApiRateLimit(listener, github);
            Set<String> result = new TreeSet<>();

            try {
                // Input data validation
                Connector.checkConnectionValidity(apiUri, listener, credentials, github);

                // Input data validation
                if (StringUtils.isBlank(repository)) {
                    throw new AbortException("No repository selected, skipping");
                }

                String fullName = repoOwner + "/" + repository;
                ghRepository = github.getRepository(fullName);
                final GHRepository ghRepository = this.ghRepository;
                listener.getLogger().format("Listing %s%n",
                        HyperlinkNote.encodeTo(ghRepository.getHtmlUrl().toString(), fullName));
                repositoryUrl = ghRepository.getHtmlUrl();
                GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, SCMHeadObserver.none())
                        .withTraits(traits);
                boolean wantBranches = context.wantBranches();
                boolean wantTags = context.wantTags();
                boolean wantPRs = context.wantPRs();
                boolean wantSinglePRs = context.forkPRStrategies().size() == 1 || context.originPRStrategies().size() == 1;
                boolean wantMultiPRs = context.forkPRStrategies().size() > 1 || context.originPRStrategies().size() > 1;
                Set<ChangeRequestCheckoutStrategy> strategies = new TreeSet<>();
                strategies.addAll(context.forkPRStrategies());
                strategies.addAll(context.originPRStrategies());
                for (GHRef ref: ghRepository.listRefs()) {
                    String name = ref.getRef();
                    if (name.startsWith(Constants.R_HEADS) && wantBranches) {
                        String branchName = name.substring(Constants.R_HEADS.length());
                        listener.getLogger().format("%n  Found branch %s%n", HyperlinkNote
                                .encodeTo(repositoryUrl + "/tree/" + branchName, branchName));
                        result.add(branchName);
                        continue;
                    }
                    if (name.startsWith(R_PULL) && wantPRs) {
                        int index = name.indexOf('/', R_PULL.length());
                        if (index != -1) {
                            String number = name.substring(R_PULL.length(), index);
                            listener.getLogger().format("%n  Found pull request %s%n", HyperlinkNote
                                    .encodeTo(repositoryUrl + "/pull/" + number, "#" + number));
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
                                for (ChangeRequestCheckoutStrategy strategy: strategies) {
                                    result.add("PR-" + number + "-" + strategy.name().toLowerCase(Locale.ENGLISH));
                                }
                            }
                        }
                        continue;
                    }
                    if (name.startsWith(Constants.R_TAGS) && wantTags) {
                        String tagName = name.substring(Constants.R_TAGS.length());
                        listener.getLogger().format("%n  Found tag %s%n", HyperlinkNote
                                .encodeTo(repositoryUrl + "/tree/" + tagName, tagName));
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
    protected SCMRevision retrieve(@NonNull String headName, @NonNull TaskListener listener)
            throws IOException, InterruptedException {
        StandardCredentials credentials = Connector.lookupScanCredentials((Item) getOwner(), apiUri, credentialsId);
        // Github client and validation
        final GitHub github = Connector.connect(apiUri, credentials);
        try {
            checkApiUrlValidity(github);
            Connector.checkApiRateLimit(listener, github);
            // Input data validation
            if (StringUtils.isBlank(repository)) {
                throw new AbortException("No repository selected, skipping");
            }

            String fullName = repoOwner + "/" + repository;
            ghRepository = github.getRepository(fullName);
            final GHRepository ghRepository = this.ghRepository;
            GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, SCMHeadObserver.none())
                    .withTraits(traits);
            Matcher prMatcher = Pattern.compile("^PR-(\\d+)(-.*)?$").matcher(headName);
            if (prMatcher.matches()) {
                // it's a looking very much like a PR
                int number = Integer.parseInt(prMatcher.group(1));
                try {
                    GHPullRequest pr = ghRepository.getPullRequest(number);
                    if (pr != null && context.wantPRs()) {
                        boolean fork = !ghRepository.getOwner().equals(pr.getHead().getUser());
                        Set<ChangeRequestCheckoutStrategy> strategies = fork
                                ? context.forkPRStrategies()
                                : context.originPRStrategies();
                        ChangeRequestCheckoutStrategy strategy;
                        if (prMatcher.group(2) == null) {
                            if (strategies.size() == 1) {
                                strategy = strategies.iterator().next();
                            } else {
                                // invalid name
                                return null;
                            }
                        } else {
                            strategy = null;
                            for (ChangeRequestCheckoutStrategy s: strategies) {
                                if (s.name().toLowerCase(Locale.ENGLISH).equals(prMatcher.group(2))) {
                                    strategy = s;
                                    break;
                                }
                            }
                            if (strategy == null) {
                                // invalid name;
                                return null;
                            }
                        }
                        PullRequestSCMHead head = new PullRequestSCMHead(
                                pr, headName, strategy == ChangeRequestCheckoutStrategy.MERGE
                        );
                        switch (strategy) {
                            case MERGE:
                                Connector.checkApiRateLimit(listener, github);
                                GHRef mergeRef = ghRepository.getRef(
                                        "heads/" + pr.getBase().getRef()
                                );
                                return new PullRequestSCMRevision(head,
                                        mergeRef.getObject().getSha(),
                                        pr.getHead().getSha());
                            default:
                                return new PullRequestSCMRevision(head, pr.getBase().getSha(),
                                        pr.getHead().getSha());
                        }

                    } else {
                        return null;
                    }
                } catch (FileNotFoundException e) {
                    // maybe some ****er created a branch or a tag called PR-_
                }
            }
            try {
                GHBranch branch = ghRepository.getBranch(headName);
                if (branch != null) {
                    return new SCMRevisionImpl(new BranchSCMHead(headName), branch.getSHA1());
                }
            } catch (FileNotFoundException e) {
                // maybe it's a tag
            }
            try {
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
                    return new GitTagSCMRevision(new GitHubTagSCMHead(headName, tagDate), tagSha);
                }
            } catch (FileNotFoundException e) {
                // ok it doesn't exist
            }
            return null;
        } finally {
            Connector.release(github);
        }
    }

    @NonNull
    private Set<String> updateCollaboratorNames(@NonNull TaskListener listener, @CheckForNull StandardCredentials credentials,
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

    private void checkApiUrlValidity(GitHub github) throws IOException {
        try {
            github.checkApiUrlValidity();
        } catch (HttpException e) {
            String message = String.format("It seems %s is unreachable", apiUri == null ? GITHUB_URL : apiUri);
            throw new AbortException(message);
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
        StandardCredentials credentials = Connector.lookupScanCredentials((Item) getOwner(), apiUri, credentialsId);
        // Github client and validation
        GitHub github = Connector.connect(apiUri, credentials);
        try {
            String fullName = repoOwner + "/" + repository;
            final GHRepository repo = github.getRepository(fullName);
            return new GitHubSCMProbe(github, repo, head, revision);
        } catch (IOException | RuntimeException | Error e) {
            Connector.release(github);
            throw e;
        }
    }

    @Override
    @CheckForNull
    protected SCMRevision retrieve(SCMHead head, TaskListener listener) throws IOException, InterruptedException {
        StandardCredentials credentials = Connector.lookupScanCredentials((Item) getOwner(), apiUri, credentialsId);

        // Github client and validation
        GitHub github = Connector.connect(apiUri, credentials);
        try {
            checkApiUrlValidity(github);

            try {
                Connector.checkConnectionValidity(apiUri, listener, credentials, github);
                Connector.checkApiRateLimit(listener, github);
                String fullName = repoOwner + "/" + repository;
                ghRepository = github.getRepository(fullName);
                repositoryUrl = ghRepository.getHtmlUrl();
                if (head instanceof PullRequestSCMHead) {
                    PullRequestSCMHead prhead = (PullRequestSCMHead) head;
                    int number = prhead.getNumber();
                    GHPullRequest pr = ghRepository.getPullRequest(number);
                    String baseHash;
                    switch (prhead.getCheckoutStrategy()) {
                        case MERGE:
                            baseHash = ghRepository.getRef("heads/" + prhead.getTarget().getName()).getObject().getSha();
                            break;
                        default:
                            baseHash = pr.getBase().getSha();
                            break;
                    }
                    return new PullRequestSCMRevision(prhead, baseHash, pr.getHead().getSha());
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
                    return new SCMRevisionImpl(head, ghRepository.getRef("heads/" + head.getName()).getObject().getSha());
                }
            } catch (RateLimitExceededException rle) {
                throw new AbortException(rle.getMessage());
            }
        } finally {
            Connector.release(github);
        }
    }

    @Override
    public SCM build(SCMHead head, SCMRevision revision) {
        return new GitHubSCMBuilder(this, head, revision).withTraits(traits).build();
    }

    @CheckForNull
    /*package*/ URL getRepositoryUrl() {
        return repositoryUrl;
    }

    @Deprecated // TODO remove once migration from 1.x is no longer supported
    PullRequestSource retrievePullRequestSource(int number) {
        // we use a big honking great lock to prevent concurrent requests to github during job loading
        Map<Integer, PullRequestSource> pullRequestSourceMap;
        synchronized (pullRequestSourceMapLock) {
            pullRequestSourceMap = this.pullRequestSourceMap;
            if (pullRequestSourceMap == null) {
                this.pullRequestSourceMap = pullRequestSourceMap = new HashMap<>();
                if (repository != null && !repository.isEmpty()) {
                    String fullName = repoOwner + "/" + repository;
                    LOGGER.log(Level.INFO, "Getting remote pull requests from {0}", fullName);
                    StandardCredentials credentials =
                            Connector.lookupScanCredentials((Item) getOwner(), apiUri, credentialsId);
                    LogTaskListener listener = new LogTaskListener(LOGGER, Level.INFO);
                    try {
                        GitHub github = Connector.connect(apiUri, credentials);
                        try {
                            checkApiUrlValidity(github);
                            Connector.checkApiRateLimit(listener, github);
                            ghRepository = github.getRepository(fullName);
                            LOGGER.log(Level.INFO, "Got remote pull requests from {0}", fullName);
                            int n = 0;
                            for (GHPullRequest pr: ghRepository.queryPullRequests().state(GHIssueState.OPEN).list()) {
                                GHRepository repository = pr.getHead().getRepository();
                                // JENKINS-41246 repository may be null for deleted forks
                                pullRequestSourceMap.put(pr.getNumber(), new PullRequestSource(
                                        repository == null ? null : repository.getOwnerName(),
                                        repository == null ? null : repository.getName(),
                                        pr.getHead().getRef()));
                                n++;
                                if (n % 30  == 0) { // default page size is 30
                                    Connector.checkApiRateLimit(listener, github);
                                }
                            }
                        } finally {
                            Connector.release(github);
                        }
                    } catch (IOException | InterruptedException e) {
                        LOGGER.log(Level.WARNING,
                                "Could not get all pull requests from " + fullName + ", there may be rebuilds", e);
                    }
                }
            }
            return pullRequestSourceMap.get(number);
        }
    }

    /**
     * Retained to migrate legacy configuration.
     * @deprecated use {@link MergeWithGitSCMExtension}.
     */
    @Restricted(DoNotUse.class)
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
            return new MergeWithGitSCMExtension("remotes/origin/"+baseName, baseHash);
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
                            .format("It seems %s is unreachable, assuming no trusted collaborators%n",
                                    apiUri == null ? GITHUB_URL : apiUri);
                    collaboratorNames = Collections.singleton(repoOwner);
                }
            }
            PullRequestSCMRevision rev = (PullRequestSCMRevision) revision;
            listener.getLogger().format("Loading trusted files from base branch %s at %s rather than %s%n",
                    head.getTarget().getName(), rev.getBaseHash(), rev.getPullHash());
            return new SCMRevisionImpl(head.getTarget(), rev.getBaseHash());
        }
        return revision;
    }

    /**
     * {@inheritDoc}
     */
    protected boolean isCategoryEnabled(@NonNull SCMHeadCategory category) {
        for (SCMSourceTrait trait : traits) {
            if (trait.isCategoryEnabled(category)) {
                return true;
            }
        }
        return false;
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
        StandardCredentials credentials = Connector.lookupScanCredentials((Item) getOwner(), apiUri, credentialsId);
        GitHub hub = Connector.connect(apiUri, credentials);
        try {
            Connector.checkConnectionValidity(apiUri, listener, credentials, hub);
            try {
                ghRepository = hub.getRepository(getRepoOwner() + '/' + repository);
                repositoryUrl = ghRepository.getHtmlUrl();
            } catch (FileNotFoundException e) {
                throw new AbortException(
                        String.format("Invalid scan credentials when using %s to connect to %s/%s on %s",
                                credentials == null ? "anonymous access" : CredentialsNameProvider.name(credentials), repoOwner, repository, apiUri == null ? GITHUB_URL : apiUri));
            }
            result.add(new ObjectMetadataAction(null, ghRepository.getDescription(), Util.fixEmpty(ghRepository.getHomepage())));
            result.add(new GitHubLink("icon-github-repo", ghRepository.getHtmlUrl()));
            if (StringUtils.isNotBlank(ghRepository.getDefaultBranch())) {
                result.add(new GitHubDefaultBranch(getRepoOwner(), repository, ghRepository.getDefaultBranch()));
            }
            return result;
        } finally {
            Connector.release(hub);
        }
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

    @Symbol("github")
    @Extension
    public static class DescriptorImpl extends SCMSourceDescriptor {

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
        // Prior to JENKINS-33161 the unconditional behavior was to build fork PRs plus origin branches, and try to build a merge revision for PRs.
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
            XSTREAM2.addCompatibilityAlias("org.jenkinsci.plugins.github_branch_source.OriginGitHubSCMSource", GitHubSCMSource.class);
        }

        @Override
        public String getDisplayName() {
            return Messages.GitHubSCMSource_DisplayName();
        }

        public ListBoxModel doFillCredentialsIdItems(@CheckForNull @AncestorInPath Item context,
                                                     @QueryParameter String apiUri,
                                                     @QueryParameter String credentialsId) {
            if (context == null
                    ? !Jenkins.getActiveInstance().hasPermission(Jenkins.ADMINISTER)
                    : !context.hasPermission(Item.EXTENDED_READ)) {
                return new StandardListBoxModel().includeCurrentValue(credentialsId);
            }
            return Connector.listScanCredentials(context, apiUri);
        }

        @RequirePOST
        @Restricted(NoExternalUse.class)
        public FormValidation doCheckCredentialsId(@CheckForNull @AncestorInPath Item context,
                                                       @QueryParameter String apiUri,
                                                       @QueryParameter String value) {
            return Connector.checkScanCredentials(context, apiUri, value);
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
        public FormValidation doCheckScanCredentialsId(@CheckForNull @AncestorInPath Item context,
                                                       @QueryParameter String apiUri,
                                                       @QueryParameter String scanCredentialsId) {
            return doCheckCredentialsId(context, apiUri, scanCredentialsId);
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
                result.add(e.getName() == null ? e.getApiUri() : e.getName() + " (" + e.getApiUri() + ")",
                        e.getApiUri());
            }
            return result;
        }

        public boolean isApiUriSelectable() {
            return !GitHubConfiguration.get().getEndpoints().isEmpty();
        }

        @RequirePOST
        public ListBoxModel doFillRepositoryItems(@CheckForNull @AncestorInPath Item context, @QueryParameter String apiUri,
                @QueryParameter String credentialsId, @QueryParameter String repoOwner) throws IOException {

            repoOwner = Util.fixEmptyAndTrim(repoOwner);
            if (repoOwner == null) {
                return new ListBoxModel();
            }
            if (context == null && !Jenkins.getActiveInstance().hasPermission(Jenkins.ADMINISTER) ||
                context != null && !context.hasPermission(Item.EXTENDED_READ)) {
                return new ListBoxModel(); // not supposed to be seeing this form
            }
            if (context != null && !context.hasPermission(CredentialsProvider.USE_ITEM)) {
                return new ListBoxModel(); // not permitted to try connecting with these credentials
            }
            try {
                StandardCredentials credentials = Connector.lookupScanCredentials(context, apiUri, credentialsId);
                GitHub github = Connector.connect(apiUri, credentials);
                try {

                    if (!github.isAnonymous()) {
                        GHMyself myself = null;
                        try {
                            myself = github.getMyself();
                        } catch (IllegalStateException e) {
                            LOGGER.log(Level.WARNING, e.getMessage(), e);
                            throw new FillErrorResponse(e.getMessage(), false);
                        } catch (IOException e) {
                            LogRecord lr = new LogRecord(Level.WARNING,
                                    "Exception retrieving the repositories of the owner {0} on {1} with credentials {2}");
                            lr.setThrown(e);
                            lr.setParameters(new Object[]{
                                    repoOwner, apiUri,
                                    credentials == null
                                            ? "anonymous access"
                                            : CredentialsNameProvider.name(credentials)
                            });
                            LOGGER.log(lr);
                            throw new FillErrorResponse(e.getMessage(), false);
                        }
                        if (myself != null && repoOwner.equalsIgnoreCase(myself.getLogin())) {
                            Set<String> result = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
                            for (GHRepository repo : myself.listRepositories(100, GHMyself.RepositoryListFilter.OWNER)) {
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
                        LogRecord lr = new LogRecord(Level.WARNING,
                                "Exception retrieving the repositories of the organization {0} on {1} with credentials {2}");
                        lr.setThrown(e);
                        lr.setParameters(new Object[]{
                                repoOwner, apiUri,
                                credentials == null
                                        ? "anonymous access"
                                        : CredentialsNameProvider.name(credentials)
                        });
                        LOGGER.log(lr);
                        throw new FillErrorResponse(e.getMessage(), false);
                    }
                    if (org != null && repoOwner.equalsIgnoreCase(org.getLogin())) {
                        Set<String> result = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
                        LOGGER.log(Level.FINE, "as {0} looking for repositories in {1}",
                                new Object[]{credentialsId, repoOwner});
                        for (GHRepository repo : org.listRepositories(100)) {
                            LOGGER.log(Level.FINE, "as {0} found {1}/{2}",
                                    new Object[]{credentialsId, repoOwner, repo.getName()});
                            result.add(repo.getName());
                        }
                        LOGGER.log(Level.FINE, "as {0} result of {1} is {2}",
                                new Object[]{credentialsId, repoOwner, result});
                        return nameAndValueModel(result);
                    }

                    GHUser user = null;
                    try {
                        user = github.getUser(repoOwner);
                    } catch (FileNotFoundException fnf) {
                        LOGGER.log(Level.FINE, "There is not any GH User named {0}", repoOwner);
                    } catch (IOException e) {
                        LogRecord lr = new LogRecord(Level.WARNING,
                                "Exception retrieving the repositories of the user {0} on {1} with credentials {2}");
                        lr.setThrown(e);
                        lr.setParameters(new Object[]{
                                repoOwner, apiUri,
                                credentials == null
                                        ? "anonymous access"
                                        : CredentialsNameProvider.name(credentials)
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

        public List<NamedArrayList<? extends SCMTraitDescriptor<?>>> getTraitsDescriptorLists() {
            List<SCMTraitDescriptor<?>> all = new ArrayList<>();
            all.addAll(SCMSourceTrait._for(this, GitHubSCMSourceContext.class, null));
            all.addAll(SCMSourceTrait._for(this, null, GitHubSCMBuilder.class));
            Set<SCMTraitDescriptor<?>> dedup = new HashSet<>();
            for (Iterator<SCMTraitDescriptor<?>> iterator = all.iterator(); iterator.hasNext(); ) {
                SCMTraitDescriptor<?> d = iterator.next();
                if (dedup.contains(d)
                        || d instanceof GitBrowserSCMSourceTrait.DescriptorImpl) {
                    // remove any we have seen already and ban the browser configuration as it will always be github
                    iterator.remove();
                } else {
                    dedup.add(d);
                }
            }
            List<NamedArrayList<? extends SCMTraitDescriptor<?>>> result = new ArrayList<>();
            NamedArrayList.select(all, "Within repository", NamedArrayList
                            .anyOf(NamedArrayList.withAnnotation(Discovery.class),
                                    NamedArrayList.withAnnotation(Selection.class)),
                    true, result);
            NamedArrayList.select(all, "Additional", null, true, result);
            return result;
        }

        public List<SCMSourceTrait> getTraitsDefaults() {
            return Arrays.asList( // TODO finalize
                    new BranchDiscoveryTrait(true, false),
                    new OriginPullRequestDiscoveryTrait(EnumSet.of(ChangeRequestCheckoutStrategy.MERGE)),
                    new ForkPullRequestDiscoveryTrait(EnumSet.of(ChangeRequestCheckoutStrategy.MERGE), new ForkPullRequestDiscoveryTrait.TrustContributors())
            );
        }

        @NonNull
        @Override
        protected SCMHeadCategory[] createCategories() {
            return new SCMHeadCategory[]{
                    new UncategorizedSCMHeadCategory(Messages._GitHubSCMSource_UncategorizedCategory()),
                    new ChangeRequestSCMHeadCategory(Messages._GitHubSCMSource_ChangeRequestCategory()),
                    new TagSCMHeadCategory(Messages._GitHubSCMSource_TagCategory())
            };
        }

    }

    private class LazyPullRequests extends LazyIterable<GHPullRequest> implements Closeable {
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
                request.checkApiRateLimit();
                Set<Integer> prs = request.getRequestedPullRequestNumbers();
                if (prs != null && prs.size() == 1) {
                    Integer number = prs.iterator().next();
                    request.listener().getLogger().format("%n  Getting remote pull request #%d...%n", number);
                    GHPullRequest pullRequest = repo.getPullRequest(number);
                    if (pullRequest.getState() != GHIssueState.OPEN) {
                        return Collections.emptyList();
                    }
                    return new CacheUdatingIterable(Collections.singletonList(pullRequest));
                }
                Set<String> branchNames = request.getRequestedOriginBranchNames();
                if (branchNames != null && branchNames.size() == 1) { // TODO flag to check PRs are all origin PRs
                    // if we were including multiple PRs and they are not all from the same origin branch
                    // then branchNames would have a size > 1 therefore if the size is 1 we must only
                    // be after PRs that come from this named branch
                    String branchName = branchNames.iterator().next();
                    request.listener().getLogger().format(
                            "%n  Getting remote pull requests from branch %s...%n", branchName
                    );
                    return new CacheUdatingIterable(repo.queryPullRequests()
                            .state(GHIssueState.OPEN)
                            .head(repo.getOwnerName() + ":" + branchName)
                            .list());
                }
                request.listener().getLogger().format("%n  Getting remote pull requests...%n");
                fullScanRequested = true;
                return new CacheUdatingIterable(LazyPullRequests.this.repo.queryPullRequests()
                        .state(GHIssueState.OPEN)
                        .list());
            } catch (IOException | InterruptedException e) {
                throw new GitHubSCMSource.WrappedException(e);
            }
        }

        @Override
        public void close() throws IOException {
            if (fullScanRequested && iterationCompleted) {
                // we needed a full scan and the scan was completed, so trim the cache entries
                pullRequestMetadataCache.keySet().retainAll(pullRequestMetadataKeys);
                pullRequestContributorCache.keySet().retainAll(pullRequestMetadataKeys);
                if (Jenkins.getActiveInstance().getInitLevel().compareTo(InitMilestone.JOB_LOADED) > 0) {
                    // synchronization should be cheap as only writers would be looking for this just to
                    // write null
                    synchronized (pullRequestSourceMapLock) {
                        pullRequestSourceMap = null; // all data has to have been migrated
                    }
                }
            }
        }

        private class CacheUdatingIterable extends SinglePassIterable<GHPullRequest> {
            /**
             * A map of all fully populated {@link GHUser} entries we have fetched, keyed by {@link GHUser#getLogin()}.
             */
            private Map<String, GHUser> users = new HashMap<>();
            CacheUdatingIterable(Iterable<GHPullRequest> delegate) {
                super(delegate);
            }

            @Override
            public void observe(GHPullRequest pr) {
                int number = pr.getNumber();
                pullRequestMetadataCache.put(number,
                        new ObjectMetadataAction(
                                pr.getTitle(),
                                pr.getBody(),
                                pr.getHtmlUrl().toExternalForm()
                        )
                );
                try {
                    GHUser user = pr.getUser();
                    if (users.containsKey(user.getLogin())) {
                        // looked up this user already
                        user = users.get(user.getLogin());
                    } else {
                        // going to be making a request to populate the user record
                        request.checkApiRateLimit();
                    }
                    pullRequestContributorCache.put(number, new ContributorMetadataAction(
                            user.getLogin(),
                            user.getName(),
                            user.getEmail()
                    ));
                    // store the populated user record now that we have it
                    users.put(user.getLogin(), user);
                } catch (IOException | InterruptedException e) {
                    throw new WrappedException(e);
                }
                pullRequestMetadataKeys.add(number);
            }

            @Override
            public void completed() {
                // we have completed a full iteration of the PRs from the delegate
                iterationCompleted = true;
            }
        }
    }

    private static class LazyBranches extends LazyIterable<GHBranch> {
        private final GitHubSCMSourceRequest request;
        private final GHRepository repo;

        public LazyBranches(GitHubSCMSourceRequest request, GHRepository repo) {
            this.request = request;
            this.repo = repo;
        }

        @Override
        protected Iterable<GHBranch> create() {
            try {
                request.checkApiRateLimit();
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
                return repo.getBranches().values();
            } catch (IOException | InterruptedException e) {
                throw new GitHubSCMSource.WrappedException(e);
            }
        }
    }

    private static class LazyTags extends LazyIterable<GHRef> {
        private final GitHubSCMSourceRequest request;
        private final GHRepository repo;

        public LazyTags(GitHubSCMSourceRequest request, GHRepository repo) {
            this.request = request;
            this.repo = repo;
        }

        @Override
        protected Iterable<GHRef> create() {
            try {
                request.checkApiRateLimit();
                Set<String> tagNames = request.getRequestedTagNames();
                if (tagNames != null && tagNames.size() == 1) {
                    String tagName = tagNames.iterator().next();
                    request.listener().getLogger().format("%n  Getting remote tag %s...%n", tagName);
                    return Collections.singletonList(repo.getRef("tags/" + tagName));
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
                                return Collections.<GHRef>emptyList().iterator();
                            }
                            throw e;
                        }
                        return new Iterator<GHRef>() {
                            @Override
                            public boolean hasNext() {
                                try {
                                    return iterator.hasNext();
                                } catch (Error e) {
                                    if (e.getCause() instanceof GHFileNotFoundException) {
                                        return false;
                                    }
                                    throw e;
                                }
                            }

                            @Override
                            public GHRef next() {
                                try {
                                    return iterator.next();
                                } catch (Error e) {
                                    if (e.getCause() instanceof GHFileNotFoundException) {
                                        throw new NoSuchElementException();
                                    }
                                    throw e;
                                }
                            }

                            @Override
                            public void remove() {
                                throw new UnsupportedOperationException("remove");
                            }
                        };
                    }
                };
            } catch (IOException | InterruptedException e) {
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
        public void record(@NonNull PullRequestSCMHead head,
                           PullRequestSCMRevision revision, boolean isMatch) {
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

        public LazyContributorNames(GitHubSCMSourceRequest request,
                                    TaskListener listener, GitHub github, GHRepository repo,
                                    StandardCredentials credentials) {
            this.request = request;
            this.listener = listener;
            this.github = github;
            this.repo = repo;
            this.credentials = credentials;
        }

        /**
         * {@inheritDoc}
         */
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

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        protected Set<String> create() {
            if (collaboratorNames != null) {
                return collaboratorNames;
            }
            listener.getLogger().format("Connecting to %s to obtain list of collaborators for %s/%s%n",
                    apiUri == null ? GITHUB_URL : apiUri, repoOwner, repository);
            StandardCredentials credentials = Connector.lookupScanCredentials(
                    (Item) getOwner(), apiUri, credentialsId
            );
            // Github client and validation
            try {
                GitHub github = Connector.connect(apiUri, credentials);
                try {
                    checkApiUrlValidity(github);
                    Connector.checkApiRateLimit(listener, github);

                    // Input data validation
                    Connector.checkConnectionValidity(apiUri, listener, credentials, github);
                    // Input data validation
                    String credentialsName =
                            credentials == null
                                    ? "anonymous access"
                                    : CredentialsNameProvider.name(credentials);
                    if (credentials != null && !github.isCredentialValid()) {
                        listener.getLogger().format("Invalid scan credentials %s to connect to %s, "
                                        + "assuming no trusted collaborators%n",
                                credentialsName, apiUri == null ? GITHUB_URL : apiUri);
                        collaboratorNames = Collections.singleton(repoOwner);
                    } else {
                        if (!github.isAnonymous()) {
                            listener.getLogger()
                                    .format("Connecting to %s using %s%n",
                                            apiUri == null ? GITHUB_URL : apiUri,
                                            credentialsName);
                        } else {
                            listener.getLogger()
                                    .format("Connecting to %s with no credentials, anonymous access%n",
                                            apiUri == null ? GITHUB_URL : apiUri);
                        }

                        // Input data validation
                        if (repository == null || repository.isEmpty()) {
                            collaboratorNames = Collections.singleton(repoOwner);
                        } else {
                            request.checkApiRateLimit();
                            String fullName = repoOwner + "/" + repository;
                            ghRepository = github.getRepository(fullName);
                            repositoryUrl = ghRepository.getHtmlUrl();
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
                listener.getLogger().format("Connecting to %s to check permissions of obtain list of %s for %s/%s%n",
                        apiUri == null ? GITHUB_URL : apiUri, username, repoOwner, repository);
                StandardCredentials credentials = Connector.lookupScanCredentials(
                        (Item) getOwner(), apiUri, credentialsId
                );
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
