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
import hudson.model.TaskListener;
import hudson.util.LogTaskListener;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSourceCriteria;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.google.common.collect.Sets.immutableEnumSet;
import static org.kohsuke.github.GHEvent.PUSH;

/**
 * This subscriber manages {@link GHEvent} PUSH.
 */
@Extension
public class PushGHEventSubscriber extends AbstractGHEventSubscriber {
    private static final Logger LOGGER = Logger.getLogger(PushGHEventSubscriber.class.getName());

    /**
     * @return set with only PUSH event
     */
    @Override
    protected Set<GHEvent> events() {
        return immutableEnumSet(PUSH);
    }

    protected boolean doUpdateFromEvent(String payload, JSONObject json, WorkflowMultiBranchProject owner, GitHubSCMSource source) {
        if (json.getBoolean("deleted")) {
            LOGGER.log(Level.INFO, "Skipping update on deleted ref push event");
            return true;
        }

        String msg = json.getJSONObject("head_commit").getString("message").toLowerCase();

        if (msg.contains("[skip ci]") || msg.contains("[ci skip]") || msg.contains("[skip jenkins]")) {
            LOGGER.log(Level.INFO, "Skipping build, because message contains skip string like [skip ci]");
            return false;
        }

        String name = json.getString("ref");

        if (!name.startsWith("refs/heads/")) {
            LOGGER.log(Level.FINE, "Skipping push for non-head ref: {0}", new Object[] { name });
            return false;
        }

        name = name.substring(11);
        String sha1 = json.getString("after");

        if (name.length() < 1 || sha1.length() != 40) {
            LOGGER.log(Level.INFO, "Malformed branch name or after sha1");
            return false;
        }

        GitHub github;
        GHRepository repository;

        try {
            github = source.getGitHub();
            repository = source.getRepository(github);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "IOException during push webhook update: {0}", new Object[] { e });
            return false;
        }

        Set<String> originBranchesWithPR = new HashSet<>(); // TODO
        TaskListener listener = new LogTaskListener(LOGGER, Level.INFO);

        SCMRevision revision;

        try {
            SCMSourceCriteria criteria = owner.getSCMSourceCriteria(source);
            revision = source.generateRevisionForBranch(name, sha1, repository, originBranchesWithPR, listener, criteria);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "IOException during push webhook generate revision: {0}", new Object[] { e });
            return false;
        }

        if (revision == null) {
            LOGGER.log(Level.INFO, "Skipping possible branch update for {0} to {1} because it failed validation", new Object[] { name, sha1 });
            return false;
        }

        LOGGER.log(Level.FINE, "About to schedule {0} build for {1} at revision {2}", new Object[] { owner.getName(), name, sha1 });
        return scheduleBuild(owner, revision, name);
    }
}
