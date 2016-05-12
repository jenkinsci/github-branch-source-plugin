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

package org.jenkinsci.plugins.github_pr_branch_source;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.net.URL;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.actions.ChangeRequestAction;
import org.kohsuke.github.GHPullRequest;

/**
 * Metadata about a {@link PullRequestSCMHead}.
 */
final class PullRequestAction extends ChangeRequestAction {

    private static final long serialVersionUID = 1L;

    private final int number;
    private final URL url;
    private final String title;
    private final String userLogin;
    private final String baseRef;

    PullRequestAction(GHPullRequest pr) {
        number = pr.getNumber();
        url = pr.getHtmlUrl();
        title = pr.getTitle();
        userLogin = pr.getUser().getLogin();
        baseRef = pr.getBase().getRef();
    }

    @NonNull
    @Override
    public String getId() {
        return Integer.toString(number);
    }

    @NonNull
    @Override
    public URL getURL() {
        return url;
    }

    @NonNull
    @Override
    public String getTitle() {
        return title;
    }

    @NonNull
    @Override
    public String getAuthor() {
        return userLogin;
    }

    // not currently implementing authorDisplayName or authorEmail since these are another round-trip in current GH API

    @NonNull
    @Override
    public SCMHead getTarget() {
        return new SCMHead(baseRef);
    }

}
