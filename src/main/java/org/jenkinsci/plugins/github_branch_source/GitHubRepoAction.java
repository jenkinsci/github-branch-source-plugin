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

import hudson.Util;
import hudson.model.InvisibleAction;
import java.io.ObjectStreamException;
import java.net.URL;
import jenkins.branch.MultiBranchProject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.github.GHRepository;

/**
 * Invisible property on {@link MultiBranchProject}
 * that retains information about GitHub repository.
 *
 * @author Kohsuke Kawaguchi
 */
public class GitHubRepoAction extends InvisibleAction {
    private final URL url;
    private final String description;
    private final String homepage;

    public GitHubRepoAction(GHRepository repo) {
        this(repo.getHtmlUrl(), repo.getDescription(), repo.getHomepage());
    }

    public GitHubRepoAction(URL url, String description, String homepage) {
        this.url = url;
        this.description = Util.fixEmpty(description);
        this.homepage = Util.fixEmpty(homepage);
    }

    public GitHubRepoAction(GitHubRepoAction that) {
        this(that.getUrl(), that.getDescription(), that.getHomepage());
    }

    private Object readResolve() throws ObjectStreamException {
        if ((description != null && StringUtils.isBlank(description))
                || (homepage != null && StringUtils.isBlank(homepage)))
            return new GitHubRepoAction(this);
        return this;
    }

    public URL getUrl() {
        return url;
    }

    public String getDescription() {
        return description;
    }

    public String getHomepage() {
        return homepage;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        GitHubRepoAction that = (GitHubRepoAction) o;

        return getUrl() != null ? getUrl().toExternalForm().equals(that.getUrl().toExternalForm()) : that.getUrl() == null;

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return getUrl() != null ? getUrl().toExternalForm().hashCode() : 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "GitHubRepoAction{" +
                "url=" + url +
                ", description='" + description + '\'' +
                ", homepage='" + homepage + '\'' +
                "}";
    }
}
