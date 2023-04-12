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

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.jenkinsci.plugins.github_branch_source.Connector.isCredentialValid;

import com.cloudbees.jenkins.GitHubWebHook;
import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.AbortException;
import hudson.Extension;
import hudson.RestrictedSince;
import hudson.Util;
import hudson.console.HyperlinkNote;
import hudson.model.Action;
import hudson.model.Item;
import hudson.model.TaskListener;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;
import jenkins.model.Jenkins;
import jenkins.plugins.git.traits.GitBrowserSCMSourceTrait;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.SCMNavigatorDescriptor;
import jenkins.scm.api.SCMNavigatorEvent;
import jenkins.scm.api.SCMNavigatorOwner;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCategory;
import jenkins.scm.api.SCMSourceObserver;
import jenkins.scm.api.metadata.ObjectMetadataAction;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import jenkins.scm.api.trait.SCMHeadAuthority;
import jenkins.scm.api.trait.SCMNavigatorRequest;
import jenkins.scm.api.trait.SCMNavigatorTrait;
import jenkins.scm.api.trait.SCMNavigatorTraitDescriptor;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMTrait;
import jenkins.scm.api.trait.SCMTraitDescriptor;
import jenkins.scm.impl.UncategorizedSCMSourceCategory;
import jenkins.scm.impl.form.NamedArrayList;
import jenkins.scm.impl.trait.Discovery;
import jenkins.scm.impl.trait.RegexSCMSourceFilterTrait;
import jenkins.scm.impl.trait.Selection;
import jenkins.scm.impl.trait.WildcardSCMHeadFilterTrait;
import net.jcip.annotations.GuardedBy;
import org.apache.commons.lang.StringUtils;
import org.jenkins.ui.icon.Icon;
import org.jenkins.ui.icon.IconFormat;
import org.jenkins.ui.icon.IconSet;
import org.jenkins.ui.icon.IconSpec;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.github.config.GitHubServerConfig;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.github.GHMyself;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHRepositorySearchBuilder;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.HttpException;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

public class GitHubSCMNavigator extends SCMNavigator {

    /** The owner of the repositories to navigate. */
    @NonNull
    private final String repoOwner;

    /** The API endpoint for the GitHub server. */
    @CheckForNull
    private String apiUri;
    /**
     * The credentials to use when accessing {@link #apiUri} (and also the default credentials to use
     * for checking out).
     */
    @CheckForNull
    private String credentialsId;
    /** The behavioural traits to apply. */
    @NonNull
    private List<SCMTrait<? extends SCMTrait<?>>> traits;

    /**
     * Legacy configuration field
     *
     * @deprecated use {@link #credentialsId}.
     */
    @Deprecated
    private transient String scanCredentialsId;
    /**
     * Legacy configuration field
     *
     * @deprecated use {@link SSHCheckoutTrait}.
     */
    @Deprecated
    private transient String checkoutCredentialsId;
    /**
     * Legacy configuration field
     *
     * @deprecated use {@link RegexSCMSourceFilterTrait}.
     */
    @Deprecated
    private transient String pattern;
    /**
     * Legacy configuration field
     *
     * @deprecated use {@link WildcardSCMHeadFilterTrait}.
     */
    @Deprecated
    private String includes;
    /**
     * Legacy configuration field
     *
     * @deprecated use {@link WildcardSCMHeadFilterTrait}.
     */
    @Deprecated
    private String excludes;
    /**
     * Legacy configuration field
     *
     * @deprecated use {@link BranchDiscoveryTrait}.
     */
    @Deprecated
    private transient Boolean buildOriginBranch;
    /**
     * Legacy configuration field
     *
     * @deprecated use {@link BranchDiscoveryTrait}.
     */
    @Deprecated
    private transient Boolean buildOriginBranchWithPR;
    /**
     * Legacy configuration field
     *
     * @deprecated use {@link OriginPullRequestDiscoveryTrait}.
     */
    @Deprecated
    private transient Boolean buildOriginPRMerge;
    /**
     * Legacy configuration field
     *
     * @deprecated use {@link OriginPullRequestDiscoveryTrait}.
     */
    @Deprecated
    private transient Boolean buildOriginPRHead;
    /**
     * Legacy configuration field
     *
     * @deprecated use {@link ForkPullRequestDiscoveryTrait}.
     */
    @Deprecated
    private transient Boolean buildForkPRMerge;
    /**
     * Legacy configuration field
     *
     * @deprecated use {@link ForkPullRequestDiscoveryTrait}.
     */
    @Deprecated
    private transient Boolean buildForkPRHead;

    /**
     * Constructor.
     *
     * @param repoOwner the owner of the repositories to navigate.
     * @since 2.2.0
     */
    @DataBoundConstructor
    public GitHubSCMNavigator(String repoOwner) {
        this.repoOwner = StringUtils.defaultString(repoOwner);
        this.traits = new ArrayList<>();
    }

    /**
     * Legacy constructor.
     *
     * @param apiUri the API endpoint for the GitHub server.
     * @param repoOwner the owner of the repositories to navigate.
     * @param scanCredentialsId the credentials to use when accessing {@link #apiUri} (and also the
     *     default credentials to use for checking out).
     * @param checkoutCredentialsId the credentials to use when checking out.
     * @deprecated use {@link #GitHubSCMNavigator(String)}, {@link #setApiUri(String)}, {@link
     *     #setCredentialsId(String)} and {@link SSHCheckoutTrait}
     */
    @Deprecated
    @Restricted(DoNotUse.class)
    @RestrictedSince("2.2.0")
    public GitHubSCMNavigator(String apiUri, String repoOwner, String scanCredentialsId, String checkoutCredentialsId) {
        this(repoOwner);
        setCredentialsId(scanCredentialsId);
        setApiUri(apiUri);
        // legacy constructor means legacy defaults
        this.traits = new ArrayList<>();
        this.traits.add(new BranchDiscoveryTrait(true, true));
        this.traits.add(new ForkPullRequestDiscoveryTrait(
                EnumSet.of(ChangeRequestCheckoutStrategy.MERGE), new ForkPullRequestDiscoveryTrait.TrustPermission()));
        if (!GitHubSCMSource.DescriptorImpl.SAME.equals(checkoutCredentialsId)) {
            traits.add(new SSHCheckoutTrait(checkoutCredentialsId));
        }
    }

    /**
     * Gets the API endpoint for the GitHub server.
     *
     * @return the API endpoint for the GitHub server.
     */
    @CheckForNull
    public String getApiUri() {
        return apiUri;
    }

    /**
     * Sets the API endpoint for the GitHub server.
     *
     * @param apiUri the API endpoint for the GitHub server.
     * @since 2.2.0
     */
    @DataBoundSetter
    public void setApiUri(String apiUri) {
        if (isBlank(apiUri)) {
            this.apiUri = GitHubServerConfig.GITHUB_URL;
        } else {
            this.apiUri = GitHubConfiguration.normalizeApiUri(Util.fixEmptyAndTrim(apiUri));
        }
    }

    /**
     * Gets the {@link StandardCredentials#getId()} of the credentials to use when accessing {@link
     * #apiUri} (and also the default credentials to use for checking out).
     *
     * @return the {@link StandardCredentials#getId()} of the credentials to use when accessing {@link
     *     #apiUri} (and also the default credentials to use for checking out).
     * @since 2.2.0
     */
    @CheckForNull
    public String getCredentialsId() {
        return credentialsId;
    }

