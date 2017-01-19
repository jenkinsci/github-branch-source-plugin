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
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.AbortException;
import hudson.Extension;
import hudson.Util;
import hudson.console.HyperlinkNote;
import hudson.model.Action;
import hudson.model.TaskListener;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.SCMNavigatorDescriptor;
import jenkins.scm.api.SCMNavigatorEvent;
import jenkins.scm.api.SCMNavigatorOwner;
import jenkins.scm.api.SCMSourceCategory;
import jenkins.scm.api.SCMSourceObserver;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.api.metadata.ObjectMetadataAction;
import jenkins.scm.impl.UncategorizedSCMSourceCategory;
import org.apache.commons.lang.StringUtils;
import org.jenkins.ui.icon.Icon;
import org.jenkins.ui.icon.IconSet;
import org.jenkins.ui.icon.IconSpec;
import org.jenkinsci.plugins.github.config.GitHubServerConfig;
import org.kohsuke.accmod.Restricted;
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

import static org.jenkinsci.plugins.github_branch_source.GitHubSCMSource.GITHUB_URL;

public class GitHubSCMNavigator extends SCMNavigator {

    private final String repoOwner;
    private final String scanCredentialsId;
    private final String checkoutCredentialsId;
    private final String apiUri;
    private String pattern = ".*";

    @CheckForNull
    private String includes;
    @CheckForNull
    private String excludes;
    /** Whether to build regular origin branches. */
    @Nonnull
    private Boolean buildOriginBranch = DescriptorImpl.defaultBuildOriginBranch;
    /** Whether to build origin branches which happen to also have a PR filed from them (but here we are naming and building as a branch). */
    @Nonnull
    private Boolean buildOriginBranchWithPR = DescriptorImpl.defaultBuildOriginBranchWithPR;
    /** Whether to build PRs filed from the origin, where the build is of the merge with the base branch. */
    @Nonnull
    private Boolean buildOriginPRMerge = DescriptorImpl.defaultBuildOriginPRMerge;
    /** Whether to build PRs filed from the origin, where the build is of the branch head. */
    @Nonnull
    private Boolean buildOriginPRHead = DescriptorImpl.defaultBuildOriginPRHead;
    /** Whether to build PRs filed from a fork, where the build is of the merge with the base branch. */
    @Nonnull
    private Boolean buildForkPRMerge = DescriptorImpl.defaultBuildForkPRMerge;
    /** Whether to build PRs filed from a fork, where the build is of the branch head. */
    @Nonnull
    private Boolean buildForkPRHead = DescriptorImpl.defaultBuildForkPRHead;

