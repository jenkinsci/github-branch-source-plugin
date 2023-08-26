/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
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

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.SCMSourceObserver;
import jenkins.scm.api.trait.SCMNavigatorContext;

/**
 * The {@link SCMNavigatorContext} for GitHub.
 *
 * @since 2.2.0
 */
public class GitHubSCMNavigatorContext
        extends SCMNavigatorContext<GitHubSCMNavigatorContext, GitHubSCMNavigatorRequest> {

    /** The team name of the repositories to navigate. */
    private String teamSlug = "";

    /** The topic which the repositories must have. */
    private List<String> topics = new ArrayList<>();

    /** If true, archived repositories will be ignored. */
    private boolean excludeArchivedRepositories;

    /** If true, public repositories will be ignored. */
    private boolean excludePublicRepositories;

    /** If true, private repositories will be ignored. */
    private boolean excludePrivateRepositories;

    /** If true, forked repositories will be ignored. */
    private boolean excludeForkedRepositories;

    /** {@inheritDoc} */
    @NonNull
    @Override
    public GitHubSCMNavigatorRequest newRequest(@NonNull SCMNavigator navigator, @NonNull SCMSourceObserver observer) {
        return new GitHubSCMNavigatorRequest(navigator, this, observer);
    }

    /** Sets the name of the team who's repositories will be navigated. */
    void setTeamSlug(String teamSlug) {
        this.teamSlug = teamSlug;
    }

    /**
     * Gets the name of the team who's repositories will be navigated.
     *
     * @return teamSlug
     */
    public String getTeamSlug() {
        return teamSlug;
    }

    /** Sets the topics which the repositories must have. */
    public void setTopics(List<String> topics) {
        this.topics = topics;
    }

    /**
     * Gets the topics which the repositories must have.
     *
     * @return topics
     */
    public List<String> getTopics() {
        return topics;
    }

    /** @return True if archived repositories should be ignored, false if they should be included. */
    public boolean isExcludeArchivedRepositories() {
        return excludeArchivedRepositories;
    }

    /** @return True if public repositories should be ignored, false if they should be included. */
    public boolean isExcludePublicRepositories() {
        return excludePublicRepositories;
    }

    /** @return True if private repositories should be ignored, false if they should be included. */
    public boolean isExcludePrivateRepositories() {
        return excludePrivateRepositories;
    }

    /** @return True if forked repositories should be ignored, false if they should be included. */
    public boolean isExcludeForkedRepositories() {
        return excludeForkedRepositories;
    }

    /** @param excludeArchivedRepositories Set true to exclude archived repositories */
    public void setExcludeArchivedRepositories(boolean excludeArchivedRepositories) {
        this.excludeArchivedRepositories = excludeArchivedRepositories;
    }

    /** @param excludePublicRepositories Set true to exclude public repositories */
    public void setExcludePublicRepositories(boolean excludePublicRepositories) {
        this.excludePublicRepositories = excludePublicRepositories;
    }

    /** @param excludePrivateRepositories Set true to exclude private repositories */
    public void setExcludePrivateRepositories(boolean excludePrivateRepositories) {
        this.excludePrivateRepositories = excludePrivateRepositories;
    }

    /** @param excludeForkedRepositories Set true to exclude archived repositories */
    public void setExcludeForkedRepositories(boolean excludeForkedRepositories) {
        this.excludeForkedRepositories = excludeForkedRepositories;
    }
}