    /**
     * Sets the {@link StandardCredentials#getId()} of the credentials to use when accessing {@link
     * #apiUri} (and also the default credentials to use for checking out).
     *
     * @param credentialsId the {@link StandardCredentials#getId()} of the credentials to use when
     *     accessing {@link #apiUri} (and also the default credentials to use for checking out).
     * @since 2.2.0
     */
    @DataBoundSetter
    public void setCredentialsId(@CheckForNull String credentialsId) {
        this.credentialsId = Util.fixEmpty(credentialsId);
    }

    /**
     * Gets the name of the owner who's repositories will be navigated.
     *
     * @return the name of the owner who's repositories will be navigated.
     */
    @NonNull
    public String getRepoOwner() {
        return repoOwner;
    }

    /**
     * Gets the behavioural traits that are applied to this navigator and any {@link GitHubSCMSource}
     * instances it discovers.
     *
     * @return the behavioural traits.
     */
    @NonNull
    public List<SCMTrait<? extends SCMTrait<?>>> getTraits() {
        return Collections.unmodifiableList(traits);
    }

    /**
     * Sets the behavioural traits that are applied to this navigator and any {@link GitHubSCMSource}
     * instances it discovers. The new traits will take affect on the next navigation through any of
     * the {@link #visitSources(SCMSourceObserver)} overloads or {@link #visitSource(String,
     * SCMSourceObserver)}.
     *
     * @param traits the new behavioural traits.
     */
    @SuppressWarnings("unchecked")
    @DataBoundSetter
    public void setTraits(@CheckForNull SCMTrait[] traits) {
        // the reduced generics in the method signature are a workaround for JENKINS-26535
        this.traits = new ArrayList<>();
        if (traits != null) {
            for (SCMTrait trait : traits) {
                this.traits.add(trait);
            }
        }
    }

    /**
     * Sets the behavioural traits that are applied to this navigator and any {@link GitHubSCMSource}
     * instances it discovers. The new traits will take affect on the next navigation through any of
     * the {@link #visitSources(SCMSourceObserver)} overloads or {@link #visitSource(String,
     * SCMSourceObserver)}.
     *
     * @param traits the new behavioural traits.
     */
    @Override
    public void setTraits(@CheckForNull List<SCMTrait<? extends SCMTrait<?>>> traits) {
        this.traits = traits != null ? new ArrayList<>(traits) : new ArrayList<>();
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
        if (traits == null) {
            boolean buildOriginBranch = this.buildOriginBranch == null || this.buildOriginBranch;
            boolean buildOriginBranchWithPR = this.buildOriginBranchWithPR == null || this.buildOriginBranchWithPR;
            boolean buildOriginPRMerge = this.buildOriginPRMerge != null && this.buildOriginPRMerge;
            boolean buildOriginPRHead = this.buildOriginPRHead != null && this.buildOriginPRHead;
            boolean buildForkPRMerge = this.buildForkPRMerge == null || this.buildForkPRMerge;
            boolean buildForkPRHead = this.buildForkPRHead != null && this.buildForkPRHead;
            List<SCMTrait<? extends SCMTrait<?>>> traits = new ArrayList<>();
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
            if (checkoutCredentialsId != null
                    && !DescriptorImpl.SAME.equals(checkoutCredentialsId)
                    && !checkoutCredentialsId.equals(scanCredentialsId)) {
                traits.add(new SSHCheckoutTrait(checkoutCredentialsId));
            }
            if ((includes != null && !"*".equals(includes)) || (excludes != null && !"".equals(excludes))) {
                traits.add(new WildcardSCMHeadFilterTrait(
                        StringUtils.defaultIfBlank(includes, "*"), StringUtils.defaultIfBlank(excludes, "")));
            }
            if (pattern != null && !".*".equals(pattern)) {
                traits.add(new RegexSCMSourceFilterTrait(pattern));
            }
            this.traits = traits;
        }
        if (!StringUtils.equals(apiUri, GitHubConfiguration.normalizeApiUri(apiUri))) {
            setApiUri(apiUri);
        }
        return this;
    }

    /**
     * Legacy getter.
     *
     * @return {@link #getCredentialsId()}.
     * @deprecated use {@link #getCredentialsId()}.
     */
    @Deprecated
    @Restricted(DoNotUse.class)
    @RestrictedSince("2.2.0")
    @CheckForNull
    public String getScanCredentialsId() {
        return credentialsId;
    }

    /**
     * Legacy setter.
     *
     * @param scanCredentialsId the credentials.
     * @deprecated use {@link #setCredentialsId(String)}
     */
    @Deprecated
    @Restricted(DoNotUse.class)
    @RestrictedSince("2.2.0")
    @DataBoundSetter
    public void setScanCredentialsId(@CheckForNull String scanCredentialsId) {
        this.credentialsId = scanCredentialsId;
    }

    /**
     * Legacy getter.
     *
     * @return {@link WildcardSCMHeadFilterTrait#getIncludes()}
     * @deprecated use {@link WildcardSCMHeadFilterTrait}.
     */
    @Deprecated
    @Restricted(DoNotUse.class)
    @RestrictedSince("2.2.0")
    @NonNull
    public String getIncludes() {
        for (SCMTrait<?> trait : traits) {
            if (trait instanceof WildcardSCMHeadFilterTrait) {
                return ((WildcardSCMHeadFilterTrait) trait).getIncludes();
            }
        }
        return "*";
    }

    /**
     * Legacy getter.
     *
     * @return {@link WildcardSCMHeadFilterTrait#getExcludes()}
     * @deprecated use {@link WildcardSCMHeadFilterTrait}.
     */
    @Deprecated
    @Restricted(DoNotUse.class)
    @RestrictedSince("2.2.0")
    @NonNull
    public String getExcludes() {
        for (SCMTrait<?> trait : traits) {
            if (trait instanceof WildcardSCMHeadFilterTrait) {
                return ((WildcardSCMHeadFilterTrait) trait).getExcludes();
            }
        }
        return "";
    }

