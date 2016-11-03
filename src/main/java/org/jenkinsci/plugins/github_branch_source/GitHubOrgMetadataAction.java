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
import hudson.model.Hudson;
import java.io.IOException;
import java.io.ObjectStreamException;
import java.net.URL;
import jenkins.branch.MetadataAction;
import jenkins.branch.OrganizationFolder;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.github.GHUser;
import org.kohsuke.stapler.Stapler;

/**
 * Invisible {@link OrganizationFolder} property that
 * retains information about GitHub organization.
 *
 * @author Kohsuke Kawaguchi
 */
public class GitHubOrgMetadataAction extends MetadataAction {
    @NonNull
    private final URL url;
    @CheckForNull
    private final String name;
    @CheckForNull
    private final String avatar;

    public GitHubOrgMetadataAction(@NonNull GHUser org) throws IOException {
        this(org.getHtmlUrl(), org.getName(), org.getAvatarUrl());
    }

    public GitHubOrgMetadataAction(@NonNull URL url, @CheckForNull String name, @CheckForNull String avatar) {
        this.url = url;
        this.name = Util.fixEmpty(name);
        this.avatar = Util.fixEmpty(avatar);
    }

    public GitHubOrgMetadataAction(@NonNull GitHubOrgMetadataAction that) {
        this(that.getUrl(), that.getObjectDisplayName(), that.getAvatar());
    }

    private Object readResolve() throws ObjectStreamException {
        if ((name != null && StringUtils.isBlank(name))
                || (avatar != null && StringUtils.isBlank(avatar)))
            return new GitHubOrgMetadataAction(this);
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
    public String getObjectDisplayName() {
        return Util.fixEmpty(name);
    }

    @CheckForNull
    public String getAvatar() {
        return Util.fixEmpty(avatar);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getFolderIconImageOf(String size) {
        if (avatar == null) {
            // fall back to the generic github org icon
            String image = folderIconClassNameImageOf(getFolderIconClassName(), size);
            return image != null
                    ? image
                    : (Stapler.getCurrentRequest().getContextPath() + Hudson.RESOURCE_PATH
                            + "/plugin/github-branch-source/images/" + size + "/github-logo.png");
        } else {
            String[] xy = size.split("x");
            if (xy.length == 0) return avatar;
            if (avatar.contains("?")) return avatar + "&s=" + xy[0];
            else return avatar + "?s=" + xy[0];
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getFolderIconClassName() {
        return avatar == null ? "icon-github-logo" : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getFolderIconDescription() {
        return Messages.GitHubOrgMetadataAction_IconDescription();
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

        GitHubOrgMetadataAction that = (GitHubOrgMetadataAction) o;

        if (!url.equals(that.url)) {
            return false;
        }
        if (name != null ? !name.equals(that.name) : that.name != null) {
            return false;
        }
        return avatar != null ? avatar.equals(that.avatar) : that.avatar == null;

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        int result = url.hashCode();
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (avatar != null ? avatar.hashCode() : 0);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "GitHubOrgMetadataAction{" +
                "url=" + url +
                ", name='" + name + '\'' +
                ", avatar='" + avatar + '\'' +
                "}";
    }

}
