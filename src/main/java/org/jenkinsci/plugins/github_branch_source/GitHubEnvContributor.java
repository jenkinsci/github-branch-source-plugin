/*
 * The MIT License
 *
 * Copyright 2019 Tim Jacomb
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

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.EnvironmentContributor;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.TaskListener;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import jenkins.branch.BranchProjectFactory;
import jenkins.branch.MultiBranchProject;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.scm.api.SCMRevision;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Extension(optional = true)
@Restricted(NoExternalUse.class)
public class GitHubEnvContributor extends EnvironmentContributor {

    private static final String CHANGE_SOURCE_COMMIT_ID = "CHANGE_SOURCE_COMMIT_ID";
    private static final String CHANGE_MERGE_COMMIT_ID = "CHANGE_MERGE_COMMIT_ID";
    private static final String CHANGE_BASE_COMMIT_ID = "CHANGE_BASE_COMMIT_ID";
    private static final String CHANGE_BRANCH = "CHANGE_BRANCH";

    @SuppressWarnings("unchecked")
    public void buildEnvironmentFor(Job job, @Nonnull EnvVars envs, @Nonnull TaskListener listener) {
        ItemGroup parent = job.getParent();
        if (parent instanceof MultiBranchProject) {
            BranchProjectFactory projectFactory = ((MultiBranchProject) parent).getProjectFactory();
            SCMRevision revision = projectFactory.getRevision(job);

            Map<String, String> envValues = new HashMap<>();
            if (revision instanceof PullRequestSCMRevision) {
                PullRequestSCMRevision pullRequestSCMRevision = (PullRequestSCMRevision) revision;

                envValues.put(CHANGE_SOURCE_COMMIT_ID, pullRequestSCMRevision.getPullHash());
                envValues.put(CHANGE_MERGE_COMMIT_ID, pullRequestSCMRevision.getMergeHash());
                envValues.put(CHANGE_BASE_COMMIT_ID, pullRequestSCMRevision.getBaseHash());


            } else if (revision instanceof AbstractGitSCMSource.SCMRevisionImpl) {
                AbstractGitSCMSource.SCMRevisionImpl gitRevision = (AbstractGitSCMSource.SCMRevisionImpl) revision;

                envValues.put(CHANGE_SOURCE_COMMIT_ID, gitRevision.getHash());
                envValues.put(CHANGE_BRANCH, revision.getHead().getName());

            } else {
                throw new IllegalArgumentException("did not recognize " + revision);
            }
            envs.putAll(envValues);
        }
    }

}