    /**
     * Legacy setter.
     *
     * @param includes see {@link WildcardSCMHeadFilterTrait#WildcardSCMHeadFilterTrait(String,
     *     String)}
     * @deprecated use {@link WildcardSCMHeadFilterTrait}.
     */
    @Deprecated
    @Restricted(DoNotUse.class)
    @RestrictedSince("2.2.0")
    @DataBoundSetter
    public void setIncludes(@NonNull String includes) {
        for (int i = 0; i < traits.size(); i++) {
            SCMTrait<?> trait = traits.get(i);
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

    /**
     * Legacy setter.
     *
     * @param excludes see {@link WildcardSCMHeadFilterTrait#WildcardSCMHeadFilterTrait(String,
     *     String)}
     * @deprecated use {@link WildcardSCMHeadFilterTrait}.
     */
    @Deprecated
    @Restricted(NoExternalUse.class)
    @RestrictedSince("2.2.0")
    @DataBoundSetter
    public void setExcludes(@NonNull String excludes) {
        for (int i = 0; i < traits.size(); i++) {
            SCMTrait<?> trait = traits.get(i);
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

    /**
     * Legacy getter.
     *
     * @return {@link BranchDiscoveryTrait#isBuildBranch()}.
     * @deprecated use {@link BranchDiscoveryTrait}
     */
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

    /**
     * Legacy setter.
     *
     * @param buildOriginBranch see {@link BranchDiscoveryTrait#BranchDiscoveryTrait(boolean,
     *     boolean)}.
     * @deprecated use {@link BranchDiscoveryTrait}
     */
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

    /**
     * Legacy getter.
     *
     * @return {@link BranchDiscoveryTrait#isBuildBranchesWithPR()}.
     * @deprecated use {@link BranchDiscoveryTrait}
     */
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

    /**
     * Legacy setter.
     *
     * @param buildOriginBranchWithPR see {@link BranchDiscoveryTrait#BranchDiscoveryTrait(boolean,
     *     boolean)}.
     * @deprecated use {@link BranchDiscoveryTrait}
     */
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

    /**
     * Legacy getter.
     *
     * @return {@link OriginPullRequestDiscoveryTrait#getStrategies()}.
     * @deprecated use {@link OriginPullRequestDiscoveryTrait#getStrategies()}
     */
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

    /**
     * Legacy setter.
     *
     * @param buildOriginPRMerge see {@link
     *     OriginPullRequestDiscoveryTrait#OriginPullRequestDiscoveryTrait(Set)}.
     * @deprecated use {@link OriginPullRequestDiscoveryTrait}
     */
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

    /**
     * Legacy getter.
     *
     * @return {@link OriginPullRequestDiscoveryTrait#getStrategies()}.
     * @deprecated use {@link OriginPullRequestDiscoveryTrait#getStrategies()}
     */
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

    /**
     * Legacy setter.
     *
     * @param buildOriginPRHead see {@link
     *     OriginPullRequestDiscoveryTrait#OriginPullRequestDiscoveryTrait(Set)}.
     * @deprecated use {@link OriginPullRequestDiscoveryTrait}
     */
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

    /**
     * Legacy getter.
     *
     * @return {@link ForkPullRequestDiscoveryTrait#getStrategies()}.
     * @deprecated use {@link ForkPullRequestDiscoveryTrait#getStrategies()}
     */
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

    /**
     * Legacy setter.
     *
     * @param buildForkPRMerge see {@link
     *     ForkPullRequestDiscoveryTrait#ForkPullRequestDiscoveryTrait(Set, SCMHeadAuthority)}.
     * @deprecated use {@link ForkPullRequestDiscoveryTrait}
     */
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

    /**
     * Legacy getter.
     *
     * @return {@link ForkPullRequestDiscoveryTrait#getStrategies()}.
     * @deprecated use {@link ForkPullRequestDiscoveryTrait#getStrategies()}
     */
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

    /**
     * Legacy setter.
     *
     * @param buildForkPRHead see {@link
     *     ForkPullRequestDiscoveryTrait#ForkPullRequestDiscoveryTrait(Set, SCMHeadAuthority)}.
     * @deprecated use {@link ForkPullRequestDiscoveryTrait}
     */
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
     * Legacy getter.
     *
     * @return {@link SSHCheckoutTrait#getCredentialsId()} with some mangling to preserve legacy
     *     behaviour.
     * @deprecated use {@link SSHCheckoutTrait}
     */
    @CheckForNull
    @Deprecated
    @Restricted(DoNotUse.class)
    @RestrictedSince("2.2.0")
    public String getCheckoutCredentialsId() {
        for (SCMTrait<?> trait : traits) {
            if (trait instanceof SSHCheckoutTrait) {
                return StringUtils.defaultString(
                        ((SSHCheckoutTrait) trait).getCredentialsId(), GitHubSCMSource.DescriptorImpl.ANONYMOUS);
            }
        }
        return DescriptorImpl.SAME;
    }

    /**
     * Legacy getter.
     *
     * @return {@link RegexSCMSourceFilterTrait#getRegex()}.
     * @deprecated use {@link RegexSCMSourceFilterTrait}
     */
    @Deprecated
    @Restricted(DoNotUse.class)
    @RestrictedSince("2.2.0")
    public String getPattern() {
        for (SCMTrait<?> trait : traits) {
            if (trait instanceof RegexSCMSourceFilterTrait) {
                return ((RegexSCMSourceFilterTrait) trait).getRegex();
            }
        }
        return ".*";
    }

    /**
     * Legacy setter.
     *
     * @param pattern see {@link RegexSCMSourceFilterTrait#RegexSCMSourceFilterTrait(String)}.
     * @deprecated use {@link RegexSCMSourceFilterTrait}
     */
    @Deprecated
    @Restricted(DoNotUse.class)
    @RestrictedSince("2.2.0")
    @DataBoundSetter
    public void setPattern(String pattern) {
        for (int i = 0; i < traits.size(); i++) {
            SCMTrait<?> trait = traits.get(i);
            if (trait instanceof RegexSCMSourceFilterTrait) {
                if (".*".equals(pattern)) {
                    traits.remove(i);
                } else {
                    traits.set(i, new RegexSCMSourceFilterTrait(pattern));
                }
                return;
            }
        }
        if (!".*".equals(pattern)) {
            traits.add(new RegexSCMSourceFilterTrait(pattern));
        }
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    protected String id() {
        final GitHubSCMNavigatorContext gitHubSCMNavigatorContext = new GitHubSCMNavigatorContext().withTraits(traits);
        if (!gitHubSCMNavigatorContext.getTopics().isEmpty()) {
            return StringUtils.defaultIfBlank(apiUri, GitHubSCMSource.GITHUB_URL)
                    + "::"
                    + repoOwner
                    + "::"
                    + String.join("::", gitHubSCMNavigatorContext.getTopics());
        }
        return StringUtils.defaultIfBlank(apiUri, GitHubSCMSource.GITHUB_URL) + "::" + repoOwner;
    }

    /** {@inheritDoc} */
    @Override
    public void visitSources(SCMSourceObserver observer) throws IOException, InterruptedException {
        Set<String> includes = observer.getIncludes();
        if (includes != null && includes.size() == 1) {
            // optimize for the single source case
            visitSource(includes.iterator().next(), observer);
            return;
        }
        TaskListener listener = observer.getListener();

        // Input data validation
        if (repoOwner.isEmpty()) {
            throw new AbortException("Must specify user or organization");
        }

        StandardCredentials credentials =
                Connector.lookupScanCredentials((Item) observer.getContext(), apiUri, credentialsId, repoOwner);

        // Github client and validation
        GitHub github = Connector.connect(apiUri, credentials);
        try {
            Connector.checkConnectionValidity(apiUri, listener, credentials, github);
            Connector.configureLocalRateLimitChecker(listener, github);

            // Input data validation
            if (credentials != null && !isCredentialValid(github)) {
                String message = String.format(
                        "Invalid scan credentials %s to connect to %s, skipping",
                        CredentialsNameProvider.name(credentials),
                        apiUri == null ? GitHubSCMSource.GITHUB_URL : apiUri);
                throw new AbortException(message);
            }

            GitHubSCMNavigatorContext gitHubSCMNavigatorContext =
                    new GitHubSCMNavigatorContext().withTraits(getTraits());

            try (GitHubSCMNavigatorRequest request = gitHubSCMNavigatorContext.newRequest(this, observer)) {
                SourceFactory sourceFactory = new SourceFactory(request);
                WitnessImpl witness = new WitnessImpl(listener);

                boolean githubAppAuthentication = credentials instanceof GitHubAppCredentials;
                if (github.isAnonymous()) {
                    listener.getLogger()
                            .format(
                                    "Connecting to %s with no credentials, anonymous access%n",
                                    apiUri == null ? GitHubSCMSource.GITHUB_URL : apiUri);
                } else if (!githubAppAuthentication) {
                    GHMyself myself;
                    try {
                        // Requires an authenticated access
                        myself = github.getMyself();
                    } catch (RateLimitExceededException rle) {
                        throw new AbortException(rle.getMessage());
                    }
                    if (myself != null && repoOwner.equalsIgnoreCase(myself.getLogin())) {
                        listener.getLogger()
                                .println(GitHubConsoleNote.create(
                                        System.currentTimeMillis(),
                                        String.format("Looking up repositories of myself %s", repoOwner)));
                        final Iterable<GHRepository> repositories;
                        if (!gitHubSCMNavigatorContext.getTopics().isEmpty()) {
                            listener.getLogger()
                                    .println(GitHubConsoleNote.create(
                                            System.currentTimeMillis(),
                                            String.format(
                                                    "Looking up repositories for topics: '%s'",
                                                    gitHubSCMNavigatorContext.getTopics())));
                            repositories = searchRepositories(github, gitHubSCMNavigatorContext);
                        } else {
                            repositories = myself.listRepositories(100);
                        }

                        for (GHRepository repo : repositories) {
                            if (!repoOwner.equals(repo.getOwnerName())) {
                                continue; // ignore repos in other orgs when using GHMyself
                            }

                            if (repo.isArchived() && gitHubSCMNavigatorContext.isExcludeArchivedRepositories()) {
                                witness.record(repo.getName(), false);
                                listener.getLogger()
                                        .println(GitHubConsoleNote.create(
                                                System.currentTimeMillis(),
                                                String.format(
                                                        "Skipping repository %s because it is archived",
                                                        repo.getName())));

                            } else if (!gitHubSCMNavigatorContext.getTopics().isEmpty()
                                    && !repo.listTopics().containsAll(gitHubSCMNavigatorContext.getTopics())) {
                                // exclude repositories which are missing one or more of the specified topics
                                witness.record(repo.getName(), false);
                                listener.getLogger()
                                        .println(GitHubConsoleNote.create(
                                                System.currentTimeMillis(),
                                                String.format(
                                                        "Skipping repository %s because it is missing one or more of the following topics: '%s'",
                                                        repo.getName(), gitHubSCMNavigatorContext.getTopics())));
                            } else if (!repo.isPrivate() && gitHubSCMNavigatorContext.isExcludePublicRepositories()) {
                                witness.record(repo.getName(), false);
                                listener.getLogger()
                                        .println(GitHubConsoleNote.create(
                                                System.currentTimeMillis(),
                                                String.format(
                                                        "Skipping repository %s because it is public",
                                                        repo.getName())));
                            } else if (repo.isPrivate() && gitHubSCMNavigatorContext.isExcludePrivateRepositories()) {
                                witness.record(repo.getName(), false);
                                listener.getLogger()
                                        .println(GitHubConsoleNote.create(
                                                System.currentTimeMillis(),
                                                String.format(
                                                        "Skipping repository %s because it is private",
                                                        repo.getName())));
                            } else if (gitHubSCMNavigatorContext.isExcludeForkedRepositories()
                                    && repo.getSource() != null) {
                                witness.record(repo.getName(), false);
                                listener.getLogger()
                                        .println(GitHubConsoleNote.create(
                                                System.currentTimeMillis(),
                                                String.format(
                                                        "Skipping repository %s because it is a fork",
                                                        repo.getName())));
                            } else if (request.process(repo.getName(), sourceFactory, null, witness)) {
                                listener.getLogger()
                                        .println(GitHubConsoleNote.create(
                                                System.currentTimeMillis(),
                                                String.format(
                                                        "%d repositories were processed (query completed)",
                                                        witness.getCount())));
                            }
                        }
                        listener.getLogger()
                                .println(GitHubConsoleNote.create(
                                        System.currentTimeMillis(),
                                        String.format("%d repositories were processed", witness.getCount())));
                        return;
                    }
                }
                GHOrganization org = getGhOrganization(github);
                if (org != null && repoOwner.equalsIgnoreCase(org.getLogin())) {
                    listener.getLogger()
                            .println(GitHubConsoleNote.create(
                                    System.currentTimeMillis(),
                                    String.format("Looking up repositories of organization %s", repoOwner)));
                    final Iterable<GHRepository> repositories;
                    if (StringUtils.isNotBlank(gitHubSCMNavigatorContext.getTeamSlug())) {
                        // get repositories for selected team
                        listener.getLogger()
                                .println(GitHubConsoleNote.create(
                                        System.currentTimeMillis(),
                                        String.format(
                                                "Looking up repositories for team %s",
                                                gitHubSCMNavigatorContext.getTeamSlug())));
                        repositories = org.getTeamBySlug(gitHubSCMNavigatorContext.getTeamSlug())
                                .listRepositories()
                                .withPageSize(100);
                    } else if (!gitHubSCMNavigatorContext.getTopics().isEmpty()) {
                        listener.getLogger()
                                .println(GitHubConsoleNote.create(
                                        System.currentTimeMillis(),
                                        String.format(
                                                "Looking up repositories for topics: '%s'",
                                                gitHubSCMNavigatorContext.getTopics())));
                        repositories = searchRepositories(github, gitHubSCMNavigatorContext);
                    } else {
                        repositories = org.listRepositories(100);
                    }
                    for (GHRepository repo : repositories) {
                        if (repo.isArchived() && gitHubSCMNavigatorContext.isExcludeArchivedRepositories()) {
                            // exclude archived repositories
                            witness.record(repo.getName(), false);
                            listener.getLogger()
                                    .println(GitHubConsoleNote.create(
                                            System.currentTimeMillis(),
                                            String.format(
                                                    "Skipping repository %s because it is archived", repo.getName())));
                        } else if (!gitHubSCMNavigatorContext.getTopics().isEmpty()
                                && !repo.listTopics().containsAll(gitHubSCMNavigatorContext.getTopics())) {
                            // exclude repositories which are missing one or more of the specified topics
                            witness.record(repo.getName(), false);
                            listener.getLogger()
                                    .println(GitHubConsoleNote.create(
                                            System.currentTimeMillis(),
                                            String.format(
                                                    "Skipping repository %s because it is missing one or more of the following topics: '%s'",
                                                    repo.getName(), gitHubSCMNavigatorContext.getTopics())));

                        } else if (!repo.isPrivate() && gitHubSCMNavigatorContext.isExcludePublicRepositories()) {
                            witness.record(repo.getName(), false);
                            listener.getLogger()
                                    .println(GitHubConsoleNote.create(
                                            System.currentTimeMillis(),
                                            String.format(
                                                    "Skipping repository %s because it is public", repo.getName())));

                        } else if (repo.isPrivate() && gitHubSCMNavigatorContext.isExcludePrivateRepositories()) {
                            witness.record(repo.getName(), false);
                            listener.getLogger()
                                    .println(GitHubConsoleNote.create(
                                            System.currentTimeMillis(),
                                            String.format(
                                                    "Skipping repository %s because it is private", repo.getName())));

                        } else if (gitHubSCMNavigatorContext.isExcludeForkedRepositories()
                                && repo.getSource() != null) {
                            witness.record(repo.getName(), false);
                            listener.getLogger()
                                    .println(GitHubConsoleNote.create(
                                            System.currentTimeMillis(),
                                            String.format(
                                                    "Skipping repository %s because it is a fork", repo.getName())));
                        } else if (request.process(repo.getName(), sourceFactory, null, witness)) {
                            listener.getLogger()
                                    .println(GitHubConsoleNote.create(
                                            System.currentTimeMillis(),
                                            String.format(
                                                    "%d repositories were processed (query completed)",
                                                    witness.getCount())));
                        }
                    }
                    listener.getLogger()
                            .println(GitHubConsoleNote.create(
                                    System.currentTimeMillis(),
                                    String.format("%d repositories were processed", witness.getCount())));
                    return;
                }

                GHUser user = null;
                try {
                    user = github.getUser(repoOwner);
                } catch (RateLimitExceededException rle) {
                    throw new AbortException(rle.getMessage());
                } catch (FileNotFoundException fnf) {
                    // the user may not exist... ok to ignore
                }
                if (user != null && repoOwner.equalsIgnoreCase(user.getLogin())) {
                    listener.getLogger().format("Looking up repositories of user %s%n%n", repoOwner);
                    for (GHRepository repo : user.listRepositories(100)) {
                        if (repo.isArchived() && gitHubSCMNavigatorContext.isExcludeArchivedRepositories()) {
                            witness.record(repo.getName(), false);
                            listener.getLogger()
                                    .println(GitHubConsoleNote.create(
                                            System.currentTimeMillis(),
                                            String.format(
                                                    "Skipping repository %s because it is archived", repo.getName())));

                        } else if (!gitHubSCMNavigatorContext.getTopics().isEmpty()
                                && !repo.listTopics().containsAll(gitHubSCMNavigatorContext.getTopics())) {
                            // exclude repositories which are missing one or more of the specified topics
                            witness.record(repo.getName(), false);
                            listener.getLogger()
                                    .println(GitHubConsoleNote.create(
                                            System.currentTimeMillis(),
                                            String.format(
                                                    "Skipping repository %s because it is missing one or more of the following topics: '%s'",
                                                    repo.getName(), gitHubSCMNavigatorContext.getTopics())));
                        } else if (gitHubSCMNavigatorContext.isExcludeForkedRepositories()
                                && repo.getSource() != null) {
                            witness.record(repo.getName(), false);
                            listener.getLogger()
                                    .println(GitHubConsoleNote.create(
                                            System.currentTimeMillis(),
                                            String.format(
                                                    "Skipping repository %s because it is a fork", repo.getName())));
                        } else if (request.process(repo.getName(), sourceFactory, null, witness)) {
                            listener.getLogger()
                                    .println(GitHubConsoleNote.create(
                                            System.currentTimeMillis(),
                                            String.format(
                                                    "%d repositories were processed (query completed)",
                                                    witness.getCount())));
                        }
                    }
                    listener.getLogger()
                            .println(GitHubConsoleNote.create(
                                    System.currentTimeMillis(),
                                    String.format("%d repositories were processed", witness.getCount())));
                    return;
                }

                throw new AbortException(
                        repoOwner + " does not correspond to a known GitHub User Account or Organization");
            }
        } finally {
            Connector.release(github);
        }
    }

    private Iterable<GHRepository> searchRepositories(final GitHub github, final GitHubSCMNavigatorContext context) {
        final GHRepositorySearchBuilder ghRepositorySearchBuilder = github.searchRepositories();
        context.getTopics().forEach(ghRepositorySearchBuilder::topic);
        ghRepositorySearchBuilder.org(getRepoOwner());
        if (!context.isExcludeForkedRepositories()) {
            ghRepositorySearchBuilder.q("fork:true");
        }
        return ghRepositorySearchBuilder.list().withPageSize(100);
    }

    private GHOrganization getGhOrganization(final GitHub github) throws IOException {
        try {
            return github.getOrganization(repoOwner);
        } catch (RateLimitExceededException rle) {
            throw new AbortException(rle.getMessage());
        } catch (FileNotFoundException fnf) {
            // may be an user... ok to ignore
        }
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public void visitSource(String sourceName, SCMSourceObserver observer) throws IOException, InterruptedException {
        TaskListener listener = observer.getListener();

        // Input data validation
        if (repoOwner.isEmpty()) {
            throw new AbortException("Must specify user or organization");
        }

        StandardCredentials credentials =
                Connector.lookupScanCredentials((Item) observer.getContext(), apiUri, credentialsId, repoOwner);

        // Github client and validation
        GitHub github;
        try {
            github = Connector.connect(apiUri, credentials);
        } catch (HttpException e) {
            throw new AbortException(e.getMessage());
        }

        try {
            // Input data validation
            if (credentials != null && !isCredentialValid(github)) {
                String message = String.format(
                        "Invalid scan credentials %s to connect to %s, skipping",
                        CredentialsNameProvider.name(credentials),
                        apiUri == null ? GitHubSCMSource.GITHUB_URL : apiUri);
                throw new AbortException(message);
            }

            GitHubSCMNavigatorContext gitHubSCMNavigatorContext = new GitHubSCMNavigatorContext().withTraits(traits);

            try (GitHubSCMNavigatorRequest request = gitHubSCMNavigatorContext.newRequest(this, observer)) {
                SourceFactory sourceFactory = new SourceFactory(request);
                WitnessImpl witness = new WitnessImpl(listener);

                boolean githubAppAuthentication = credentials instanceof GitHubAppCredentials;
                if (github.isAnonymous()) {
                    listener.getLogger()
                            .format(
                                    "Connecting to %s with no credentials, anonymous access%n",
                                    apiUri == null ? GitHubSCMSource.GITHUB_URL : apiUri);
                } else if (!githubAppAuthentication) {
                    listener.getLogger()
                            .format(
                                    "Connecting to %s using %s%n",
                                    apiUri == null ? GitHubSCMSource.GITHUB_URL : apiUri,
                                    CredentialsNameProvider.name(credentials));
                    GHMyself myself;
                    try {
                        // Requires an authenticated access
                        myself = github.getMyself();
                    } catch (RateLimitExceededException rle) {
                        throw new AbortException(rle.getMessage());
                    }
                    if (myself != null && repoOwner.equalsIgnoreCase(myself.getLogin())) {
                        listener.getLogger().format("Looking up %s repository of myself %s%n%n", sourceName, repoOwner);
                        GHRepository repo = myself.getRepository(sourceName);
                        if (repo != null && repo.getOwnerName().equals(repoOwner)) {

                            if (repo.isArchived() && gitHubSCMNavigatorContext.isExcludeArchivedRepositories()) {
                                witness.record(repo.getName(), false);
                                listener.getLogger()
                                        .println(GitHubConsoleNote.create(
                                                System.currentTimeMillis(),
                                                String.format(
                                                        "Skipping repository %s because it is archived",
                                                        repo.getName())));

                            } else if (!gitHubSCMNavigatorContext.getTopics().isEmpty()
                                    && !repo.listTopics().containsAll(gitHubSCMNavigatorContext.getTopics())) {
                                // exclude repositories which are missing one or more of the specified topics
                                witness.record(repo.getName(), false);
                                listener.getLogger()
                                        .println(GitHubConsoleNote.create(
                                                System.currentTimeMillis(),
                                                String.format(
                                                        "Skipping repository %s because it is missing one or more of the following topics: '%s'",
                                                        repo.getName(), gitHubSCMNavigatorContext.getTopics())));
                            } else if (!repo.isPrivate() && gitHubSCMNavigatorContext.isExcludePublicRepositories()) {
                                witness.record(repo.getName(), false);
                                listener.getLogger()
                                        .println(GitHubConsoleNote.create(
                                                System.currentTimeMillis(),
                                                String.format(
                                                        "Skipping repository %s because it is public",
                                                        repo.getName())));
                            } else if (repo.isPrivate() && gitHubSCMNavigatorContext.isExcludePrivateRepositories()) {
                                witness.record(repo.getName(), false);
                                listener.getLogger()
                                        .println(GitHubConsoleNote.create(
                                                System.currentTimeMillis(),
                                                String.format(
                                                        "Skipping repository %s because it is private",
                                                        repo.getName())));

                            } else if (gitHubSCMNavigatorContext.isExcludeForkedRepositories()
                                    && repo.getSource() != null) {
                                witness.record(repo.getName(), false);
                                listener.getLogger()
                                        .println(GitHubConsoleNote.create(
                                                System.currentTimeMillis(),
                                                String.format(
                                                        "Skipping repository %s because it is a fork",
                                                        repo.getName())));
                            } else if (request.process(repo.getName(), sourceFactory, null, witness)) {
                                listener.getLogger()
                                        .println(GitHubConsoleNote.create(
                                                System.currentTimeMillis(),
                                                String.format(
                                                        "%d repositories were processed (query completed)",
                                                        witness.getCount())));
                            }
                        }
                        listener.getLogger()
                                .println(GitHubConsoleNote.create(
                                        System.currentTimeMillis(),
                                        String.format("%d repositories were processed", witness.getCount())));
                        return;
                    }
                }

                GHOrganization org = getGhOrganization(github);
                if (org != null && repoOwner.equalsIgnoreCase(org.getLogin())) {
                    listener.getLogger()
                            .format("Looking up %s repository of organization %s%n%n", sourceName, repoOwner);
                    GHRepository repo = org.getRepository(sourceName);
                    if (repo != null) {

                        if (repo.isArchived() && gitHubSCMNavigatorContext.isExcludeArchivedRepositories()) {
                            witness.record(repo.getName(), false);
                            listener.getLogger()
                                    .println(GitHubConsoleNote.create(
                                            System.currentTimeMillis(),
                                            String.format(
                                                    "Skipping repository %s because it is archived", repo.getName())));

                        } else if (!gitHubSCMNavigatorContext.getTopics().isEmpty()
                                && !repo.listTopics().containsAll(gitHubSCMNavigatorContext.getTopics())) {
                            // exclude repositories which are missing one or more of the specified topics
                            witness.record(repo.getName(), false);
                            listener.getLogger()
                                    .println(GitHubConsoleNote.create(
                                            System.currentTimeMillis(),
                                            String.format(
                                                    "Skipping repository %s because it is missing one or more of the following topics: '%s'",
                                                    repo.getName(), gitHubSCMNavigatorContext.getTopics())));
                        } else if (StringUtils.isNotBlank(gitHubSCMNavigatorContext.getTeamSlug())
                                && !isRepositoryVisibleToTeam(org, repo, gitHubSCMNavigatorContext.getTeamSlug())) {
                            listener.getLogger()
                                    .println(GitHubConsoleNote.create(
                                            System.currentTimeMillis(),
                                            String.format(
                                                    "Skipping repository %s because it is not in team %s",
                                                    repo.getName(), gitHubSCMNavigatorContext.getTeamSlug())));
                        } else if (!repo.isPrivate() && gitHubSCMNavigatorContext.isExcludePublicRepositories()) {
                            witness.record(repo.getName(), false);
                            listener.getLogger()
                                    .println(GitHubConsoleNote.create(
                                            System.currentTimeMillis(),
                                            String.format(
                                                    "Skipping repository %s because it is public", repo.getName())));
                        } else if (repo.isPrivate() && gitHubSCMNavigatorContext.isExcludePrivateRepositories()) {
                            witness.record(repo.getName(), false);
                            listener.getLogger()
                                    .println(GitHubConsoleNote.create(
                                            System.currentTimeMillis(),
                                            String.format(
                                                    "Skipping repository %s because it is private", repo.getName())));
                        } else if (gitHubSCMNavigatorContext.isExcludeForkedRepositories()
                                && repo.getSource() != null) {
                            witness.record(repo.getName(), false);
                            listener.getLogger()
                                    .println(GitHubConsoleNote.create(
                                            System.currentTimeMillis(),
                                            String.format(
                                                    "Skipping repository %s because it is a fork", repo.getName())));

                        } else if (request.process(repo.getName(), sourceFactory, null, witness)) {
                            listener.getLogger()
                                    .println(GitHubConsoleNote.create(
                                            System.currentTimeMillis(),
                                            String.format(
                                                    "%d repositories were processed (query completed)",
                                                    witness.getCount())));
                        }
                    }
                    listener.getLogger()
                            .println(GitHubConsoleNote.create(
                                    System.currentTimeMillis(),
                                    String.format("%d repositories were processed", witness.getCount())));
                    return;
                }

                GHUser user = null;
                try {
                    user = github.getUser(repoOwner);
                } catch (RateLimitExceededException rle) {
                    throw new AbortException(rle.getMessage());
                } catch (FileNotFoundException fnf) {
                    // the user may not exist... ok to ignore
                }
                if (user != null && repoOwner.equalsIgnoreCase(user.getLogin())) {
                    listener.getLogger().format("Looking up %s repository of user %s%n%n", sourceName, repoOwner);
                    GHRepository repo = user.getRepository(sourceName);
                    if (repo != null) {

                        if (repo.isArchived() && gitHubSCMNavigatorContext.isExcludeArchivedRepositories()) {
                            witness.record(repo.getName(), false);
                            listener.getLogger()
                                    .println(GitHubConsoleNote.create(
                                            System.currentTimeMillis(),
                                            String.format(
                                                    "Skipping repository %s because it is archived", repo.getName())));

                        } else if (!gitHubSCMNavigatorContext.getTopics().isEmpty()
                                && !repo.listTopics().containsAll(gitHubSCMNavigatorContext.getTopics())) {
                            // exclude repositories which are missing one or more of the specified topics
                            witness.record(repo.getName(), false);
                            listener.getLogger()
                                    .println(GitHubConsoleNote.create(
                                            System.currentTimeMillis(),
                                            String.format(
                                                    "Skipping repository %s because it is missing one or more of the following topics: '%s'",
                                                    repo.getName(), gitHubSCMNavigatorContext.getTopics())));
                        } else if (!repo.isPrivate() && gitHubSCMNavigatorContext.isExcludePublicRepositories()) {
                            witness.record(repo.getName(), false);
                            listener.getLogger()
                                    .println(GitHubConsoleNote.create(
                                            System.currentTimeMillis(),
                                            String.format(
                                                    "Skipping repository %s because it is public", repo.getName())));
                        } else if (repo.isPrivate() && gitHubSCMNavigatorContext.isExcludePrivateRepositories()) {
                            witness.record(repo.getName(), false);
                            listener.getLogger()
                                    .println(GitHubConsoleNote.create(
                                            System.currentTimeMillis(),
                                            String.format(
                                                    "Skipping repository %s because it is private", repo.getName())));

                        } else if (gitHubSCMNavigatorContext.isExcludeForkedRepositories()
                                && repo.getSource() != null) {
                            witness.record(repo.getName(), false);
                            listener.getLogger()
                                    .println(GitHubConsoleNote.create(
                                            System.currentTimeMillis(),
                                            String.format(
                                                    "Skipping repository %s because it is a fork", repo.getName())));

                        } else if (request.process(repo.getName(), sourceFactory, null, witness)) {
                            listener.getLogger()
                                    .println(GitHubConsoleNote.create(
                                            System.currentTimeMillis(),
                                            String.format(
                                                    "%d repositories were processed (query completed)",
                                                    witness.getCount())));
                        }
                    }
                    listener.getLogger()
                            .println(GitHubConsoleNote.create(
                                    System.currentTimeMillis(),
                                    String.format("%d repositories were processed", witness.getCount())));
                    return;
                }

                throw new AbortException(
                        repoOwner + " does not correspond to a known GitHub User Account or Organization");
            }
        } finally {
            Connector.release(github);
        }
    }

    private boolean isRepositoryVisibleToTeam(GHOrganization org, GHRepository repo, String teamSlug)
            throws IOException {
        final Iterable<GHRepository> repositories =
                org.getTeamBySlug(teamSlug).listRepositories().withPageSize(100);
        for (GHRepository item : repositories) {
            if (repo.getFullName().equals(item.getFullName())) {
                return true;
            }
        }
        return false;
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public List<Action> retrieveActions(
            @NonNull SCMNavigatorOwner owner, @CheckForNull SCMNavigatorEvent event, @NonNull TaskListener listener)
            throws IOException, InterruptedException {
        // TODO when we have support for trusted events, use the details from event if event was from
        // trusted source
        listener.getLogger().printf("Looking up details of %s...%n", getRepoOwner());
        List<Action> result = new ArrayList<>();
        String apiUri = Util.fixEmptyAndTrim(getApiUri());
        StandardCredentials credentials =
                Connector.lookupScanCredentials((Item) owner, getApiUri(), credentialsId, repoOwner);
        GitHub hub = Connector.connect(getApiUri(), credentials);
        boolean privateMode = determinePrivateMode(apiUri);
        try {
            Connector.configureLocalRateLimitChecker(listener, hub);
            GHUser u = hub.getUser(getRepoOwner());
            String objectUrl = u.getHtmlUrl() == null ? null : u.getHtmlUrl().toExternalForm();
            result.add(new ObjectMetadataAction(Util.fixEmpty(u.getName()), null, objectUrl));
            if (privateMode) {
                result.add(new GitHubOrgMetadataAction((String) null));
            } else {
                result.add(new GitHubOrgMetadataAction(u));
            }
            result.add(new GitHubLink("icon-github-logo", u.getHtmlUrl()));
            if (objectUrl == null) {
                listener.getLogger().println("Organization URL: unspecified");
            } else {
                listener.getLogger()
                        .printf(
                                "Organization URL: %s%n",
                                HyperlinkNote.encodeTo(objectUrl, StringUtils.defaultIfBlank(u.getName(), objectUrl)));
            }
            return result;
        } finally {
            Connector.release(hub);
        }
    }

    private static boolean determinePrivateMode(String apiUri) {
        if (apiUri == null || apiUri.equals(GitHubServerConfig.GITHUB_URL)) {
            return false;
        }
        try {
            GitHub.connectToEnterpriseAnonymously(apiUri).checkApiUrlValidity();
        } catch (MalformedURLException e) {
            // URL is bogus so there is never going to be an avatar - or anything else come to think of it
            return true;
        } catch (IOException e) {
            if (e.getMessage().contains("private mode enabled")) {
                return true;
            }
        }
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public void afterSave(@NonNull SCMNavigatorOwner owner) {
        GitHubWebHook.get().registerHookFor(owner);
        try {
            // FIXME MINOR HACK ALERT
            StandardCredentials credentials =
                    Connector.lookupScanCredentials((Item) owner, getApiUri(), credentialsId, repoOwner);
            GitHub hub = Connector.connect(getApiUri(), credentials);
            try {
                GitHubOrgWebHook.register(hub, repoOwner);
            } finally {
                Connector.release(hub);
            }
        } catch (IOException e) {
            DescriptorImpl.LOGGER.log(Level.WARNING, e.getMessage(), e);
        }
    }

    @Symbol("github")
    @Extension
    public static class DescriptorImpl extends SCMNavigatorDescriptor implements IconSpec {

        private static final Logger LOGGER = Logger.getLogger(DescriptorImpl.class.getName());

        @Deprecated
        @Restricted(DoNotUse.class)
        @RestrictedSince("2.2.0")
        public static final String defaultIncludes = "*";

        @Deprecated
        @Restricted(DoNotUse.class)
        @RestrictedSince("2.2.0")
        public static final String defaultExcludes = "";

        public static final String SAME = GitHubSCMSource.DescriptorImpl.SAME;

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
        public static final boolean defaultBuildForkPRMerge = false;

        @Deprecated
        @Restricted(DoNotUse.class)
        @RestrictedSince("2.2.0")
        public static final boolean defaultBuildForkPRHead = false;

        @Inject
        private GitHubSCMSource.DescriptorImpl delegate;

        /** {@inheritDoc} */
        @Override
        public String getPronoun() {
            return Messages.GitHubSCMNavigator_Pronoun();
        }

        /** {@inheritDoc} */
        @Override
        public String getDisplayName() {
            return Messages.GitHubSCMNavigator_DisplayName();
        }

        /** {@inheritDoc} */
        @Override
        public String getDescription() {
            return Messages.GitHubSCMNavigator_Description();
        }

        /** {@inheritDoc} */
        @Override
        public String getIconFilePathPattern() {
            return "plugin/github-branch-source/images/github-scmnavigator.svg";
        }

        /** {@inheritDoc} */
        @Override
        public String getIconClassName() {
            return "icon-github-scm-navigator";
        }

        /** {@inheritDoc} */
        @SuppressWarnings("unchecked")
        @Override
        public SCMNavigator newInstance(String name) {
            GitHubSCMNavigator navigator = new GitHubSCMNavigator(name);
            navigator.setTraits(getTraitsDefaults());
            navigator.setApiUri(GitHubServerConfig.GITHUB_URL);
            return navigator;
        }

        /** {@inheritDoc} */
        @NonNull
        @Override
        protected SCMSourceCategory[] createCategories() {
            return new SCMSourceCategory[] {
                new UncategorizedSCMSourceCategory(Messages._GitHubSCMNavigator_UncategorizedCategory())
                // TODO add support for forks
            };
        }

        /**
         * Validates the selected credentials.
         *
         * @param context the context.
         * @param apiUri the end-point.
         * @param credentialsId the credentials.
         * @return validation results.
         * @since 2.2.0
         */
        @RequirePOST
        @Restricted(NoExternalUse.class) // stapler
        public FormValidation doCheckCredentialsId(
                @CheckForNull @AncestorInPath Item context,
                @QueryParameter String apiUri,
                @QueryParameter String credentialsId,
                @QueryParameter String repoOwner) {
            return Connector.checkScanCredentials(context, apiUri, credentialsId, repoOwner);
        }

        /**
         * Populates the drop-down list of credentials.
         *
         * @param context the context.
         * @param apiUri the end-point.
         * @param credentialsId the existing selection;
         * @return the drop-down list.
         * @since 2.2.0
         */
        @Restricted(NoExternalUse.class) // stapler
        public ListBoxModel doFillCredentialsIdItems(
                @CheckForNull @AncestorInPath Item context,
                @QueryParameter String apiUri,
                @QueryParameter String credentialsId) {
            if (context == null
                    ? !Jenkins.get().hasPermission(Jenkins.ADMINISTER)
                    : !context.hasPermission(Item.EXTENDED_READ)) {
                return new StandardListBoxModel().includeCurrentValue(credentialsId);
            }
            return Connector.listScanCredentials(context, apiUri);
        }

        /**
         * Returns the available GitHub endpoint items.
         *
         * @return the available GitHub endpoint items.
         */
        @Restricted(NoExternalUse.class) // stapler
        @SuppressWarnings("unused") // stapler
        public ListBoxModel doFillApiUriItems() {
            return getPossibleApiUriItems();
        }

        static ListBoxModel getPossibleApiUriItems() {
            ListBoxModel result = new ListBoxModel();
            result.add("GitHub", "");
            for (Endpoint e : GitHubConfiguration.get().getEndpoints()) {
                result.add(
                        e.getName() == null ? e.getApiUri() : e.getName() + " (" + e.getApiUri() + ")", e.getApiUri());
            }
            return result;
        }

        /**
         * Returns {@code true} if there is more than one GitHub endpoint configured, and consequently
         * the UI should provide the ability to select the endpoint.
         *
         * @return {@code true} if there is more than one GitHub endpoint configured.
         */
        @SuppressWarnings("unused") // jelly
        public boolean isApiUriSelectable() {
            return !GitHubConfiguration.get().getEndpoints().isEmpty();
        }

        /**
         * Returns the {@link SCMTraitDescriptor} instances grouped into categories.
         *
         * @return the categorized list of {@link SCMTraitDescriptor} instances.
         * @since 2.2.0
         */
        @SuppressWarnings("unused") // jelly
        public List<NamedArrayList<? extends SCMTraitDescriptor<?>>> getTraitsDescriptorLists() {
            GitHubSCMSource.DescriptorImpl sourceDescriptor =
                    Jenkins.get().getDescriptorByType(GitHubSCMSource.DescriptorImpl.class);
            List<SCMTraitDescriptor<?>> all = new ArrayList<>();
            all.addAll(SCMNavigatorTrait._for(this, GitHubSCMNavigatorContext.class, GitHubSCMSourceBuilder.class));
            all.addAll(SCMSourceTrait._for(sourceDescriptor, GitHubSCMSourceContext.class, null));
            all.addAll(SCMSourceTrait._for(sourceDescriptor, null, GitHubSCMBuilder.class));
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
                    "Repositories",
                    new NamedArrayList.Predicate<SCMTraitDescriptor<?>>() {
                        @Override
                        public boolean test(SCMTraitDescriptor<?> scmTraitDescriptor) {
                            return scmTraitDescriptor instanceof SCMNavigatorTraitDescriptor;
                        }
                    },
                    true,
                    result);
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

        @SuppressWarnings("unused") // jelly
        @NonNull
        public List<SCMTrait<? extends SCMTrait<?>>> getTraitsDefaults() {
            return new ArrayList<>(delegate.getTraitsDefaults());
        }

        static {
            IconSet.icons.addIcon(new Icon(
                    "icon-github-scm-navigator icon-sm",
                    "plugin/github-branch-source/images/svgs/github-scmnavigator.svg",
                    Icon.ICON_SMALL_STYLE));
            IconSet.icons.addIcon(new Icon(
                    "icon-github-scm-navigator icon-md",
                    "plugin/github-branch-source/images/svgs/github-scmnavigator.svg",
                    Icon.ICON_MEDIUM_STYLE));
            IconSet.icons.addIcon(new Icon(
                    "icon-github-scm-navigator icon-lg",
                    "plugin/github-branch-source/images/svgs/github-scmnavigator.svg",
                    Icon.ICON_LARGE_STYLE));
            IconSet.icons.addIcon(new Icon(
                    "icon-github-scm-navigator icon-xlg",
                    "plugin/github-branch-source/images/svgs/github-scmnavigator.svg",
                    Icon.ICON_XLARGE_STYLE));

            IconSet.icons.addIcon(new Icon(
                    "icon-github-logo icon-sm",
                    "plugin/github-branch-source/images/svgs/sprite-github.svg#github-logo",
                    Icon.ICON_SMALL_STYLE,
                    IconFormat.EXTERNAL_SVG_SPRITE));
            IconSet.icons.addIcon(new Icon(
                    "icon-github-logo icon-md",
                    "plugin/github-branch-source/images/svgs/sprite-github.svg#github-logo",
                    Icon.ICON_MEDIUM_STYLE,
                    IconFormat.EXTERNAL_SVG_SPRITE));
            IconSet.icons.addIcon(new Icon(
                    "icon-github-logo icon-lg",
                    "plugin/github-branch-source/images/svgs/sprite-github.svg#github-logo",
                    Icon.ICON_LARGE_STYLE,
                    IconFormat.EXTERNAL_SVG_SPRITE));
            IconSet.icons.addIcon(new Icon(
                    "icon-github-logo icon-xlg",
                    "plugin/github-branch-source/images/svgs/sprite-github.svg#github-logo",
                    Icon.ICON_XLARGE_STYLE,
                    IconFormat.EXTERNAL_SVG_SPRITE));

            IconSet.icons.addIcon(new Icon(
                    "icon-github-repo icon-sm",
                    "plugin/github-branch-source/images/svgs/sprite-github.svg#github-repo",
                    Icon.ICON_SMALL_STYLE,
                    IconFormat.EXTERNAL_SVG_SPRITE));
            IconSet.icons.addIcon(new Icon(
                    "icon-github-repo icon-md",
                    "plugin/github-branch-source/images/svgs/sprite-github.svg#github-repo",
                    Icon.ICON_MEDIUM_STYLE,
                    IconFormat.EXTERNAL_SVG_SPRITE));
            IconSet.icons.addIcon(new Icon(
                    "icon-github-repo icon-lg",
                    "plugin/github-branch-source/images/svgs/sprite-github.svg#github-repo",
                    Icon.ICON_LARGE_STYLE,
                    IconFormat.EXTERNAL_SVG_SPRITE));
            IconSet.icons.addIcon(new Icon(
                    "icon-github-repo icon-xlg",
                    "plugin/github-branch-source/images/svgs/sprite-github.svg#github-repo",
                    Icon.ICON_XLARGE_STYLE,
                    IconFormat.EXTERNAL_SVG_SPRITE));

            IconSet.icons.addIcon(new Icon(
                    "icon-github-branch icon-sm",
                    "plugin/github-branch-source/images/svgs/sprite-github.svg#git-branch",
                    Icon.ICON_SMALL_STYLE,
                    IconFormat.EXTERNAL_SVG_SPRITE));
            IconSet.icons.addIcon(new Icon(
                    "icon-github-branch icon-md",
                    "plugin/github-branch-source/images/svgs/sprite-github.svg#git-branch",
                    Icon.ICON_MEDIUM_STYLE,
                    IconFormat.EXTERNAL_SVG_SPRITE));
            IconSet.icons.addIcon(new Icon(
                    "icon-github-branch icon-lg",
                    "plugin/github-branch-source/images/svgs/sprite-github.svg#git-branch",
                    Icon.ICON_LARGE_STYLE,
                    IconFormat.EXTERNAL_SVG_SPRITE));
            IconSet.icons.addIcon(new Icon(
                    "icon-github-branch icon-xlg",
                    "plugin/github-branch-source/images/svgs/sprite-github.svg#git-branch",
                    Icon.ICON_XLARGE_STYLE,
                    IconFormat.EXTERNAL_SVG_SPRITE));
        }
    }

    /** A {@link SCMNavigatorRequest.Witness} that counts how many sources have been observed. */
    private static class WitnessImpl implements SCMNavigatorRequest.Witness {
        /** The count of repositories matches. */
        @GuardedBy("this")
        private int count;
        /** The listener to log to. */
        @NonNull
        private final TaskListener listener;

        /**
         * Constructor.
         *
         * @param listener the listener to log to.
         */
        public WitnessImpl(@NonNull TaskListener listener) {
            this.listener = listener;
        }

        /** {@inheritDoc} */
        @Override
        public void record(@NonNull String name, boolean isMatch) {
            if (isMatch) {
                listener.getLogger().format("Proposing %s%n", name);
                synchronized (this) {
                    count++;
                }
            } else {
                listener.getLogger().format("Ignoring %s%n", name);
            }
        }

        /**
         * Returns the count of repositories matches.
         *
         * @return the count of repositories matches.
         */
        public synchronized int getCount() {
            return count;
        }
    }

    /** Our {@link SCMNavigatorRequest.SourceLambda}. */
    private class SourceFactory implements SCMNavigatorRequest.SourceLambda {
        /** The request. */
        private final GitHubSCMNavigatorRequest request;

        /**
         * Constructor.
         *
         * @param request the request to decorate {@link SCMSource} instances with.
         */
        public SourceFactory(GitHubSCMNavigatorRequest request) {
            this.request = request;
        }

        /** {@inheritDoc} */
        @NonNull
        @Override
        public SCMSource create(@NonNull String name) {
            return new GitHubSCMSourceBuilder(getId() + "::" + name, apiUri, credentialsId, repoOwner, name)
                    .withRequest(request)
                    .build();
        }
    }
}
