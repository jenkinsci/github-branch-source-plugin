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

import hudson.model.InvisibleAction;
import java.io.IOException;
import java.net.URL;
import jenkins.branch.OrganizationFolder;
import org.kohsuke.github.GHUser;

/**
 * Invisible {@link OrganizationFolder} property that
 * retains information about GitHub organization.
 *
 * @author Kohsuke Kawaguchi
 */
public class GitHubOrgAction extends InvisibleAction {
    private final URL url;
    private final String name;
    private final String avatar;

    public GitHubOrgAction(GHUser org) throws IOException {
        this(org.getHtmlUrl(), org.getName(), org.getAvatarUrl());
    }

    public GitHubOrgAction(URL url, String name, String avatar) {
        this.url = url;
        this.name = name;
        this.avatar = avatar;
    }

    public GitHubOrgAction(GitHubOrgAction that) {
        this(that.getUrl(), that.getName(), that.getAvatar());
    }

    public URL getUrl() {
        return url;
    }

    public String getName() {
        return name;
    }

    public String getAvatar() {
        return avatar;
    }
}
