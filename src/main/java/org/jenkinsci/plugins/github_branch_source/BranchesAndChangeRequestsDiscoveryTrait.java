/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
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
import hudson.Extension;
import jenkins.scm.api.SCMHeadCategory;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import jenkins.scm.impl.trait.Discovery;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.github_branch_source.BranchDiscoveryTrait.BranchSCMHeadAuthority;
import org.jenkinsci.plugins.github_branch_source.OriginPullRequestDiscoveryTrait.OriginChangeRequestSCMHeadAuthority;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.EnumSet;

/**
 * A {@link Discovery} trait for GitHub that will discover branches on the repository.
 *
 * @since 2.2.0
 */
public class BranchesAndChangeRequestsDiscoveryTrait extends SCMSourceTrait {
    /**
     * Constructor for stapler.
     */
    @DataBoundConstructor
    public BranchesAndChangeRequestsDiscoveryTrait() {}

    /** {@inheritDoc} */
    @Override
    protected void decorateContext(SCMSourceContext<?, ?> context) {
        GitHubSCMSourceContext ctx = (GitHubSCMSourceContext) context;
        ctx.wantBranches(true);
        ctx.withFilter(new BranchDiscoveryTrait.ExcludeOriginPRBranchesSCMHeadFilter());
        ctx.withAuthority(new BranchSCMHeadAuthority());

        ctx.wantOriginPRs(true);
        ctx.wantForkPRs(false); // TODO: make optional?
        ctx.withAuthority(new OriginChangeRequestSCMHeadAuthority());
        ctx.withOriginPRStrategies(EnumSet.of(ChangeRequestCheckoutStrategy.HEAD));
    }

    /** {@inheritDoc} */
    @Override
    public boolean includeCategory(@NonNull SCMHeadCategory category) {
        return category instanceof BranchesAndChangeRequestsCategory;
    }

    /** Our descriptor. */
    @Symbol("gitHubBranchesAndChangeRequestsDiscovery")
    @Extension
    @Discovery
    public static class DescriptorImpl extends SCMSourceTraitDescriptor {

        /** {@inheritDoc} */
        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.BranchesAndChangeRequestsDiscoveryTrait_displayName();
        }

        /** {@inheritDoc} */
        @Override
        public Class<? extends SCMSourceContext<?, ?>> getContextClass() {
            return GitHubSCMSourceContext.class;
        }

        /** {@inheritDoc} */
        @Override
        public Class<? extends SCMSource> getSourceClass() {
            return GitHubSCMSource.class;
        }
    }
}
