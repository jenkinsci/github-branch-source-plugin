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

import com.cloudbees.jenkins.GitHubRepositoryName;
import hudson.Extension;
import hudson.model.Job;
import hudson.security.ACL;
import jenkins.branch.OrganizationFolder;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.api.SCMSourceOwners;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.github.extension.GHEventsSubscriber;
import org.kohsuke.github.GHEvent;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.collect.Sets.immutableEnumSet;
import static org.kohsuke.github.GHEvent.REPOSITORY;

/**
 * This subscriber manages {@link org.kohsuke.github.GHEvent} REPOSITORY.
 */
@Extension
public class RepositoryGHEventSubscriber extends GHEventsSubscriber {

    private static final Logger LOGGER = Logger.getLogger(RepositoryGHEventSubscriber.class.getName());
    private static final Pattern REPOSITORY_NAME_PATTERN = Pattern.compile("https?://([^/]+)/([^/]+)/([^/]+)");


    @Override
    protected boolean isApplicable(@Nullable Job<?, ?> project) {
        return true;
    }

    /**
     * @return set with only REPOSITORY event
     */
    @Override
    protected Set<GHEvent> events() {
        return immutableEnumSet(REPOSITORY);
    }

    /**
     * @param event only REPOSITORY event
     * @param payload payload of gh-event. Never blank
     */
    @Override
    protected void onEvent(GHEvent event, String payload) {
        JSONObject json = JSONObject.fromObject(payload);
        String repoUrl = json.getJSONObject("repository").getString("html_url");
        boolean fork = json.getJSONObject("repository").getBoolean("fork");

        LOGGER.log(Level.FINE, "Received REPOSITORY_EVENT for {0}", repoUrl);
        Matcher matcher = REPOSITORY_NAME_PATTERN.matcher(repoUrl);
        if (matcher.matches()) {
            final GitHubRepositoryName repo = GitHubRepositoryName.create(repoUrl);
            if (repo == null) {
                LOGGER.log(Level.WARNING, "Malformed repository URL {0}", repoUrl);
                return;
            }
            if (!fork) {
                LOGGER.log(Level.FINE, "Repository {0} was created but it is empty, will be ignored", repo.getRepositoryName());
                return;
            }
            ACL.impersonate(ACL.SYSTEM, new Runnable() {
                @Override public void run() {
                    for (final SCMSourceOwner owner : SCMSourceOwners.all()) {
                        if (owner instanceof OrganizationFolder) {
                            OrganizationFolder orgFolder = (OrganizationFolder) owner;
                            for (GitHubSCMNavigator navigator : orgFolder.getNavigators().getAll(GitHubSCMNavigator.class)) {
                                if (Pattern.matches(navigator.getPattern(), repo.getRepositoryName())) {
                                    orgFolder.scheduleBuild();
                                }
                            }
                        }
                    }
                }
            });
        } else {
            LOGGER.log(Level.WARNING, "Malformed repository URL {0}", repoUrl);
        }
    }
}
