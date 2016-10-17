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

import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.AbortException;
import hudson.Extension;
import hudson.Util;
import hudson.model.TaskListener;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.SCMNavigatorDescriptor;
import jenkins.scm.api.SCMSourceObserver;
import jenkins.scm.api.SCMSourceOwner;
import org.jenkins.ui.icon.Icon;
import org.jenkins.ui.icon.IconSet;
import org.jenkins.ui.icon.IconSpec;
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

import static org.jenkinsci.plugins.github.config.GitHubServerConfig.GITHUB_URL;

public class GitHubSCMNavigator extends SCMNavigator {

    private final String repoOwner;
    private final String scanCredentialsId;
    private final String checkoutCredentialsId;
    private final String apiUri;
    private String pattern = ".*";

    @CheckForNull private String includes;
    @CheckForNull private String excludes;
    /** Whether to build regular origin branches. */
    private @Nonnull Boolean buildOriginBranch = DescriptorImpl.defaultBuildOriginBranch;
    /** Whether to build origin branches which happen to also have a PR filed from them (but here we are naming and building as a branch). */
    private @Nonnull Boolean buildOriginBranchWithPR = DescriptorImpl.defaultBuildOriginBranchWithPR;
    /** Whether to build PRs filed from the origin, where the build is of the merge with the base branch. */
    private @Nonnull Boolean buildOriginPRMerge = DescriptorImpl.defaultBuildOriginPRMerge;
    /** Whether to build PRs filed from the origin, where the build is of the branch head. */
    private @Nonnull Boolean buildOriginPRHead = DescriptorImpl.defaultBuildOriginPRHead;
    /** Whether to build PRs filed from a fork, where the build is of the merge with the base branch. */
    private @Nonnull Boolean buildForkPRMerge = DescriptorImpl.defaultBuildForkPRMerge;
    /** Whether to build PRs filed from a fork, where the build is of the branch head. */
    private @Nonnull Boolean buildForkPRHead = DescriptorImpl.defaultBuildForkPRHead;

    @DataBoundConstructor public GitHubSCMNavigator(String apiUri, String repoOwner, String scanCredentialsId, String checkoutCredentialsId) {
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

    @Nonnull public String getIncludes() {
        return includes != null ? includes : DescriptorImpl.defaultIncludes;
    }

    @DataBoundSetter public void setIncludes(@Nonnull String includes) {
        this.includes = includes.equals(DescriptorImpl.defaultIncludes) ? null : includes;
    }

    @Nonnull public String getExcludes() {
        return excludes != null ? excludes : DescriptorImpl.defaultExcludes;
    }

    @DataBoundSetter public void setExcludes(@Nonnull String excludes) {
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

    @DataBoundSetter public void setPattern(String pattern) {
        Pattern.compile(pattern);
        this.pattern = pattern;
    }

    @Override public void visitSources(SCMSourceObserver observer) throws IOException, InterruptedException {
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
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        SCMSourceObserver.ProjectObserver projectObserver = observer.observe(name);
        
        GitHubSCMSource ghSCMSource = new GitHubSCMSource(null, apiUri, checkoutCredentialsId, scanCredentialsId, repoOwner, name);
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

    @Extension public static class DescriptorImpl extends SCMNavigatorDescriptor implements IconSpec {

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

        @Override public String getDisplayName() {
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
                        LOGGER.log(Level.WARNING, "Exception validating credentials " + CredentialsNameProvider.name(credentials) + " on " + apiUri);
                        return FormValidation.error("Exception validating credentials");
                    }
                }
            } else {
                return FormValidation.warning("Credentials are recommended");
            }
        }

        public ListBoxModel doFillScanCredentialsIdItems(@AncestorInPath SCMSourceOwner context/* TODO , @QueryParameter String apiUri*/) {
            StandardListBoxModel result = new StandardListBoxModel();
            result.withEmptySelection();
            Connector.fillScanCredentialsIdItems(result, context, null);
            return result;
        }

        public ListBoxModel doFillCheckoutCredentialsIdItems(@AncestorInPath SCMSourceOwner context/* TODO , @QueryParameter String apiUri*/) {
            StandardListBoxModel result = new StandardListBoxModel();
            result.add("- same as scan credentials -", GitHubSCMSource.DescriptorImpl.SAME);
            result.add("- anonymous -", GitHubSCMSource.DescriptorImpl.ANONYMOUS);
            Connector.fillCheckoutCredentialsIdItems(result, context, null);
            return result;
        }

        public ListBoxModel doFillApiUriItems() {
            ListBoxModel result = new ListBoxModel();
            result.add("GitHub", "");
            for (Endpoint e : GitHubConfiguration.get().getEndpoints()) {
                result.add(e.getName() == null ? e.getApiUri() : e.getName(), e.getApiUri());
            }
            return result;
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
        }
    }

}
