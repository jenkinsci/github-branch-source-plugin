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

package org.jenkinsci.plugins.github.multibranch;

import hudson.Extension;
import hudson.console.HyperlinkNote;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import org.eclipse.jgit.transport.RefSpec;
import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHRepository;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Loads branches from the GitHub origin repository.
 */
public class OriginGitHubSCMSource extends AbstractGitHubSCMSource {

    @DataBoundConstructor public OriginGitHubSCMSource(String id, String apiUri, String checkoutCredentialsId, String
            scanCredentialsId, String repoOwner, String repository) {
        super(id, apiUri, checkoutCredentialsId, scanCredentialsId, repoOwner, repository);
    }

    @Override
    protected List<RefSpec> getRefSpecs() {
        return Collections.singletonList(new RefSpec("+refs/heads/*:refs/remotes/origin/*"));
    }

    @Override protected void doRetrieve(SCMHeadObserver observer, TaskListener listener, GHRepository repo) throws IOException, InterruptedException {
        listener.getLogger().format("%n  Getting remote branches...%n");
        SCMSourceCriteria branchCriteria = getCriteria();

        int branches = 0;
        for (Map.Entry<String,GHBranch> entry : repo.getBranches().entrySet()) {
            final String branchName = entry.getKey();
            if (isExcluded(branchName)) {
                continue;
            }
            listener.getLogger().format("%n    Checking branch %s%n", HyperlinkNote.encodeTo(repo.getHtmlUrl().toString() + "/tree/" + branchName, branchName));
            if (branchCriteria != null) {
                SCMSourceCriteria.Probe probe = getProbe(branchName, "branch", "refs/heads/" + branchName, repo, listener);
                if (branchCriteria.isHead(probe, listener)) {
                    listener.getLogger().format("    Met criteria%n%n");
                } else {
                    listener.getLogger().format("    Does not meet criteria%n%n");
                    continue;
                }
            }
            SCMHead head = new SCMHead(branchName);
            SCMRevision hash = new SCMRevisionImpl(head, entry.getValue().getSHA1());
            observer.observe(head, hash);
            if (!observer.isObserving()) {
                return;
            }
            branches++;
        }

        listener.getLogger().format("  %d branches were processed%n", branches);
    }

    @Extension public static class DescriptorImpl extends AbstractGitHubSCMSourceDescriptor {

        @Override public String getDisplayName() {
            return "GitHub";
        }

    }

    @Extension public static class OriginGitHubSCMSourceAddition implements GitHubSCMNavigator.GitHubSCMSourceAddition {
        @Override public List<? extends SCMSource> sourcesFor(String checkoutCredentialsId, String scanCredentialsId, String repoOwner, String repository) {
            return Collections.singletonList(new OriginGitHubSCMSource(null, null, checkoutCredentialsId, scanCredentialsId, repoOwner, repository));
        }
    }

}