    @DataBoundConstructor
    public GitHubSCMNavigator(String apiUri, String repoOwner, String scanCredentialsId, String checkoutCredentialsId) {
        this.repoOwner = repoOwner;
        this.scanCredentialsId = Util.fixEmpty(scanCredentialsId);
        this.checkoutCredentialsId = checkoutCredentialsId;
        this.apiUri = Util.fixEmpty(apiUri);
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

    @Nonnull
    public String getIncludes() {
        return includes != null ? includes : DescriptorImpl.defaultIncludes;
    }

    @DataBoundSetter
    public void setIncludes(@Nonnull String includes) {
        this.includes = includes.equals(DescriptorImpl.defaultIncludes) ? null : includes;
    }

    @Nonnull
    public String getExcludes() {
        return excludes != null ? excludes : DescriptorImpl.defaultExcludes;
    }

    @DataBoundSetter
    public void setExcludes(@Nonnull String excludes) {
        this.excludes = excludes.equals(DescriptorImpl.defaultExcludes) ? null : excludes;
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

    public String getRepoOwner() {
        return repoOwner;
    }

    @CheckForNull
    public String getScanCredentialsId() {
        return scanCredentialsId;
    }

    @CheckForNull
    public String getCheckoutCredentialsId() {
        return checkoutCredentialsId;
    }

    public String getPattern() {
        return pattern;
    }

    @CheckForNull
    public String getApiUri() {
        return apiUri;
    }

    @DataBoundSetter
    public void setPattern(String pattern) {
        Pattern.compile(pattern);
        this.pattern = pattern;
    }

    @NonNull
    @Override
    protected String id() {
        return StringUtils.defaultIfBlank(apiUri, GITHUB_URL) + "::" + repoOwner;
    }

    @Override
    public void visitSources(SCMSourceObserver observer) throws IOException, InterruptedException {
        TaskListener listener = observer.getListener();

        // Input data validation
        if (repoOwner.isEmpty()) {
            throw new AbortException("Must specify user or organization");
        }

        StandardCredentials credentials = Connector.lookupScanCredentials(observer.getContext(), apiUri, scanCredentialsId);

        // Github client and validation
        GitHub github = Connector.connect(apiUri, credentials);
        try {
            github.checkApiUrlValidity();
        } catch (HttpException e) {
            String message = String.format("It seems %s is unreachable", apiUri == null ? GITHUB_URL : apiUri);
            throw new AbortException(message);
        }

        // Input data validation
        if (credentials != null && !github.isCredentialValid()) {
            String message = String.format("Invalid scan credentials %s to connect to %s, skipping", CredentialsNameProvider.name(credentials), apiUri == null ? GITHUB_URL : apiUri);
            throw new AbortException(message);
        }

        if (!github.isAnonymous()) {
            listener.getLogger().format("Connecting to %s using %s%n", apiUri == null ? GITHUB_URL : apiUri, CredentialsNameProvider.name(credentials));
            GHMyself myself = null;
            try {
                // Requires an authenticated access
                myself = github.getMyself();
            } catch (RateLimitExceededException rle) {
                throw new AbortException(rle.getMessage());
            }
            if (myself != null && repoOwner.equalsIgnoreCase(myself.getLogin())) {
                listener.getLogger().format("Looking up repositories of myself %s%n%n", repoOwner);
                for (GHRepository repo : myself.listRepositories(100)) {
                    if (!observer.isObserving()) {
                        return;
                    }
                    checkInterrupt();
                    if (!repo.getOwnerName().equals(repoOwner)) {
                        continue; // ignore repos in other orgs when using GHMyself
                    }
                    add(listener, observer, repo);
                }
                return;
            }
        } else {
            listener.getLogger().format("Connecting to %s with no credentials, anonymous access%n", apiUri == null ? GITHUB_URL : apiUri);
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
            listener.getLogger().format("Looking up repositories of organization %s%n%n", repoOwner);
            for (GHRepository repo : org.listRepositories(100)) {
                if (!observer.isObserving()) {
                    return;
                }
                checkInterrupt();
                add(listener, observer, repo);
            }
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
                if (!observer.isObserving()) {
                    return;
                }
                checkInterrupt();
                add(listener, observer, repo);
            }
            return;
        }

        throw new AbortException(repoOwner + " does not correspond to a known GitHub User Account or Organization");
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
                Connector.lookupScanCredentials(observer.getContext(), apiUri, scanCredentialsId);

        // Github client and validation
        GitHub github = Connector.connect(apiUri, credentials);
        try {
            github.checkApiUrlValidity();
        } catch (HttpException e) {
            String message = String.format("It seems %s is unreachable", apiUri == null ? GITHUB_URL : apiUri);
            throw new AbortException(message);
        }

        // Input data validation
        if (credentials != null && !github.isCredentialValid()) {
            String message = String.format("Invalid scan credentials %s to connect to %s, skipping",
                    CredentialsNameProvider.name(credentials), apiUri == null ? GITHUB_URL : apiUri);
            throw new AbortException(message);
        }

        if (!github.isAnonymous()) {
            listener.getLogger().format("Connecting to %s using %s%n", apiUri == null ? GITHUB_URL : apiUri,
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
                    add(listener, observer, repo);
                }
                return;
            }
        } else {
            listener.getLogger().format("Connecting to %s with no credentials, anonymous access%n",
                    apiUri == null ? GITHUB_URL : apiUri);
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
            listener.getLogger().format("Looking up %s repository of organization %s%n%n", sourceName, repoOwner);
            GHRepository repo = org.getRepository(sourceName);
            if (repo != null) {
                add(listener, observer, repo);
            }
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
                add(listener, observer, repo);
            }
            return;
        }

