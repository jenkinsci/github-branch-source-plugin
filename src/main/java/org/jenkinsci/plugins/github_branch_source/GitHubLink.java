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
import jenkins.model.Jenkins;
import org.apache.commons.jelly.JellyContext;
import org.jenkins.ui.icon.Icon;
import org.jenkins.ui.icon.IconSet;
import org.jenkins.ui.icon.IconSpec;
import org.kohsuke.stapler.Stapler;

/**
 * Link to GitHub
 *
 * @author Kohsuke Kawaguchi
 */
public class GitHubLink implements Action, IconSpec {
  /** The icon class name to use. */
  @NonNull private final String iconClassName;

  /** Target of the hyperlink to take the user to. */
  @NonNull private final String url;

  public GitHubLink(@NonNull String iconClassName, @NonNull String url) {
    this.iconClassName = iconClassName;
    this.url = url;
  }

  public GitHubLink(String iconClassName, URL url) {
    this(iconClassName, url.toExternalForm());
  }

  @NonNull
  public String getUrl() {
    return url;
  }

  @Override
  public String getIconClassName() {
    return iconClassName;
  }

  @Override
  public String getIconFileName() {
    String iconClassName = getIconClassName();
    if (iconClassName != null) {
      Icon icon = IconSet.icons.getIconByClassSpec(iconClassName + " icon-md");
      if (icon != null) {
        JellyContext ctx = new JellyContext();
        ctx.setVariable(
            "resURL", Stapler.getCurrentRequest().getContextPath() + Jenkins.RESOURCE_PATH);
        return icon.getQualifiedUrl(ctx);
      }
    }
    return null;
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

    if (!iconClassName.equals(that.iconClassName)) {
      return false;
    }
    return url.equals(that.url);
  }

  @Override
  public int hashCode() {
    int result = iconClassName.hashCode();
    result = 31 * result + url.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "GitHubLink{" + "iconClassName='" + iconClassName + '\'' + ", url='" + url + '\'' + '}';
  }
}
