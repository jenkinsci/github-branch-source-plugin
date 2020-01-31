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

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.TopLevelItem;
import hudson.model.View;
import hudson.views.ViewJobFilter;
import java.util.List;
import jenkins.scm.api.SCMHead;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * A {@link ViewJobFilter} that matches {@link BranchSCMHead} based branches.
 *
 * @since 2.0.0
 */
public class GitHubBranchFilter extends ViewJobFilter {
    /**
     * Our constructor.
     */
    @DataBoundConstructor
    public GitHubBranchFilter() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<TopLevelItem> filter(List<TopLevelItem> added, List<TopLevelItem> all, View filteringView) {
        for (TopLevelItem item:all) {
            if (added.contains(item)) {
                continue;
            }
            if (SCMHead.HeadByItem.findHead(item) instanceof BranchSCMHead) {
                added.add(item);
            }
        }
        return added;
    }

    /**
     * Our descriptor.
     */
    @Extension(optional = true)
    public static class DescriptorImpl extends Descriptor<ViewJobFilter> {

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.GitHubBranchFilter_DisplayName();
        }
    }

}
