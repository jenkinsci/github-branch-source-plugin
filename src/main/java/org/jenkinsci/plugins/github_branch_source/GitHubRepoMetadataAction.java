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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import java.io.ObjectStreamException;
import java.net.URL;
import jenkins.branch.MetadataAction;
import jenkins.branch.MultiBranchProject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.github.GHRepository;

/**
 * Invisible property on {@link MultiBranchProject}
 * that retains information about GitHub repository.
 *
 * @author Kohsuke Kawaguchi
 */
public class GitHubRepoMetadataAction extends MetadataAction {
    @NonNull
    private final URL url;
    @CheckForNull
    private final String description;
    @CheckForNull
    private final String homepage;

    public GitHubRepoMetadataAction(@NonNull GHRepository repo) {
        this(repo.getHtmlUrl(), repo.getDescription(), repo.getHomepage());
    }

    public GitHubRepoMetadataAction(@NonNull URL url, @CheckForNull String description, @CheckForNull String homepage) {
        this.url = url;
        this.description = Util.fixEmpty(description);
        this.homepage = Util.fixEmpty(homepage);
    }

    public GitHubRepoMetadataAction(GitHubRepoMetadataAction that) {
        this(that.getUrl(), that.getObjectDescription(), that.getObjectUrl());
    }

    private Object readResolve() throws ObjectStreamException {
        if ((description != null && StringUtils.isBlank(description))
                || (homepage != null && StringUtils.isBlank(homepage)))
            return new GitHubRepoMetadataAction(this);
        return this;
    }

    @NonNull
    public URL getUrl() {
        return url;
    }

    /**
     * {@inheritDoc}
     */
    @CheckForNull
    @Override
    public String getObjectDescription() {
        return description;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getObjectUrl() {
        return homepage;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getFolderIconClassName() {
        return "icon-github-repo";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getFolderIconDescription() {
        return Messages.GitHubRepoMetadataAction_IconDescription();
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

        GitHubRepoMetadataAction that = (GitHubRepoMetadataAction) o;

        if (!url.equals(that.url)) {
            return false;
        }
        if (description != null ? !description.equals(that.description) : that.description != null) {
            return false;
        }
        return homepage != null ? homepage.equals(that.homepage) : that.homepage == null;

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        int result = url.hashCode();
        result = 31 * result + (description != null ? description.hashCode() : 0);
        result = 31 * result + (homepage != null ? homepage.hashCode() : 0);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "GitHubRepoMetadataAction{" +
                "url=" + url +
                ", description='" + description + '\'' +
                ", homepage='" + homepage + '\'' +
                "}";
    }
}
