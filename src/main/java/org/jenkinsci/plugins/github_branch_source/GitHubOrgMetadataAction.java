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
import java.util.Objects;
import jenkins.scm.api.metadata.AvatarMetadataAction;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.github.GHUser;
import org.kohsuke.stapler.Stapler;

/**
 * Invisible {@link AvatarMetadataAction} property that retains information about GitHub
 * organization.
 *
 * @author Kohsuke Kawaguchi
 */
public class GitHubOrgMetadataAction extends AvatarMetadataAction {
    @CheckForNull
    private final String avatar;

    public GitHubOrgMetadataAction(@NonNull GHUser org) throws IOException {
        this(org.getAvatarUrl());
    }

    public GitHubOrgMetadataAction(@CheckForNull String avatar) {
        this.avatar = Util.fixEmpty(avatar);
    }

    public GitHubOrgMetadataAction(@NonNull GitHubOrgMetadataAction that) {
        this(that.getAvatar());
    }

    private Object readResolve() throws ObjectStreamException {
        if (avatar != null && StringUtils.isBlank(avatar)) return new GitHubOrgMetadataAction(this);
        return this;
    }

    @CheckForNull
    public String getAvatar() {
        return Util.fixEmpty(avatar);
    }

    /** {@inheritDoc} */
    @Override
    public String getAvatarImageOf(String size) {
        if (avatar == null) {
            // fall back to the generic github org icon
            String image = avatarIconClassNameImageOf(getAvatarIconClassName(), size);
            return image != null
                    ? image
                    : (Stapler.getCurrentRequest().getContextPath()
                            + Hudson.RESOURCE_PATH
                            + "/plugin/github-branch-source/images/"
                            + "/github-logo.svg");
        } else {
            String[] xy = size.split("x");
            if (xy.length == 0) return avatar;
            if (avatar.contains("?")) return avatar + "&s=" + xy[0];
            else return avatar + "?s=" + xy[0];
        }
    }

    /** {@inheritDoc} */
    @Override
    public String getAvatarIconClassName() {
        return avatar == null ? "icon-github-logo" : null;
    }

    /** {@inheritDoc} */
    @Override
    public String getAvatarDescription() {
        return Messages.GitHubOrgMetadataAction_IconDescription();
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        GitHubOrgMetadataAction that = (GitHubOrgMetadataAction) o;

        return Objects.equals(avatar, that.avatar);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return (avatar != null ? avatar.hashCode() : 0);
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "GitHubOrgMetadataAction{" + ", avatar='" + avatar + '\'' + "}";
    }
}
