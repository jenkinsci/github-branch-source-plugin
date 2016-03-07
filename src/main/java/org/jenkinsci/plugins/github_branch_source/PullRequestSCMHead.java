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

import jenkins.scm.api.SCMHead;

/**
 * Head corresponding to a pull request.
 * Named like {@code PR-123}.
 */
public final class PullRequestSCMHead extends SCMHead {

    private static final String PR_BRANCH_PREFIX = "PR-";

    private static final long serialVersionUID = 1;

    private String user;

    private String title;

    public PullRequestSCMHead(int number, String user, String title) {
        super(PR_BRANCH_PREFIX + number);
        this.user = user;
        this.title = title;
    }

    public int getNumber() {
        return Integer.parseInt(getName().substring(PR_BRANCH_PREFIX.length()));
    }

    /**
     * @return The github login name of the user who created the PR.
     */
    public String getUser() {
        return user;
    }

    /**
     * @return Title of the PR.
     */
    public String getTitle() {
        return title;
    }
}
