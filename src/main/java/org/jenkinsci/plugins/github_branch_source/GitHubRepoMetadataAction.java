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

import jenkins.scm.api.metadata.AvatarMetadataAction;

/**
 * Invisible property that retains information about GitHub repository.
 *
 * @author Kohsuke Kawaguchi
 */
public class GitHubRepoMetadataAction extends AvatarMetadataAction {

  /** {@inheritDoc} */
  @Override
  public String getAvatarIconClassName() {
    return "icon-github-repo";
  }

  /** {@inheritDoc} */
  @Override
  public String getAvatarDescription() {
    return Messages.GitHubRepoMetadataAction_IconDescription();
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
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    return 0;
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return "GitHubRepoMetadataAction{}";
  }
}
