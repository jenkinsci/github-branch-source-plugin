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

import com.cloudbees.jenkins.GitHubWebHook;
import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.SCMNavigatorDescriptor;
import jenkins.scm.api.SCMNavigatorEvent;
import jenkins.scm.api.SCMNavigatorOwner;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCategory;
import jenkins.scm.api.SCMSourceObserver;
import jenkins.scm.api.metadata.ObjectMetadataAction;
import jenkins.scm.api.trait.SCMNavigatorRequest;
import jenkins.scm.api.trait.SCMNavigatorTrait;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMTrait;
import jenkins.scm.api.trait.SCMTraitDescriptor;
import jenkins.scm.impl.UncategorizedSCMSourceCategory;
import jenkins.scm.impl.trait.RegexSCMSourceFilterTrait;
import jenkins.scm.impl.trait.WildcardSCMHeadFilterTrait;
import org.apache.commons.lang.StringUtils;
import org.jenkins.ui.icon.Icon;
import org.jenkins.ui.icon.IconSet;
import org.jenkins.ui.icon.IconSpec;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.github.GHMyself;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.HttpException;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

public class GitHubSCMNavigator extends SCMNavigator {

    @NonNull
    private final String repoOwner;
    @CheckForNull
    private final String scanCredentialsId;
    @CheckForNull
    private final String apiUri;
    @NonNull
    private List<SCMTrait<? extends SCMTrait<?>>> traits;

    @Deprecated
    private transient String checkoutCredentialsId;
    @Deprecated
    private transient String pattern;
    @Deprecated
    private String includes;
    @Deprecated
    private String excludes;
    @Deprecated
    private transient Boolean buildOriginBranch;
    @Deprecated
    private transient Boolean buildOriginBranchWithPR;
    @Deprecated
    private transient Boolean buildOriginPRMerge;
    @Deprecated
    private transient Boolean buildOriginPRHead;
    @Deprecated
    private transient Boolean buildForkPRMerge;
    @Deprecated
    private transient Boolean buildForkPRHead;

    @DataBoundConstructor
    public GitHubSCMNavigator(String apiUri, String repoOwner, String scanCredentialsId) {
        this.apiUri = Util.fixEmptyAndTrim(apiUri);
        this.repoOwner = repoOwner;
        this.scanCredentialsId = Util.fixEmpty(scanCredentialsId);
        this.traits = new ArrayList<>();
    }

    @Deprecated
    @Restricted(DoNotUse.class)
    @RestrictedSince("2.2.0")
    public GitHubSCMNavigator(String apiUri, String repoOwner, String scanCredentialsId, String checkoutCredentialsId) {
        this.repoOwner = repoOwner;
        this.scanCredentialsId = Util.fixEmpty(scanCredentialsId);
        this.apiUri = Util.fixEmpty(apiUri);
        this.traits = new ArrayList<>();
        if (!GitHubSCMSource.DescriptorImpl.SAME.equals(checkoutCredentialsId)) {
            traits.add(new SSHCheckoutTrait(checkoutCredentialsId));
        }
    }

    public String getRepoOwner() {
        return repoOwner;
    }

    @CheckForNull
    public String getApiUri() {
        return apiUri;
    }

    @CheckForNull
    public String getScanCredentialsId() {
        return scanCredentialsId;
    }

    @NonNull
    public List<SCMTrait<? extends SCMTrait<?>>> getTraits() {
        return traits;
    }

    @DataBoundSetter
    public void setTraits(@edu.umd.cs.findbugs.annotations.CheckForNull List<SCMTrait<? extends SCMTrait<?>>> traits) {
        this.traits = traits != null ? new ArrayList<>(traits) : new ArrayList<SCMTrait<? extends SCMTrait<?>>>();
    }

