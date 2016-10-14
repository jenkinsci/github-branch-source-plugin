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

import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cloudbees.jenkins.GitHubRepositoryName;
import hudson.Extension;
import hudson.model.CauseAction;
import hudson.model.Job;
import hudson.security.ACL;
import jenkins.branch.BranchProperty;
import jenkins.branch.MultiBranchProject;
import jenkins.model.ParameterizedJobMixIn;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.api.SCMSourceOwners;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.github.extension.GHEventsSubscriber;
import org.kohsuke.github.GHEvent;

import static com.google.common.collect.Sets.immutableEnumSet;
import static org.kohsuke.github.GHEvent.ISSUE_COMMENT;

/**
 * This subscriber manages {@link org.kohsuke.github.GHEvent} ISSUE_COMMENT.
 */
@Extension
public class IssueCommentGHEventSubscriber extends GHEventsSubscriber {
    /**
     * Logger.
     */
    private static final Logger LOGGER = Logger.getLogger(IssueCommentGHEventSubscriber.class.getName());
    /**
     * Regex pattern for a GitHub repository.
     */
    private static final Pattern REPOSITORY_NAME_PATTERN = Pattern.compile("https?://([^/]+)/([^/]+)/([^/]+)");
    /**
     * Regex pattern for a pull request ID.
     */
    private static final Pattern PULL_REQUEST_ID_PATTERN = Pattern.compile("https?://[^/]+/[^/]+/[^/]+/pull/(\\d+)");
    /**
     * String representing the created action on an issue comment.
     */
    private static final String ACTION_CREATED = "created";
    /**
     * String representing the edited action on an issue comment.
     */
    private static final String ACTION_EDITED = "edited";

    @Override
    protected boolean isApplicable(Job<?, ?> project) {
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

    @Override
    protected Set<GHEvent> events() {
        return immutableEnumSet(ISSUE_COMMENT);
    }

    /**
     * Handles comments on pull requests.
     * @param event only ISSUE_COMMENT event
     * @param payload payload of gh-event. Never blank
     */
    @Override
    protected void onEvent(GHEvent event, String payload) {
        JSONObject json = JSONObject.fromObject(payload);

        // Make sure this issue is a PR
        final String issueUrl = json.getJSONObject("issue").getString("html_url");
        Matcher matcher = PULL_REQUEST_ID_PATTERN.matcher(issueUrl);
        if (!matcher.matches()) {
            LOGGER.log(Level.FINE, "Issue comment is not for a pull request, ignoring {0}", issueUrl);
            return;
        }

        final String pullRequestId = matcher.group(1);
        final String pullRequestJobName = "PR-" + pullRequestId;
        
        // Verify that the comment body matches the trigger build string
        final String commentBody = json.getJSONObject("comment").getString("body");
        final String commentUrl = json.getJSONObject("comment").getString("html_url");

        // Make sure the action is edited or created (not deleted)
        String action = json.getString("action");
        if (!ACTION_CREATED.equals(action) && !ACTION_EDITED.equals(action)) {
            LOGGER.log(Level.FINER, "Issue comment action is not created or edited ({0}) for PR {1}",
                new Object[] { action, issueUrl }
            );
            return;
        }

        // Make sure the repository URL is valid
        String repoUrl = json.getJSONObject("repository").getString("html_url");
        matcher = REPOSITORY_NAME_PATTERN.matcher(repoUrl);
        if (!matcher.matches()) {
            LOGGER.log(Level.WARNING, "Malformed repository URL {0}", repoUrl);
            return;
        }
        final GitHubRepositoryName changedRepository = GitHubRepositoryName.create(repoUrl);
        if (changedRepository == null) {
            LOGGER.log(Level.WARNING, "Malformed repository URL {0}", repoUrl);
            return;
        }

        LOGGER.log(Level.FINE, "Received comment on PR {0} for {1}", new Object[] { pullRequestId, repoUrl });
        ACL.impersonate(ACL.SYSTEM, new Runnable() {
            @Override
            public void run() {
                boolean jobFound = false;
                for (final SCMSourceOwner owner : SCMSourceOwners.all()) {
                    for (SCMSource source : owner.getSCMSources()) {
                        if (!(source instanceof GitHubSCMSource)) {
                            continue;
                        }
                        GitHubSCMSource gitHubSCMSource = (GitHubSCMSource) source;
                        if (gitHubSCMSource.getRepoOwner().equals(changedRepository.getUserName()) &&
                                gitHubSCMSource.getRepository().equals(changedRepository.getRepositoryName())) {
                            for (Job<?, ?> job : owner.getAllJobs()) {
                                if (job.getName().equals(pullRequestJobName)) {
                                    if (!(job.getParent() instanceof MultiBranchProject)) {
                                        continue;
                                    }
                                    boolean propFound = false;
                                    for (BranchProperty prop : ((MultiBranchProject) job.getParent()).getProjectFactory().
                                            getBranch(job).getProperties()) {
                                        if (!(prop instanceof TriggerPRCommentBranchProperty)) {
                                            continue;
                                        }
                                        propFound = true;
                                        String expectedCommentBody = ((TriggerPRCommentBranchProperty) prop).getCommentBody();
                                        if (expectedCommentBody.equals(commentBody)) {
                                            ParameterizedJobMixIn.scheduleBuild2(job, 0,
                                                    new CauseAction(new GitHubPullRequestCommentCause(commentUrl)));
                                            LOGGER.log(Level.FINE,
                                                    "Triggered build for {0} due to PR comment on {1}:{2}/{3}",
                                                    new Object[] {
                                                            job.getFullName(),
                                                            changedRepository.getHost(),
                                                            changedRepository.getUserName(),
                                                            changedRepository.getRepositoryName()
                                                    }
                                            );
                                        } else {
                                            LOGGER.log(Level.FINER,
                                                    "Issue comment does not match the trigger build string ({0}) for PR {1}",
                                                    new Object[] { expectedCommentBody, issueUrl }
                                            );
                                            return;
                                        }
                                        break;
                                    }

                                    if (!propFound) {
                                        LOGGER.log(Level.FINE, "Job {0} for {1}:{2}/{3} does not have a trigger branch property",
                                                new Object[] {
                                                        job.getFullName(),
                                                        changedRepository.getHost(),
                                                        changedRepository.getUserName(),
                                                        changedRepository.getRepositoryName()
                                                }
                                        );
                                    }

                                    jobFound = true;
                                    break;
                                }
                            }
                        }
                    }
                }
                if (!jobFound) {
                    LOGGER.log(Level.FINE, "PR comment on {0}:{1}/{2} did not match any job",
                        new Object[] {
                            changedRepository.getHost(), changedRepository.getUserName(),
                            changedRepository.getRepositoryName()
                        }
                    );
                }
            }
        });
    }
}
