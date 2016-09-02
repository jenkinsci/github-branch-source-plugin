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
import hudson.model.CauseAction;
import hudson.model.Job;
import hudson.security.ACL;
import jenkins.branch.BranchProjectFactory;
import jenkins.model.ParameterizedJobMixIn;
import jenkins.scm.api.*;
import jenkins.util.Timer;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.github.extension.GHEventsSubscriber;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.kohsuke.github.*;
import org.kohsuke.github.GHEventPayload.PullRequest;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.StringReader;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.collect.Sets.immutableEnumSet;
import static org.kohsuke.github.GHEvent.PULL_REQUEST;

/**
 * This subscriber manages {@link org.kohsuke.github.GHEvent} PULL_REQUEST.
 */
@Extension
public class PullRequestGHEventSubscriber extends GHEventsSubscriber {

    private static final Logger LOGGER = Logger.getLogger(PullRequestGHEventSubscriber.class.getName());
    private static final Pattern REPOSITORY_NAME_PATTERN = Pattern.compile("https?://([^/]+)/([^/]+)/([^/]+)");

    @Override
    protected boolean isApplicable(@Nullable Job<?, ?> project) {
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
     * @return set with only PULL_REQUEST event
     */
    @Override
    protected Set<GHEvent> events() {
        return immutableEnumSet(PULL_REQUEST);
    }

    /**
     * @param event only PULL_REQUEST event
     * @param payload payload of gh-event. Never blank
     */
    @Override
    protected void onEvent(final GHEvent event, final String payload) {
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
                                                    LOGGER.log(Level.FINE, "PR event on {0}:{1}/{2} forwarded to {3} individually", new Object[] {changedRepository.getHost(), changedRepository.getUserName(), changedRepository.getRepositoryName(), owner.getFullName()});
                                                } else {
                                                    LOGGER.log(Level.FINE, "PR event on {0}:{1}/{2} - could not forward to {3} individually", new Object[] {changedRepository.getHost(), changedRepository.getUserName(), changedRepository.getRepositoryName(), owner.getFullName()});
                                                }
                                            }

                                            if (!found) {
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

    private boolean doUpdateFromEvent(String payload, JSONObject json, WorkflowMultiBranchProject owner, GitHubSCMSource source) {
        GitHub github;
        GHPullRequest pull;
        GHRepository repository;
        boolean trusted;

        try {
            github = source.getGitHub();
            pull = getPullRequest(payload, github).getPullRequest();

            LOGGER.log(Level.INFO, "Got PR object from event payload: " + pull.getNumber());

            repository = source.getRepository(github);
            trusted = source.isTrusted(repository, pull);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Could not connect to GitHub during webhook update: " + e);
            return false;
        }

        String prHeadOwner = json.getJSONObject("pull_request").getJSONObject("head").getJSONObject("repo").getJSONObject("owner").getString("login");
        boolean fork = !source.getRepoOwner().equals(prHeadOwner); // ?

        String baseHash = pull.getBase().getSha();
        String headHash = pull.getHead().getSha();

        boolean found = false;

        for (boolean merge : new boolean[] {false, true}) {
            String name = source.getPRJobName(pull.getNumber(), merge, fork);

            if (name == null) {
                continue;
            }

            PullRequestSCMHead head = new PullRequestSCMHead(pull, name, merge, trusted);
            PullRequestSCMRevision revision = new PullRequestSCMRevision(head, baseHash, headHash);

            found = found || scheduleBuild(owner, revision, name);
        }

        return found;
    }

    /**
     * todo Rough copy from MultiBranchProject.scheduleBuild because I'm not sure how to access it here
     */
    private boolean scheduleBuild(WorkflowMultiBranchProject owner, SCMRevision revision, String name) {
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

    private PullRequest getPullRequest(String payload, GitHub gh) throws IOException {
        return gh.parseEventPayload(new StringReader(payload), PullRequest.class);
    }
}
