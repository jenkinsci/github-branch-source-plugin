/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
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

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.listeners.ItemListener;
import hudson.util.DescribableList;
import jenkins.branch.MultiBranchProject;
import jenkins.branch.OrganizationFolder;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.SCMNavigatorDescriptor;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceOwner;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHHook;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * TODO: Add description.
 */
@Extension
public class WebhookRegisterListener extends ItemListener {

    private static final Logger LOGGER = Logger.getLogger(WebhookRegisterListener.class.getName());

    @Override
    public void onCreated(Item item) {
        if (!isApplicable(item)) {
            return;
        }
    }

    @Override
    public void onUpdated(Item item) {
        if (!isApplicable(item)) {
            return;
        }
        if (item instanceof MultiBranchProject) {
            SCMSourceOwner owner = (SCMSourceOwner) item;
            for (SCMSource source : owner.getSCMSources()) {
                if (source instanceof AbstractGitHubSCMSource) {
                    AbstractGitHubSCMSource gitHubSCMSource = (AbstractGitHubSCMSource) source;
                    if (gitHubSCMSource.getScanCredentialsId() == null) {
                        return;
                    }
                    StandardCredentials credentials = Connector.lookupScanCredentials(owner, gitHubSCMSource.getApiUri(), gitHubSCMSource.getScanCredentialsId());
                    try {
                        GitHub github = Connector.connect(gitHubSCMSource.getApiUri(), credentials);
                        GHRepository repo = github.getRepository(gitHubSCMSource.getRepoOwner() + "/" + gitHubSCMSource.getRepository());
                        String url = Jenkins.getActiveInstance().getRootUrl() + "/github-webhook/";
                        if (!hasHook(repo.getHooks(), url)) {
                            repo.createWebHook(new URL(url), Collections.singletonList(GHEvent.PUSH));
                            LOGGER.log(Level.INFO /* FINE */, "A webhook was registered for the repository {0}", repo.getFullName());
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        } else if (item instanceof OrganizationFolder) {
            OrganizationFolder organizationFolder = (OrganizationFolder) item;
            DescribableList<SCMNavigator, SCMNavigatorDescriptor> navigators = organizationFolder.getNavigators();
            if (navigators == null) {
                return;
            }
            for (SCMNavigator navigator : navigators) {
                if (navigator instanceof GitHubSCMNavigator) {
                    GitHubSCMNavigator gitHubSCMNavigator = (GitHubSCMNavigator) navigator;
                    if (gitHubSCMNavigator.getScanCredentialsId() == null) {
                        return;
                    }
                    StandardCredentials credentials = Connector.lookupScanCredentials(organizationFolder, gitHubSCMNavigator.getApiUri(), gitHubSCMNavigator.getScanCredentialsId());
                    try {
                        GitHub github = Connector.connect(gitHubSCMNavigator.getApiUri(), credentials);
                        GHOrganization org = github.getOrganization(gitHubSCMNavigator.getRepoOwner());
                        String url = Jenkins.getActiveInstance().getRootUrl() + "/github-webhook/";
                        if (!hasHook(org.getHooks(), url)) {
                            org.createWebHook(new URL(url), Collections.singletonList(GHEvent.REPOSITORY));
                            LOGGER.log(Level.INFO /* FINE */, "A webhook was registered for the organization {0}", org.getName());
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

    }

    private boolean isApplicable(Item item) {
        return item instanceof SCMSourceOwner;
    }

    /**
     * TODO: Add description.
     */
    private boolean hasHook (List<GHHook> hooks, String url) {
        int cont = 0;
        boolean found = false;
        while (!found && cont < hooks.size()) {
            GHHook current = hooks.get(cont);
            if (current.getConfig().get("url").equals(url)) {
                found = true;
            } else {
                cont++;
            }
        }
        return found;
    }
}
