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

import hudson.Extension;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import java.io.IOException;
import java.io.StringReader;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.google.common.collect.Sets.immutableEnumSet;
import static org.kohsuke.github.GHEvent.PULL_REQUEST;

/**
 * This subscriber manages {@link org.kohsuke.github.GHEvent} PULL_REQUEST.
 */
@Extension
public class PullRequestGHEventSubscriber extends AbstractGHEventSubscriber {
    private static final Logger LOGGER = Logger.getLogger(PullRequestGHEventSubscriber.class.getName());

    /**
     * @return set with only PULL_REQUEST event
     */
    @Override
    protected Set<GHEvent> events() {
        return immutableEnumSet(PULL_REQUEST);
    }

    private GHEventPayload.PullRequest getPullRequest(String payload, GitHub gh) throws IOException {
        return gh.parseEventPayload(new StringReader(payload), GHEventPayload.PullRequest.class);
    }

    protected boolean doUpdateFromEvent(String payload, JSONObject json, WorkflowMultiBranchProject owner, GitHubSCMSource source) {
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
            LOGGER.log(Level.SEVERE, "IOException during PR webhook update: " + e);
            return false;
        }

        String prHeadOwner = json.getJSONObject("pull_request").getJSONObject("head").getJSONObject("repo").getJSONObject("owner").getString("login");
        boolean fork = !source.getRepoOwner().equals(prHeadOwner); // ?

        String baseHash = pull.getBase().getSha();
        String headHash = pull.getHead().getSha();

        boolean found = false;

        for (boolean merge : new boolean[]{false, true}) {
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
}
