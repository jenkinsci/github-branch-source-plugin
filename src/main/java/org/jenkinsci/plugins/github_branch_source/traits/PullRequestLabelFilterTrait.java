/*
 * The MIT License
 *
 * Copyright (c) 2019, CloudBees, Inc.
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
package org.jenkinsci.plugins.github_branch_source.traits;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;

import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadCategory;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.trait.SCMHeadFilter;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceRequest;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import jenkins.scm.impl.ChangeRequestSCMHeadCategory;
import jenkins.scm.impl.trait.Selection;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.github_branch_source.GitHubSCMSource;
import org.jenkinsci.plugins.github_branch_source.GitHubSCMSourceContext;
import org.jenkinsci.plugins.github_branch_source.GitHubSCMSourceRequest;
import org.jenkinsci.plugins.github_branch_source.PullRequestSCMHead;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;

//TODO: add interface in SCM API?
/**
 * A {@code Filter} trait for GitHub that will filter out pull request matching the specified labels.
 *
 * @since TODO
 */
public class PullRequestLabelFilterTrait extends SCMSourceTrait {

    /**
     * Labels to be ignored.
     */
    Set<String> labelsToIgnore;

    /**
     * Constructor for stapler.
     *
     * @param labelsToIgnore Labels to ignore
     */
    @DataBoundConstructor
    public PullRequestLabelFilterTrait(@NonNull Set<String> labelsToIgnore) {
        this.labelsToIgnore = Collections.unmodifiableSet(labelsToIgnore);
    }

    @NonNull
    public Set<String> getLabelsToIgnore() {
        return labelsToIgnore;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void decorateContext(SCMSourceContext<?, ?> context) {
        GitHubSCMSourceContext ctx = (GitHubSCMSourceContext) context;
        ctx.wantForkPRs(true);
        ctx.wantOriginPRs(true);
        ctx.withFilter(new SCMHeadFilter() {
            @Override
            public boolean isExcluded(@NonNull SCMSourceRequest request, @NonNull SCMHead head) throws IOException, InterruptedException {
                if (request instanceof GitHubSCMSourceRequest && head instanceof PullRequestSCMHead) {
                    PullRequestSCMHead pr = (PullRequestSCMHead)head;
                    Set<String> labels = pr.getLabels();
                    for (String label : labelsToIgnore) {
                        if(labels.contains(label)) {
                            return true;
                        }
                    }
                    return false;
                }
                return false;
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean includeCategory(@NonNull SCMHeadCategory category) {
        return category instanceof ChangeRequestSCMHeadCategory;
    }

    /**
     * Our descriptor.
     */
    @Symbol("gitHubFilterPRsByLabels")
    @Extension
    @Selection
    public static class DescriptorImpl extends SCMSourceTraitDescriptor {

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.PullRequestLabelFilterTrait_displayName();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Class<? extends SCMSourceContext> getContextClass() {
            return GitHubSCMSourceContext.class;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Class<? extends SCMSource> getSourceClass() {
            return GitHubSCMSource.class;
        }
    }

}