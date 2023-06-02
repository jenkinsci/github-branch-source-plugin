/*
 * The MIT License
 *
 * Copyright (c) 2017 CloudBees, Inc.
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
 *
 */
package org.jenkinsci.plugins.github_branch_source;

import com.cloudbees.jenkins.GitHubRepositoryName;
import com.cloudbees.jenkins.GitHubRepositoryNameContributor;
import hudson.Extension;
import hudson.model.Item;
import java.util.Collection;
import jenkins.scm.api.SCMSourceOwner;

/**
 * Finds the repository name(s) associated with a {@link SCMSourceOwner}'s {@link GitHubSCMSource}s.
 *
 * @see GitHubRepositoryNameContributor#parseAssociatedNames(Item)
 * @see org.jenkinsci.plugins.github.webhook.WebhookManager#registerFor(Item)
 */
@Extension
public class GitHubSCMSourceRepositoryNameContributor extends GitHubRepositoryNameContributor {

    @Override
    public void parseAssociatedNames(Item item, Collection<GitHubRepositoryName> result) {
        if (item instanceof SCMSourceOwner) {
            SCMSourceOwner mp = (SCMSourceOwner) item;
            for (Object o : mp.getSCMSources()) {
                if (o instanceof GitHubSCMSource) {
                    GitHubSCMSource gitHubSCMSource = (GitHubSCMSource) o;
                    result.add(new GitHubRepositoryName(
                            RepositoryUriResolver.hostnameFromApiUri(gitHubSCMSource.getApiUri()),
                            gitHubSCMSource.getRepoOwner(),
                            gitHubSCMSource.getRepository()));
                }
            }
        }
    }
}
