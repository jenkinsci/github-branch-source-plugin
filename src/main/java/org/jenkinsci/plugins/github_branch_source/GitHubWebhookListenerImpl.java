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

import com.cloudbees.jenkins.GitHubRepositoryName;
import com.cloudbees.jenkins.GitHubWebHook;
import hudson.Extension;
import hudson.security.ACL;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.api.SCMSourceOwners;

/**
 * This listener is registered only when {@link org.kohsuke.github.GHEvent} PUSH is received.
 */
@Extension
public class GitHubWebhookListenerImpl extends GitHubWebHook.Listener {

    @Override
    public void onPushRepositoryChanged(String pusherName, final GitHubRepositoryName changedRepository) {
        ACL.impersonate(ACL.SYSTEM, new Runnable() {
            @Override public void run() {
                for (final SCMSourceOwner owner : SCMSourceOwners.all()) {
                    for (SCMSource source : owner.getSCMSources()) {
                        if (source instanceof AbstractGitHubSCMSource) {
                            AbstractGitHubSCMSource gitHubSCMSource = (AbstractGitHubSCMSource) source;
                            if (gitHubSCMSource.getRepoOwner().equals(changedRepository.getUserName()) &&
                                    gitHubSCMSource.getRepository().equals(changedRepository.getRepositoryName())) {
                                owner.onSCMSourceUpdated(gitHubSCMSource);
                            }
                        }
                    }
                }
            }
        });
    }
}
