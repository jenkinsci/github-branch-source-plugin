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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Action;
import java.net.URL;
import org.jenkins.ui.icon.IconSpec;

/**
 * Link to GitHub
 *
 * @author Kohsuke Kawaguchi
 */
public class GitHubLink implements Action, IconSpec {
    /** Target of the hyperlink to take the user to. */
    @NonNull
    private final String url;

    public GitHubLink(@NonNull String url) {
        this.url = url;
    }

    public GitHubLink(URL url) {
        this(url.toExternalForm());
    }

    @NonNull
    public String getUrl() {
        return url;
    }

    @Override
    public String getIconClassName() {
        return "symbol-logo-github plugin-ionicons-api";
    }

    @Override
    public String getIconFileName() {
        return getIconClassName();
    }

    @Override
    public String getDisplayName() {
        return Messages.GitHubLink_DisplayName();
    }

    @Override
    public String getUrlName() {
        return url;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        GitHubLink that = (GitHubLink) o;

        return url.equals(that.url);
    }

    @Override
    public int hashCode() {
        return 31 * url.hashCode();
    }

    @Override
    public String toString() {
        return "GitHubLink{" + "url='" + url + '\'' + '}';
    }
}
