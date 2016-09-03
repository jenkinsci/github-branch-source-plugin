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
import hudson.model.CauseAction;
import hudson.model.Job;
import hudson.security.ACL;
import jenkins.branch.BranchProjectFactory;
import jenkins.model.ParameterizedJobMixIn;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.api.SCMSourceOwners;
import jenkins.util.Timer;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.github.extension.GHEventsSubscriber;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GitHub;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.StringReader;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

abstract class AbstractGHEventSubscriber extends GHEventsSubscriber {
    private static final Logger LOGGER = Logger.getLogger(AbstractGHEventSubscriber.class.getName());
    private static final Pattern REPOSITORY_NAME_PATTERN = Pattern.compile("https?://([^/]+)/([^/]+)/([^/]+)");
    private static final boolean ENABLE_FALLBACK_BRANCH_INDEXING = false;

    /**
     * @param payload The string payload of the webhook
     * @param json    The json payload of the webhook
     * @param owner   The 'owner' project of the source
     * @param source  The source instance concerned
     * @return Whether the event was handled - if true, no further action need be taken. If false, full branch reindexing
     *          may be needed, because the event could not be handled.
     */
    abstract protected boolean doUpdateFromEvent(String payload, JSONObject json, WorkflowMultiBranchProject owner, GitHubSCMSource source);

    @Override
    final protected boolean isApplicable(@Nullable Job<?, ?> project) {
        if (project != null) {
            if (project.getParent() instanceof SCMSourceOwner) {
                SCMSourceOwner owner = (SCMSourceOwner) project.getParent();
                for (SCMSource source : owner.getSCMSources()) {
                    if (source instanceof GitHubSCMSource) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * todo Rough copy from MultiBranchProject.scheduleBuild because I'm not sure how to access it here
     */
    final boolean scheduleBuild(WorkflowMultiBranchProject owner, SCMRevision revision, String name) {
        BranchProjectFactory<WorkflowJob, WorkflowRun> factory = owner.getProjectFactory();
        WorkflowJob job = owner.getJob(name);

        if (ParameterizedJobMixIn.scheduleBuild2(job, 0, new CauseAction(new WebhookEventCause())) != null) {
            LOGGER.log(Level.INFO, "Scheduled build for branch: " + name);

            try {
                factory.setRevisionHash(job, revision);
                return true;
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Could not update last revision hash: " + e);
            }
        } else {
            LOGGER.log(Level.INFO, "Did not schedule build for branch: " + name);
        }

        return false;
    }

    /**
     * @param event only PULL_REQUEST event
     * @param payload payload of gh-event. Never blank
     */
    @Override
    final protected void onEvent(final GHEvent event, final String payload) {
        final JSONObject json = JSONObject.fromObject(payload);
        String repoUrl = json.getJSONObject("repository").getString("html_url");

        LOGGER.log(Level.FINE, "Received POST for {0}", repoUrl);
        Matcher matcher = REPOSITORY_NAME_PATTERN.matcher(repoUrl);
        if (matcher.matches()) {
            final GitHubRepositoryName changedRepository = GitHubRepositoryName.create(repoUrl);
            if (changedRepository == null) {
                LOGGER.log(Level.WARNING, "Malformed repository URL {0}", repoUrl);
                return;
            }
            // Delaying the indexing for some seconds to avoid GitHub cache
            Timer.get().schedule(new Runnable() {
                @Override public void run() {
                    ACL.impersonate(ACL.SYSTEM, new Runnable() {
                        @Override public void run() {
                            boolean found = false;
                            for (final SCMSourceOwner owner : SCMSourceOwners.all()) {
                                for (SCMSource source : owner.getSCMSources()) {
                                    if (source instanceof GitHubSCMSource) {
                                        GitHubSCMSource gitHubSCMSource = (GitHubSCMSource) source;
                                        if (gitHubSCMSource.getRepoOwner().equals(changedRepository.getUserName()) &&
                                                gitHubSCMSource.getRepository().equals(changedRepository.getRepositoryName())) {

                                            if (owner instanceof WorkflowMultiBranchProject) {
                                                found = doUpdateFromEvent(payload, json, (WorkflowMultiBranchProject) owner, (GitHubSCMSource) source);

                                                if (found) {
                                                    LOGGER.log(Level.FINE, "event on {0}:{1}/{2} forwarded to {3} individually", new Object[] {changedRepository.getHost(), changedRepository.getUserName(), changedRepository.getRepositoryName(), owner.getFullName()});
                                                } else {
                                                    LOGGER.log(Level.FINE, "event on {0}:{1}/{2} - could not forward to {3} individually", new Object[] {changedRepository.getHost(), changedRepository.getUserName(), changedRepository.getRepositoryName(), owner.getFullName()});
                                                }
                                            }

                                            if (!found && ENABLE_FALLBACK_BRANCH_INDEXING) {
                                                owner.onSCMSourceUpdated(gitHubSCMSource);
                                                LOGGER.log(Level.FINE, "PR event on {0}:{1}/{2} forwarded to {3} (full onSCMSourceUpdated)", new Object[] {changedRepository.getHost(), changedRepository.getUserName(), changedRepository.getRepositoryName(), owner.getFullName()});
                                                found = true;
                                            }
                                        }
                                    }
                                }
                            }
                            if (!found) {
                                LOGGER.log(Level.FINE, "PR event on {0}:{1}/{2} did not match any project", new Object[] {changedRepository.getHost(), changedRepository.getUserName(), changedRepository.getRepositoryName()});
                            }
                        }
                    });
                }
            }, 5, TimeUnit.SECONDS);
        } else {
            LOGGER.log(Level.WARNING, "Malformed repository URL {0}", repoUrl);
        }
    }
}
