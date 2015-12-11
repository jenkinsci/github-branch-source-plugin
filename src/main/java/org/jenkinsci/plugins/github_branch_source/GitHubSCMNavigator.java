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
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.Util;
import hudson.model.TaskListener;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;
import javax.annotation.CheckForNull;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.SCMNavigatorDescriptor;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceObserver;
import jenkins.scm.api.SCMSourceOwner;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.github.GHMyself;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

public class GitHubSCMNavigator extends SCMNavigator {

    private final String repoOwner;
    private final String scanCredentialsId;
    private final String checkoutCredentialsId;
    private final String apiUri;
    private String pattern = ".*";

    @DataBoundConstructor public GitHubSCMNavigator(String apiUri, String repoOwner, String scanCredentialsId, String checkoutCredentialsId) {
        this.repoOwner = repoOwner;
        this.scanCredentialsId = Util.fixEmpty(scanCredentialsId);
        this.checkoutCredentialsId = checkoutCredentialsId;
        this.apiUri = Util.fixEmpty(apiUri);
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
        if (repoOwner.isEmpty()) {
            listener.getLogger().format("Must specify user or organization%n");
            return;
        }
        StandardCredentials credentials = Connector.lookupScanCredentials(observer.getContext(), apiUri, scanCredentialsId);
        GitHub github = Connector.connect(apiUri, credentials);
        if (credentials != null && !github.isCredentialValid()) {
            listener.getLogger().format("Invalid scan credentials, skipping%n");
            return;
        }

        if (!github.isAnonymous()) {
            listener.getLogger().format("Connecting to GitHub using %s%n", CredentialsNameProvider.name(credentials));
            GHMyself myself = null;
            try {
                // Requires an authenticated access
                myself = github.getMyself();
            } catch (IOException e) {
                // Something wrong happened, maybe java.net.ConnectException?
            }
            if (myself != null && repoOwner.equals(myself.getLogin())) {
                listener.getLogger().format("Looking up repositories of myself %s%n%n", repoOwner);
                for (GHRepository repo : myself.listRepositories()) {
                    if (!repo.getOwnerName().equals(repoOwner)) {
                        continue; // ignore repos in other orgs when using GHMyself
                    }
                    add(listener, observer, repo);
                }
                return;
            }
        } else {
            listener.getLogger().format("Connecting to GitHub using anonymous access%n");
        }

        GHOrganization org = null;
        try {
            org = github.getOrganization(repoOwner);
        } catch (RateLimitExceededException rle) {
            listener.getLogger().format("%n%s%n%n", rle.getMessage());
            throw new InterruptedException();
        } catch (IOException e) {
            // may be a user... ok to ignore
        }
        if (org != null && repoOwner.equals(org.getLogin())) {
            listener.getLogger().format("Looking up repositories of organization %s%n%n", repoOwner);
            for (GHRepository repo : org.listRepositories()) {
                add(listener, observer, repo);
            }
            return;
        }

        GHUser user = null;
        try {
            user = github.getUser(repoOwner);
        } catch (RateLimitExceededException rle) {
            listener.getLogger().format("%n%s%n%n", rle.getMessage());
            throw new InterruptedException();
        } catch (IOException e) {
            // Something wrong happened, maybe java.net.ConnectException?
        }
        if (user != null && repoOwner.equals(user.getLogin())) {
            listener.getLogger().format("Looking up repositories of user %s%n%n", repoOwner);
            for (GHRepository repo : user.listRepositories()) {
                add(listener, observer, repo);
            }
        }
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
        for (GitHubSCMSourceAddition addition : ExtensionList.lookup(GitHubSCMSourceAddition.class)) {
            for (SCMSource source : addition.sourcesFor(apiUri, checkoutCredentialsId, scanCredentialsId, repoOwner, name)) {
                projectObserver.addSource(source);
            }
        }
        projectObserver.complete();
    }

    public interface GitHubSCMSourceAddition extends ExtensionPoint {
        List<? extends SCMSource> sourcesFor(String apiUri, String checkoutCredentialsId, String scanCredentialsId, String repoOwner, String repository);
    }

    @Extension public static class DescriptorImpl extends SCMNavigatorDescriptor {

        @Override public String getDisplayName() {
            return "GitHub Organization";
        }

        @Override public SCMNavigator newInstance(String name) {
            return new GitHubSCMNavigator("", name, "", AbstractGitHubSCMSource.AbstractGitHubSCMSourceDescriptor.SAME);
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

        public ListBoxModel doFillScanCredentialsIdItems(@AncestorInPath SCMSourceOwner context/* TODO , @QueryParameter String apiUri*/) {
            StandardListBoxModel result = new StandardListBoxModel();
            result.withEmptySelection();
            Connector.fillScanCredentialsIdItems(result, context, null);
            return result;
        }

        public ListBoxModel doFillCheckoutCredentialsIdItems(@AncestorInPath SCMSourceOwner context/* TODO , @QueryParameter String apiUri*/) {
            StandardListBoxModel result = new StandardListBoxModel();
            result.add("- same as scan credentials -", AbstractGitHubSCMSource.AbstractGitHubSCMSourceDescriptor.SAME);
            result.add("- anonymous -", AbstractGitHubSCMSource.AbstractGitHubSCMSourceDescriptor.ANONYMOUS);
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
    }

}
