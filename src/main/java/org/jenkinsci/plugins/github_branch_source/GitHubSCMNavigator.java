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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import javax.annotation.CheckForNull;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.SCMNavigatorDescriptor;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceOwner;
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
    private String pattern = ".*";

    @DataBoundConstructor public GitHubSCMNavigator(String repoOwner, String scanCredentialsId, String checkoutCredentialsId) {
        this.repoOwner = repoOwner;
        this.scanCredentialsId = Util.fixEmpty(scanCredentialsId);
        this.checkoutCredentialsId = checkoutCredentialsId;
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

    @DataBoundSetter public void setPattern(String pattern) {
        Pattern.compile(pattern);
        this.pattern = pattern;
    }

    @Override public Map<String, ? extends List<? extends SCMSource>> discoverSources(SCMSourceOwner context, TaskListener listener) throws IOException, InterruptedException {
        Map<String,List<? extends SCMSource>> result = new TreeMap<String,List<? extends SCMSource>>();
        if (repoOwner.isEmpty()) {
            listener.getLogger().format("Must specify user or organization%n");
            return result;
        }
        String apiUrl = null; // TODO GHE
        StandardCredentials credentials = Connector.lookupScanCredentials(context, apiUrl, scanCredentialsId);
        if (credentials == null) {
            listener.getLogger().println("No scan credentials, skipping");
            return result;
        }
        listener.getLogger().format("Connecting to GitHub using %s%n", CredentialsNameProvider.name(credentials));
        GitHub github = Connector.connect(apiUrl, credentials);
        GHMyself myself = null;
        try {
            myself = github.getMyself();
        } catch (IllegalStateException e) {
            // may be anonymous... ok to ignore
        } catch (IOException e) {
            // may be anonymous... ok to ignore
        }
        if (myself != null && repoOwner.equals(myself.getLogin())) {
            listener.getLogger().format("Looking up repositories of myself %s%n", repoOwner);
            for (GHRepository repo : myself.listRepositories()) {
                if (!repo.getOwnerName().equals(repoOwner)) {
                    continue; // ignore repos in other orgs when using GHMyself
                }
                add(listener, result, repo);
            }
            return result;
        }
        GHOrganization org = github.getOrganization(repoOwner);
        if (org != null && repoOwner.equals(org.getLogin())) {
            listener.getLogger().format("Looking up repositories of organization %s%n", repoOwner);
            for (GHRepository repo : org.listRepositories()) {
                add(listener, result, repo);
            }
            return result;
        }
        GHUser user = null;
        try {
            user = github.getUser(repoOwner);
        } catch (IOException e) {
            // may be organization... ok to ignore
        }
        if (user != null && repoOwner.equals(user.getLogin())) {
            listener.getLogger().format("Looking up repositories of user %s%n", repoOwner);
            for (GHRepository repo : user.listRepositories()) {
                add(listener, result, repo);
            }
            return result;
        }
        return result;
    }

    private void add(TaskListener listener, Map<String, List<? extends SCMSource>> result, GHRepository repo) throws InterruptedException {
        String name = repo.getName();
        if (!Pattern.compile(pattern).matcher(name).matches()) {
            listener.getLogger().format("Ignoring %s%n", name);
            return;
        }
        listener.getLogger().format("Proposing %s%n", name);
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        List<SCMSource> sources = new ArrayList<SCMSource>();
        for (GitHubSCMSourceAddition addition : ExtensionList.lookup(GitHubSCMSourceAddition.class)) {
            sources.addAll(addition.sourcesFor(checkoutCredentialsId, scanCredentialsId, repoOwner, name));
        }
        result.put(name, sources);
    }

    public interface GitHubSCMSourceAddition extends ExtensionPoint {
        List<? extends SCMSource> sourcesFor(String checkoutCredentialsId, String scanCredentialsId, String repoOwner, String repository);
    }

    @Extension public static class DescriptorImpl extends SCMNavigatorDescriptor {

        @Override public String getDisplayName() {
            return "GitHub Organization";
        }

        @Override public SCMNavigator newInstance(String name) {
            return new GitHubSCMNavigator(name, "", AbstractGitHubSCMSource.AbstractGitHubSCMSourceDescriptor.SAME);
        }

        public FormValidation doCheckScanCredentialsId(@QueryParameter String value) {
            if (!value.isEmpty()) {
                return FormValidation.ok();
            } else {
                return FormValidation.warning("Credentials are required");
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

    }

}