    /** Use defaults for old settings. */
    @SuppressWarnings("ConstantConditions")
    @SuppressFBWarnings(value="RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE", justification="Only non-null after we set them here!")
    private Object readResolve() {
        if (traits == null) {
            boolean buildOriginBranch = this.buildOriginBranch == null
                    ? DescriptorImpl.defaultBuildOriginBranch : this.buildOriginBranch;
            boolean buildOriginBranchWithPR = this.buildOriginBranchWithPR == null
                    ? DescriptorImpl.defaultBuildOriginBranchWithPR : this.buildOriginBranchWithPR;
            boolean buildOriginPRMerge = this.buildOriginPRMerge == null
                    ? DescriptorImpl.defaultBuildOriginPRMerge : this.buildOriginPRMerge;
            boolean buildOriginPRHead = this.buildOriginPRHead == null
                    ? DescriptorImpl.defaultBuildOriginPRHead : this.buildOriginPRHead;
            boolean buildForkPRMerge = this.buildForkPRMerge
                    ? DescriptorImpl.defaultBuildForkPRMerge : this.buildForkPRMerge;
            boolean buildForkPRHead = this.buildForkPRHead
                    ? DescriptorImpl.defaultBuildForkPRHead : this.buildForkPRHead;
            List<SCMTrait<? extends SCMTrait<?>>> traits = new ArrayList<>();
            if (buildOriginBranch || buildOriginBranchWithPR) {
                traits.add(new BranchDiscoveryTrait(buildOriginBranch, buildOriginBranchWithPR));
            }
            if (buildOriginPRMerge || buildOriginPRHead) {
                traits.add(new OriginPullRequestDiscoveryTrait(buildOriginPRMerge, buildOriginPRHead));
            }
            if (buildForkPRMerge || buildForkPRHead) {
                traits.add(new ForkPullRequestDiscoveryTrait(buildForkPRMerge, buildForkPRHead));
            }
            if (!GitHubSCMSource.DescriptorImpl.SAME.equals(checkoutCredentialsId)) {
                traits.add(new SSHCheckoutTrait(checkoutCredentialsId));
            }
            if (!GitHubSCMSource.DescriptorImpl.defaultIncludes.equals(includes)
                    || !GitHubSCMSource.DescriptorImpl.defaultExcludes.equals(excludes)) {
                traits.add(new WildcardSCMHeadFilterTrait(includes, excludes));
            }
            if (pattern != null && !".*".equals(pattern)) {
                traits.add(new RegexSCMSourceFilterTrait(pattern));
            }
            this.traits = traits;
        }
        return this;
    }

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
        return GitHubSCMSource.DescriptorImpl.defaultIncludes;
    }

    @Deprecated
    @Restricted(DoNotUse.class)
    @RestrictedSince("2.2.0")
    @DataBoundSetter
    public void setIncludes(@NonNull String includes) {
        for (int i = 0; i < traits.size(); i++) {
            SCMTrait<?> trait = traits.get(i);
            if (trait instanceof WildcardSCMHeadFilterTrait) {
                traits.set(i,
                        new WildcardSCMHeadFilterTrait(includes, ((WildcardSCMHeadFilterTrait) trait).getExcludes()));
                return;
            }
        }
        traits.add(new WildcardSCMHeadFilterTrait(includes, GitHubSCMSource.DescriptorImpl.defaultExcludes));
    }

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
        return GitHubSCMSource.DescriptorImpl.defaultExcludes;
    }

    @Deprecated
    @Restricted(DoNotUse.class)
    @RestrictedSince("2.2.0")
    @DataBoundSetter
    public void setExcludes(@NonNull String excludes) {
        for (int i = 0; i < traits.size(); i++) {
            SCMTrait<?> trait = traits.get(i);
            if (trait instanceof WildcardSCMHeadFilterTrait) {
                traits.set(i,
                        new WildcardSCMHeadFilterTrait(((WildcardSCMHeadFilterTrait) trait).getIncludes(), excludes));
                return;
            }
        }
        traits.add(new WildcardSCMHeadFilterTrait(GitHubSCMSource.DescriptorImpl.defaultIncludes, excludes));
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
                traits.set(i, new BranchDiscoveryTrait(
                        buildOriginBranch, ((BranchDiscoveryTrait) trait).isBuildBranchesWithPR()
                ));
                return;
            }
        }
        traits.add(new BranchDiscoveryTrait(buildOriginBranch, false));
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
                traits.set(i, new BranchDiscoveryTrait(
                        ((BranchDiscoveryTrait) trait).isBuildBranch(), buildOriginBranchWithPR
                ));
                return;
            }
        }
        traits.add(new BranchDiscoveryTrait(false, buildOriginBranchWithPR));
    }

    @Deprecated
    @Restricted(DoNotUse.class)
    @RestrictedSince("2.2.0")
    public boolean getBuildOriginPRMerge() {
        for (SCMTrait<?> trait : traits) {
            if (trait instanceof OriginPullRequestDiscoveryTrait) {
                return ((OriginPullRequestDiscoveryTrait) trait).isPRMerge();
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
                traits.set(i, new OriginPullRequestDiscoveryTrait(
                        buildOriginPRMerge, ((OriginPullRequestDiscoveryTrait)trait).isPRHead()
                ));
                return;
            }
        }
        traits.add(new OriginPullRequestDiscoveryTrait(buildOriginPRMerge, false));
    }

    @Deprecated
    @Restricted(DoNotUse.class)
    @RestrictedSince("2.2.0")
    public boolean getBuildOriginPRHead() {
        for (SCMTrait<?> trait : traits) {
            if (trait instanceof OriginPullRequestDiscoveryTrait) {
                return ((OriginPullRequestDiscoveryTrait) trait).isPRHead();
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
                traits.set(i, new OriginPullRequestDiscoveryTrait(
                        ((OriginPullRequestDiscoveryTrait) trait).isPRMerge(), buildOriginPRHead
                ));
                return;
            }
        }
        traits.add(new OriginPullRequestDiscoveryTrait(false, buildOriginPRHead));
    }

    @Deprecated
    @Restricted(DoNotUse.class)
    @RestrictedSince("2.2.0")
    public boolean getBuildForkPRMerge() {
        for (SCMTrait<?> trait : traits) {
            if (trait instanceof ForkPullRequestDiscoveryTrait) {
                return ((ForkPullRequestDiscoveryTrait) trait).isPRMerge();
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
                traits.set(i, new ForkPullRequestDiscoveryTrait(
                        buildForkPRMerge, ((ForkPullRequestDiscoveryTrait) trait).isPRHead()
                ));
                return;
            }
        }
        traits.add(new ForkPullRequestDiscoveryTrait(buildForkPRMerge, false));
    }

    @Deprecated
    @Restricted(DoNotUse.class)
    @RestrictedSince("2.2.0")
    public boolean getBuildForkPRHead() {
        for (SCMTrait<?> trait : traits) {
            if (trait instanceof ForkPullRequestDiscoveryTrait) {
                return ((ForkPullRequestDiscoveryTrait) trait).isPRHead();
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
                traits.set(i, new ForkPullRequestDiscoveryTrait(
                        ((ForkPullRequestDiscoveryTrait) trait).isPRMerge(), buildForkPRHead
                ));
                return;
            }
        }
        traits.add(new ForkPullRequestDiscoveryTrait(false, buildForkPRHead));
    }

    @CheckForNull
    @Deprecated
    @Restricted(DoNotUse.class)
    @RestrictedSince("2.2.0")
    public String getCheckoutCredentialsId() {
        for (SCMTrait<?> trait : traits) {
            if (trait instanceof SSHCheckoutTrait) {
                return ((SSHCheckoutTrait) trait).getCredentialsId();
            }
        }
        return GitHubSCMSource.DescriptorImpl.SAME;
    }

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

    @Deprecated
    @Restricted(DoNotUse.class)
    @RestrictedSince("2.2.0")
    @DataBoundSetter
    public void setPattern(String pattern) {
        for (int i = 0; i < traits.size(); i++) {
            SCMTrait<?> trait = traits.get(i);
            if (trait instanceof RegexSCMSourceFilterTrait) {
                traits.set(i, new RegexSCMSourceFilterTrait(pattern));
                return;
            }
        }
        traits.add(new RegexSCMSourceFilterTrait(pattern));
    }

    @NonNull
    @Override
    protected String id() {
        return StringUtils.defaultIfBlank(apiUri, GitHubSCMSource.GITHUB_URL) + "::" + repoOwner;
    }

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

        StandardCredentials credentials = Connector.lookupScanCredentials((Item)observer.getContext(), apiUri, scanCredentialsId);

        // Github client and validation
        GitHub github = Connector.connect(apiUri, credentials);
        try {
            Connector.checkConnectionValidity(apiUri, listener, credentials, github);
            Connector.checkApiRateLimit(listener, github);

            // Input data validation
            if (credentials != null && !github.isCredentialValid()) {
                String message = String.format("Invalid scan credentials %s to connect to %s, skipping",
                        CredentialsNameProvider.name(credentials),
                        apiUri == null ? GitHubSCMSource.GITHUB_URL : apiUri);
                throw new AbortException(message);
            }

            GitHubSCMNavigatorRequest request = new GitHubSCMNavigatorContext()
                    .withTraits() // TODO
                    .newRequest(this, observer);
            try {
                SourceFactoryImpl sourceFactory = new SourceFactoryImpl(request);
                WitnessImpl witness = new WitnessImpl(listener);

                if (!github.isAnonymous()) {
                    GHMyself myself = null;
                    try {
                        // Requires an authenticated access
                        myself = github.getMyself();
                    } catch (RateLimitExceededException rle) {
                        throw new AbortException(rle.getMessage());
                    }
                    if (myself != null && repoOwner.equalsIgnoreCase(myself.getLogin())) {
                            listener.getLogger()
                                    .println(GitHubConsoleNote.create(System.currentTimeMillis(), String.format(
                                            "Looking up repositories of myself %s", repoOwner
                                    )));
                        for (GHRepository repo : myself.listRepositories(100)) {
                            Connector.checkApiRateLimit(listener, github);
                            if (!repo.getOwnerName().equals(repoOwner)) {
                                continue; // ignore repos in other orgs when using GHMyself
                            }
                            if (request.process(repo.getName(), sourceFactory, null, witness)) {
                                listener.getLogger()
                                        .println(GitHubConsoleNote.create(System.currentTimeMillis(), String.format(
                                                "%d repositories were processed (query completed)", witness.getCount()
                                        )));
                            }
                        }
                        listener.getLogger().println(GitHubConsoleNote.create(System.currentTimeMillis(), String.format(
                                "%d repositories were processed", witness.getCount()
                        )));
                        return;
                    }
                }

                GHOrganization org = null;
                try {
                    org = github.getOrganization(repoOwner);
                } catch (RateLimitExceededException rle) {
                    throw new AbortException(rle.getMessage());
                } catch (FileNotFoundException fnf) {
                    // may be an user... ok to ignore
                }
                if (org != null && repoOwner.equalsIgnoreCase(org.getLogin())) {
                    listener.getLogger().println(GitHubConsoleNote.create(System.currentTimeMillis(), String.format(
                            "Looking up repositories of organization %s", repoOwner
                    )));
                    for (GHRepository repo : org.listRepositories(100)) {
                        Connector.checkApiRateLimit(listener, github);
                        if (request.process(repo.getName(), sourceFactory, null, witness)) {
                            listener.getLogger()
                                    .println(GitHubConsoleNote.create(System.currentTimeMillis(), String.format(
                                            "%d repositories were processed (query completed)", witness.getCount()
                                    )));
                        }
                    }
                    listener.getLogger().println(GitHubConsoleNote.create(System.currentTimeMillis(), String.format(
                            "%d repositories were processed", witness.getCount()
                    )));
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
                    Connector.checkApiRateLimit(listener, github);
                    for (GHRepository repo : user.listRepositories(100)) {
                        Connector.checkApiRateLimit(listener, github);
                        if (request.process(repo.getName(), sourceFactory, null, witness)) {
                            listener.getLogger()
                                    .println(GitHubConsoleNote.create(System.currentTimeMillis(), String.format(
                                            "%d repositories were processed (query completed)", witness.getCount()
                                    )));
                        }
                    }
                    listener.getLogger().println(GitHubConsoleNote.create(System.currentTimeMillis(), String.format(
                            "%d repositories were processed", witness.getCount()
                    )));
                    return;
                }

                throw new AbortException(
                        repoOwner + " does not correspond to a known GitHub User Account or Organization");
            } finally {
                request.close();
            }
        } finally {
            Connector.release(github);
        }
    }

    @Override
    public void visitSource(String sourceName, SCMSourceObserver observer)
            throws IOException, InterruptedException {
        TaskListener listener = observer.getListener();

        // Input data validation
        if (repoOwner.isEmpty()) {
            throw new AbortException("Must specify user or organization");
        }

        StandardCredentials credentials =
                Connector.lookupScanCredentials((Item)observer.getContext(), apiUri, scanCredentialsId);

        // Github client and validation
        GitHub github = Connector.connect(apiUri, credentials);
        try {
            try {
                github.checkApiUrlValidity();
            } catch (HttpException e) {
                String message = String.format("It seems %s is unreachable",
                        apiUri == null ? GitHubSCMSource.GITHUB_URL : apiUri);
                throw new AbortException(message);
            }

            // Input data validation
            if (credentials != null && !github.isCredentialValid()) {
                String message = String.format("Invalid scan credentials %s to connect to %s, skipping",
                        CredentialsNameProvider.name(credentials),
                        apiUri == null ? GitHubSCMSource.GITHUB_URL : apiUri);
                throw new AbortException(message);
            }

            GitHubSCMNavigatorRequest request = new GitHubSCMNavigatorContext()
                    .withTraits() // TODO
                    .newRequest(this, observer);
            try {
                SourceFactoryImpl sourceFactory = new SourceFactoryImpl(request);
                WitnessImpl witness = new WitnessImpl(listener);

                if (!github.isAnonymous()) {
                    listener.getLogger()
                            .format("Connecting to %s using %s%n", apiUri == null ? GitHubSCMSource.GITHUB_URL : apiUri,
                                    CredentialsNameProvider.name(credentials));
                    GHMyself myself = null;
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
                            if (request.process(repo.getName(), sourceFactory, null, witness)) {
                                listener.getLogger()
                                        .println(GitHubConsoleNote.create(System.currentTimeMillis(), String.format(
                                                "%d repositories were processed (query completed)", witness.getCount()
                                        )));
                            }
                        }
                        listener.getLogger().println(GitHubConsoleNote.create(System.currentTimeMillis(), String.format(
                                "%d repositories were processed", witness.getCount()
                        )));
                        return;
                    }
                } else {
                    listener.getLogger().format("Connecting to %s with no credentials, anonymous access%n",
                            apiUri == null ? GitHubSCMSource.GITHUB_URL : apiUri);
                }

                GHOrganization org = null;
                try {
                    org = github.getOrganization(repoOwner);
                } catch (RateLimitExceededException rle) {
                    throw new AbortException(rle.getMessage());
                } catch (FileNotFoundException fnf) {
                    // may be an user... ok to ignore
                }
                if (org != null && repoOwner.equalsIgnoreCase(org.getLogin())) {
                    listener.getLogger()
                            .format("Looking up %s repository of organization %s%n%n", sourceName, repoOwner);
                    GHRepository repo = org.getRepository(sourceName);
                    if (repo != null) {
                        if (request.process(repo.getName(), sourceFactory, null, witness)) {
                            listener.getLogger()
                                    .println(GitHubConsoleNote.create(System.currentTimeMillis(), String.format(
                                            "%d repositories were processed (query completed)", witness.getCount()
                                    )));
                        }
                    }
                    listener.getLogger().println(GitHubConsoleNote.create(System.currentTimeMillis(), String.format(
                            "%d repositories were processed", witness.getCount()
                    )));
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
                        if (request.process(repo.getName(), sourceFactory, null, witness)) {
                            listener.getLogger()
                                    .println(GitHubConsoleNote.create(System.currentTimeMillis(), String.format(
                                            "%d repositories were processed (query completed)", witness.getCount()
                                    )));
                        }
                    }
                    listener.getLogger().println(GitHubConsoleNote.create(System.currentTimeMillis(), String.format(
                            "%d repositories were processed", witness.getCount()
                    )));
                    return;
                }

                throw new AbortException(
                        repoOwner + " does not correspond to a known GitHub User Account or Organization");
            } finally {
                request.close();
            }
        } finally {
            Connector.release(github);
        }
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public List<Action> retrieveActions(@NonNull SCMNavigatorOwner owner,
                                        @CheckForNull SCMNavigatorEvent event,
                                        @NonNull TaskListener listener) throws IOException, InterruptedException {
        // TODO when we have support for trusted events, use the details from event if event was from trusted source
        listener.getLogger().printf("Looking up details of %s...%n", getRepoOwner());
        List<Action> result = new ArrayList<>();
        StandardCredentials credentials = Connector.lookupScanCredentials((Item)owner, getApiUri(), getScanCredentialsId());
        GitHub hub = Connector.connect(getApiUri(), credentials);
        try {
            Connector.checkApiRateLimit(listener, hub);
            GHUser u = hub.getUser(getRepoOwner());
            String objectUrl = u.getHtmlUrl() == null ? null : u.getHtmlUrl().toExternalForm();
            result.add(new ObjectMetadataAction(
                    Util.fixEmpty(u.getName()),
                    null,
                    objectUrl)
            );
            result.add(new GitHubOrgMetadataAction(u));
            result.add(new GitHubLink("icon-github-logo", u.getHtmlUrl()));
            if (objectUrl == null) {
                listener.getLogger().println("Organization URL: unspecified");
            } else {
                listener.getLogger().printf("Organization URL: %s%n",
                        HyperlinkNote.encodeTo(objectUrl, StringUtils.defaultIfBlank(u.getName(), objectUrl)));
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
    public void afterSave(@NonNull SCMNavigatorOwner owner) {
        GitHubWebHook.get().registerHookFor(owner);
        try {
            // FIXME MINOR HACK ALERT
            StandardCredentials credentials =
                    Connector.lookupScanCredentials((Item)owner, getApiUri(), getScanCredentialsId());
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

        public static final String defaultIncludes = GitHubSCMSource.DescriptorImpl.defaultIncludes;
        public static final String defaultExcludes = GitHubSCMSource.DescriptorImpl.defaultExcludes;
        public static final String SAME = GitHubSCMSource.DescriptorImpl.SAME;
        public static final boolean defaultBuildOriginBranch = GitHubSCMSource.DescriptorImpl.defaultBuildOriginBranch;
        public static final boolean defaultBuildOriginBranchWithPR = GitHubSCMSource.DescriptorImpl.defaultBuildOriginBranchWithPR;
        public static final boolean defaultBuildOriginPRMerge = GitHubSCMSource.DescriptorImpl.defaultBuildOriginPRMerge;
        public static final boolean defaultBuildOriginPRHead = GitHubSCMSource.DescriptorImpl.defaultBuildOriginPRHead;
        public static final boolean defaultBuildForkPRMerge = GitHubSCMSource.DescriptorImpl.defaultBuildForkPRMerge;
        public static final boolean defaultBuildForkPRHead = GitHubSCMSource.DescriptorImpl.defaultBuildForkPRHead;

        @Inject private GitHubSCMSource.DescriptorImpl delegate;

        /**
         * {@inheritDoc}
         */
        @Override
        public String getPronoun() {
            return Messages.GitHubSCMNavigator_Pronoun();
        }

        @Override
        public String getDisplayName() {
            return Messages.GitHubSCMNavigator_DisplayName();
        }

        @Override
        public String getDescription() {
            return Messages.GitHubSCMNavigator_Description();
        }

        @Override
        public String getIconFilePathPattern() {
            return "plugin/github-branch-source/images/:size/github-scmnavigator.png";
        }

        @Override
        public String getIconClassName() {
            return "icon-github-scm-navigator";
        }

        @Override
        public SCMNavigator newInstance(String name) {
            GitHubSCMNavigator navigator = new GitHubSCMNavigator("", name, "");
            navigator.setTraits(getTraitDefaults());
            return navigator;
        }

        @NonNull
        @Override
        protected SCMSourceCategory[] createCategories() {
            return new SCMSourceCategory[]{
                    new UncategorizedSCMSourceCategory(Messages._GitHubSCMNavigator_UncategorizedCategory())
                    // TODO add support for forks
            };
        }

        @Restricted(NoExternalUse.class)
        public FormValidation doCheckScanCredentialsId(@CheckForNull @AncestorInPath Item context,
                                                       @QueryParameter String apiUri,
                                                       @QueryParameter String scanCredentialsId) {
            return Connector.checkScanCredentials(context, apiUri, scanCredentialsId);
        }

        public ListBoxModel doFillScanCredentialsIdItems(@CheckForNull @AncestorInPath Item context, @QueryParameter String apiUri) {
            return Connector.listScanCredentials(context, apiUri);
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

        public List<SCMTraitDescriptor<?>> getTraitDescriptors() {
            List<SCMTraitDescriptor<?>> descriptors = new ArrayList<SCMTraitDescriptor<?>>();
            descriptors.addAll(SCMNavigatorTrait._for(GitHubSCMNavigatorContext.class, GitHubSCMSourceBuilder.class));
            descriptors.addAll(SCMSourceTrait._for(GitHubSCMSourceContext.class, GitHubSCMSource.GitHubSCMBuilder.class));
            return descriptors;
        }

        public List<SCMTrait<? extends SCMTrait<?>>> getTraitDefaults() {
            List<SCMTrait<? extends SCMTrait<?>>> result = new ArrayList<>();
            result.addAll(delegate.getTraitDefaults());
            return result;
        }

        static {
            IconSet.icons.addIcon(
                    new Icon("icon-github-scm-navigator icon-sm",
                            "plugin/github-branch-source/images/16x16/github-scmnavigator.png",
                            Icon.ICON_SMALL_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-github-scm-navigator icon-md",
                            "plugin/github-branch-source/images/24x24/github-scmnavigator.png",
                            Icon.ICON_MEDIUM_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-github-scm-navigator icon-lg",
                            "plugin/github-branch-source/images/32x32/github-scmnavigator.png",
                            Icon.ICON_LARGE_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-github-scm-navigator icon-xlg",
                            "plugin/github-branch-source/images/48x48/github-scmnavigator.png",
                            Icon.ICON_XLARGE_STYLE));

            IconSet.icons.addIcon(
                    new Icon("icon-github-logo icon-sm",
                            "plugin/github-branch-source/images/16x16/github-logo.png",
                            Icon.ICON_SMALL_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-github-logo icon-md",
                            "plugin/github-branch-source/images/24x24/github-logo.png",
                            Icon.ICON_MEDIUM_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-github-logo icon-lg",
                            "plugin/github-branch-source/images/32x32/github-logo.png",
                            Icon.ICON_LARGE_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-github-logo icon-xlg",
                            "plugin/github-branch-source/images/48x48/github-logo.png",
                            Icon.ICON_XLARGE_STYLE));

            IconSet.icons.addIcon(
                    new Icon("icon-github-repo icon-sm",
                            "plugin/github-branch-source/images/16x16/github-repo.png",
                            Icon.ICON_SMALL_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-github-repo icon-md",
                            "plugin/github-branch-source/images/24x24/github-repo.png",
                            Icon.ICON_MEDIUM_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-github-repo icon-lg",
                            "plugin/github-branch-source/images/32x32/github-repo.png",
                            Icon.ICON_LARGE_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-github-repo icon-xlg",
                            "plugin/github-branch-source/images/48x48/github-repo.png",
                            Icon.ICON_XLARGE_STYLE));

            IconSet.icons.addIcon(
                    new Icon("icon-github-branch icon-sm",
                            "plugin/github-branch-source/images/16x16/github-branch.png",
                            Icon.ICON_SMALL_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-github-branch icon-md",
                            "plugin/github-branch-source/images/24x24/github-branch.png",
                            Icon.ICON_MEDIUM_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-github-branch icon-lg",
                            "plugin/github-branch-source/images/32x32/github-branch.png",
                            Icon.ICON_LARGE_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-github-branch icon-xlg",
                            "plugin/github-branch-source/images/48x48/github-branch.png",
                            Icon.ICON_XLARGE_STYLE));
        }
    }

    private static class WitnessImpl implements SCMNavigatorRequest.Witness {
        private int count;
        private final TaskListener listener;

        public WitnessImpl(TaskListener listener) {
            this.listener = listener;
        }

        @Override
        public void record(@NonNull String name, boolean isMatch) {
            if (isMatch) {
                listener.getLogger().format("Proposing %s%n", name);
                count++;
            } else {
                listener.getLogger().format("Ignoring %s%n", name);
            }
        }

        public int getCount() {
            return count;
        }
    }

    private class SourceFactoryImpl implements SCMNavigatorRequest.SourceLambda {
        private final GitHubSCMNavigatorRequest request;

        public SourceFactoryImpl(GitHubSCMNavigatorRequest request) {
            this.request = request;
        }

        @NonNull
        @Override
        public SCMSource create(@NonNull String name) {
            return new GitHubSCMSourceBuilder(getId() + "::" + name, apiUri, scanCredentialsId, repoOwner, name)
                    .withRequest(request)
                    .build();
        }
    }
}