        throw new AbortException(repoOwner + " does not correspond to a known GitHub User Account or Organization");
    }

    private void add(TaskListener listener, SCMSourceObserver observer, GHRepository repo) throws InterruptedException {
        String name = repo.getName();
        if (!Pattern.compile(pattern).matcher(name).matches()) {
            listener.getLogger().format("Ignoring %s%n", name);
            return;
        }
        listener.getLogger().format("Proposing %s%n", name);
        checkInterrupt();
        SCMSourceObserver.ProjectObserver projectObserver = observer.observe(name);

        GitHubSCMSource ghSCMSource = new GitHubSCMSource(getId()+ "::" + name,
                apiUri, checkoutCredentialsId, scanCredentialsId, repoOwner, name
        );
        ghSCMSource.setExcludes(getExcludes());
        ghSCMSource.setIncludes(getIncludes());
        ghSCMSource.setBuildOriginBranch(getBuildOriginBranch());
        ghSCMSource.setBuildOriginBranchWithPR(getBuildOriginBranchWithPR());
        ghSCMSource.setBuildOriginPRMerge(getBuildOriginPRMerge());
        ghSCMSource.setBuildOriginPRHead(getBuildOriginPRHead());
        ghSCMSource.setBuildForkPRMerge(getBuildForkPRMerge());
        ghSCMSource.setBuildForkPRHead(getBuildForkPRHead());

        projectObserver.addSource(ghSCMSource);
        projectObserver.complete();
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public List<Action> retrieveActions(@NonNull SCMNavigatorOwner owner,
                                        @CheckForNull SCMNavigatorEvent event,
                                        @NonNull TaskListener listener) throws IOException {
        // TODO when we have support for trusted events, use the details from event if event was from trusted source
        listener.getLogger().printf("Looking up details of %s...%n", getRepoOwner());
        List<Action> result = new ArrayList<>();
        StandardCredentials credentials = Connector.lookupScanCredentials(owner, getApiUri(), getScanCredentialsId());
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
        GHUser u = null;
        try {
            u = hub.getUser(getRepoOwner());
        } catch (FileNotFoundException e) {
            throw new AbortException(String.format("Invalid scan credentials when using %s to connect to %s on %s",
                    credentialsName, getRepoOwner(), apiUri == null ? GITHUB_URL : apiUri));
        }
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
                    Connector.lookupScanCredentials(owner, getApiUri(), getScanCredentialsId());
            GitHub hub = Connector.connect(getApiUri(), credentials);
            GitHubOrgWebHook.register(hub, repoOwner);
        } catch (IOException e) {
            DescriptorImpl.LOGGER.log(Level.WARNING, e.getMessage(), e);
        }
    }

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
            return new GitHubSCMNavigator("", name, "", GitHubSCMSource.DescriptorImpl.SAME);
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
        public FormValidation doCheckScanCredentialsId(@AncestorInPath SCMSourceOwner context,
                                                       @QueryParameter String apiUri,
                                                       @QueryParameter String scanCredentialsId) {
            return Connector.checkScanCredentials(context, apiUri, scanCredentialsId);
        }

        public ListBoxModel doFillScanCredentialsIdItems(@AncestorInPath SCMSourceOwner context, @QueryParameter String apiUri) {
            return Connector.listScanCredentials(context, apiUri);
        }

        public ListBoxModel doFillCheckoutCredentialsIdItems(@AncestorInPath SCMSourceOwner context, @QueryParameter String apiUri) {
            return Connector.listCheckoutCredentials(context, apiUri);
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

        // TODO repeating configuration blocks like this is clumsy; better to factor shared config into a Describable and use f:property

        @Restricted(NoExternalUse.class)
        public FormValidation doCheckIncludes(@QueryParameter String value) {
            return delegate.doCheckIncludes(value);
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
            return delegate.doCheckBuildOriginBranchWithPR(buildOriginBranch, buildOriginBranchWithPR, buildOriginPRMerge, buildOriginPRHead, buildForkPRMerge, buildForkPRHead);
        }

        @Restricted(NoExternalUse.class)
        public FormValidation doCheckBuildOriginPRHead(@QueryParameter boolean buildOriginBranchWithPR, @QueryParameter boolean buildOriginPRMerge, @QueryParameter boolean buildOriginPRHead) {
            return delegate.doCheckBuildOriginPRHead(buildOriginBranchWithPR, buildOriginPRMerge, buildOriginPRHead);
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
            return delegate.doCheckBuildForkPRHead(buildOriginBranch, buildOriginBranchWithPR, buildOriginPRMerge, buildOriginPRHead, buildForkPRMerge, buildForkPRHead);
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

}
