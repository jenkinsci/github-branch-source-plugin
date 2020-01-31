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
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.SCMSourceObserver;
import jenkins.scm.api.trait.SCMNavigatorContext;

/**
 * The {@link SCMNavigatorContext} for GitHub.
 *
 * @since 2.2.0
 */
public class GitHubSCMNavigatorContext extends SCMNavigatorContext<GitHubSCMNavigatorContext, GitHubSCMNavigatorRequest> {

    /**
     * The team name of the repositories to navigate.
     */
    private String teamSlug = "";

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public GitHubSCMNavigatorRequest newRequest(@NonNull SCMNavigator navigator, @NonNull SCMSourceObserver observer) {
        return new GitHubSCMNavigatorRequest(navigator, this, observer);
    }

    /**
     * Sets the name of the team who's repositories will be navigated.
     */
    void setTeamSlug(String teamSlug) {
        this.teamSlug = teamSlug;
    }

    /**
     * Gets the name of the team who's repositories will be navigated.
     * @return teamSlug
     */
    public String getTeamSlug() {
        return teamSlug;
    }
}
