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
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Item;
import hudson.scm.SCM;
import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.scm.api.SCMEvent;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.util.Timer;
import org.jenkinsci.plugins.github.extension.GHEventsSubscriber;
import org.jenkinsci.plugins.github.extension.GHSubscriberEvent;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

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
    protected boolean isApplicable(@Nullable Item project) {
        if (project != null) {
            if (project instanceof SCMSourceOwner) {
                SCMSourceOwner owner = (SCMSourceOwner) project;
                for (SCMSource source : owner.getSCMSources()) {
                    if (source instanceof GitHubSCMSource) {
                        return true;
                    }
                }
            }
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

    @Override
    protected void onEvent(GHSubscriberEvent event) {
        try {
            final GHEventPayload.PullRequest p = GitHub.offline()
                    .parseEventPayload(new StringReader(event.getPayload()), GHEventPayload.PullRequest.class);
            String action = p.getAction();
            String repoUrl = p.getRepository().getHtmlUrl().toExternalForm();
            LOGGER.log(Level.INFO, "Received {0} for {1} from {2}",
                    new Object[]{event.getGHEvent(), repoUrl, event.getOrigin()}
            );
            Matcher matcher = REPOSITORY_NAME_PATTERN.matcher(repoUrl);
            if (matcher.matches()) {
                final GitHubRepositoryName changedRepository = GitHubRepositoryName.create(repoUrl);
                if (changedRepository == null) {
                    LOGGER.log(Level.WARNING, "Malformed repository URL {0}", repoUrl);
                    return;
                }

                if ("opened".equals(action)) {
                    fireAfterDelay(new SCMHeadEventImpl(
                            SCMEvent.Type.CREATED,
                            event.getTimestamp(),
                            p,
                            changedRepository,
                            event.getOrigin()
                    ));
                } else if ("reopened".equals(action) || "synchronize".equals(action)) {
                    fireAfterDelay(new SCMHeadEventImpl(
                            SCMEvent.Type.UPDATED,
                            event.getTimestamp(),
                            p,
                            changedRepository,
                            event.getOrigin()
                    ));
                } else if ("closed".equals(action)) {
                    fireAfterDelay(new SCMHeadEventImpl(
                            SCMEvent.Type.REMOVED,
                            event.getTimestamp(),
                            p,
                            changedRepository,
                            event.getOrigin()
                    ));
                }
            }

        } catch (IOException e) {
            LogRecord lr = new LogRecord(Level.WARNING, "Could not parse {0} event from {1} with payload: {2}");
            lr.setParameters(new Object[]{event.getGHEvent(), event.getOrigin(), event.getPayload()});
            lr.setThrown(e);
            LOGGER.log(lr);
        }
    }

    private void fireAfterDelay(final SCMHeadEventImpl e) {
        SCMHeadEvent.fireLater(e, GitHubSCMSource.getEventDelaySeconds(), TimeUnit.SECONDS);
    }

    private static class SCMHeadEventImpl extends SCMHeadEvent<GHEventPayload.PullRequest> {
        private final String repoHost;
        private final String repoOwner;
        private final String repository;

        public SCMHeadEventImpl(Type type, long timestamp, GHEventPayload.PullRequest pullRequest, GitHubRepositoryName repo,
                                String origin) {
            super(type, timestamp, pullRequest, origin);
            this.repoHost = repo.getHost();
            this.repoOwner = pullRequest.getRepository().getOwnerName();
            this.repository = pullRequest.getRepository().getName();
        }

        private boolean isApiMatch(String apiUri) {
            return repoHost.equalsIgnoreCase(RepositoryUriResolver.hostnameFromApiUri(apiUri));
        }

        @Override
        public boolean isMatch(@NonNull SCMNavigator navigator) {
            return navigator instanceof GitHubSCMNavigator
                    && repoOwner.equalsIgnoreCase(((GitHubSCMNavigator) navigator).getRepoOwner());
        }

        @NonNull
        @Override
        public String getSourceName() {
            return repository;
        }

        @NonNull
        @Override
        public Map<SCMHead, SCMRevision> heads(@NonNull SCMSource source) {
            if (!(source instanceof GitHubSCMSource
                    && isApiMatch(((GitHubSCMSource) source).getApiUri())
                    && repoOwner.equalsIgnoreCase(((GitHubSCMSource) source).getRepoOwner())
                    && repository.equalsIgnoreCase(((GitHubSCMSource) source).getRepository()))) {
                return Collections.emptyMap();
            }
            GitHubSCMSource src = (GitHubSCMSource) source;
            GHEventPayload.PullRequest pullRequest = getPayload();
            GHPullRequest ghPullRequest = pullRequest.getPullRequest();
            GHRepository repo = pullRequest.getRepository();
            String prRepoName = repo.getName();
            if (!prRepoName.matches(GitHubSCMSource.VALID_GITHUB_REPO_NAME)) {
                // fake repository name
                return Collections.emptyMap();
            }
            String prOwnerName = ghPullRequest.getHead().getUser().getLogin();
            if (!prOwnerName.matches(GitHubSCMSource.VALID_GITHUB_USER_NAME)) {
                // fake owner name
                return Collections.emptyMap();
            }
            if (!ghPullRequest.getBase().getSha().matches(GitHubSCMSource.VALID_GIT_SHA1)) {
                // fake base sha1
                return Collections.emptyMap();
            }
            if (!ghPullRequest.getHead().getSha().matches(GitHubSCMSource.VALID_GIT_SHA1)) {
                // fake head sha1
                return Collections.emptyMap();
            }
            boolean hasPR = false;

            boolean fork = !src.getRepoOwner().equalsIgnoreCase(prOwnerName);

            Map<SCMHead, SCMRevision> result = new HashMap<>();

            if (src.getBuildOriginBranchWithPR()
                    || src.getBuildOriginPRMerge()
                    || src.getBuildOriginPRHead()
                    || src.getBuildForkPRMerge()
                    || src.getBuildForkPRHead()) {
                int number = pullRequest.getNumber();
                if (fork && !src.getBuildForkPRMerge() && !src.getBuildForkPRHead()) {
                    // Submitted from fork, skipping
                } else if (!fork && !src.getBuildOriginPRMerge() && !src.getBuildOriginPRHead() && !src
                        .getBuildOriginBranchWithPR()) {
                    // Submitted from origin repository, skipping
                } else {
                    if (!fork) {
                        hasPR = true;
                    }
                    for (boolean merge : new boolean[]{false, true}) {
                        String branchName = "PR-" + number;
                        if (merge && fork) {
                            if (!src.getBuildForkPRMerge()) {
                                continue; // not doing this combination
                            }
                            if (src.getBuildForkPRHead()) {
                                branchName += "-merge"; // make sure they are distinct
                            }
                            // If we only build merged, or only unmerged, then we use the /PR-\d+/ scheme as before.
                        }
                        if (merge && !fork) {
                            if (!src.getBuildOriginPRMerge()) {
                                continue;
                            }
                            if (src.getBuildForkPRHead()) {
                                branchName += "-merge";
                            }
                        }
                        if (!merge && fork) {
                            if (!src.getBuildForkPRHead()) {
                                continue;
                            }
                            if (src.getBuildForkPRMerge()) {
                                branchName += "-head";
                            }
                        }
                        if (!merge && !fork) {
                            if (!src.getBuildOriginPRHead()) {
                                continue;
                            }
                            if (src.getBuildOriginPRMerge()) {
                                branchName += "-head";
                            }
                        }
                        // don't care about trusted as SCMHead equality is based on the branch name anyway and
                        PullRequestSCMHead head = new PullRequestSCMHead(ghPullRequest, branchName, merge);
                        if (merge) {
                            // it will take a call to GitHub to get the merge commit, so let the event receiver poll
                            result.put(head, null);
                        } else {
                            // Give the event receiver the data we have so they can revalidate
                            result.put(head, new PullRequestSCMRevision(
                                    head,
                                    ghPullRequest.getBase().getSha(),
                                    ghPullRequest.getHead().getSha()
                            ));
                        }
                    }
                }
            }
            if (!fork && (hasPR ? src.getBuildOriginBranchWithPR() : src.getBuildOriginBranch())) {
                final String branchName = ghPullRequest.getHead().getRef();
                if (!src.isExcluded(branchName)) {
                    SCMHead head = new BranchSCMHead(branchName);
                    SCMRevision hash =
                            new AbstractGitSCMSource.SCMRevisionImpl(head, ghPullRequest.getHead().getSha());
                    result.put(head, hash);
                }
            }

            return result;
        }

        @Override
        public boolean isMatch(@NonNull SCM scm) {
            return false;
        }
    }
}
