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
import hudson.util.FormValidation;
import java.io.IOException;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadCategory;
import jenkins.scm.api.SCMHeadOrigin;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.mixin.TagSCMHead;
import jenkins.scm.api.trait.SCMHeadAuthority;
import jenkins.scm.api.trait.SCMHeadAuthorityDescriptor;
import jenkins.scm.api.trait.SCMHeadFilter;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceRequest;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import jenkins.scm.impl.trait.Discovery;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.github.GHBranch;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * A {@link Discovery} trait for GitHub that will discover branches with a certain age on the repository.
 *
 * @since 2.6.0
 */
public class BranchOrTagAgeDiscoveryTrait extends SCMSourceTrait {
    private static final Logger LOGGER = Logger.getLogger(BranchOrTagAgeDiscoveryTrait.class.getName());
    
    /**
     * The maximum age (in days) of branches or tags.
     */
    private int maxDaysOld = 0;

    /**
     * Constructor for stapler.
     *
     * @param maxDaysOld the maximum amount of days a branch or tag can be old.
     */
    @DataBoundConstructor
    public BranchOrTagAgeDiscoveryTrait(int maxDaysOld) {
        this.maxDaysOld = maxDaysOld;
    }

    /**
     * Returns the maximum days a branch or tag can be old.
     *
     * @return the maximum days a branch or tag can be old.
     */
    public int getMaxDaysOld() {
        return maxDaysOld;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void decorateContext(SCMSourceContext<?, ?> context) {
        GitHubSCMSourceContext ctx = (GitHubSCMSourceContext) context;
        ctx.withAuthority(new BranchOrTagAgeSCMHeadAuthority());
        ctx.withFilter(new OnlyHeadCommitsNewerThanSCMHeadFilter(maxDaysOld));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean includeCategory(@NonNull SCMHeadCategory category) {
        return category.isUncategorized();
    }

    /**
     * Our descriptor.
     */
    @Symbol("gitHubBranchOrTagAgeDiscovery")
    @Extension
    @Discovery
    public static class DescriptorImpl extends SCMSourceTraitDescriptor {

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.BranchOrTagAgeDiscoveryTrait_displayName();
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
        
        @Restricted(NoExternalUse.class)
        public FormValidation doCheckMaxDaysOld(@QueryParameter int maxDaysOld) {
            if (maxDaysOld <= 0) {
                return FormValidation.warning("Setting maximum age to 0 or negative turns off this filter.");
            }
            
            return FormValidation.ok();
        }
    }

    /**
     * Trusts branches from the origin repository.
     */
    public static class BranchOrTagAgeSCMHeadAuthority extends SCMHeadAuthority<SCMSourceRequest, SCMHead, SCMRevision> {
        /**
         * {@inheritDoc}
         */
        @Override
        protected boolean checkTrusted(@NonNull SCMSourceRequest request, @NonNull SCMHead head) {
            return true;
        }
            
        /**
         * Out descriptor.
         */
        @Symbol("gitHubBranchOrTagAgeHeadAuthority")
        @Extension
        public static class DescriptorImpl extends SCMHeadAuthorityDescriptor {
            /**
             * {@inheritDoc}
             */
            @Override
            public String getDisplayName() {
                return Messages.BranchOrTagAgeDiscoveryTrait_authorityDisplayName();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean isApplicableToOrigin(@NonNull Class<? extends SCMHeadOrigin> originClass) {
                return SCMHeadOrigin.Default.class.isAssignableFrom(originClass);
            }
        }
    }

    /**
     * Filter that excludes branches and tags that are older than maxDaysOld.
     */
    public static class OnlyHeadCommitsNewerThanSCMHeadFilter extends SCMHeadFilter {
        
        /**
         * The maximum age (in days) of branches or tags.
         */
        private int maxDaysOld = 0;
        
        /**
         * The branches that are needed in test-purposes.
         */
        private Iterable<GHBranch> branches = null;
        
        /**
         * Constructor.
         *
         * @param maxDaysOld the maximum amount of days a branch or tag can be old.
         */
        public OnlyHeadCommitsNewerThanSCMHeadFilter(int maxDaysOld) {
            this.maxDaysOld = maxDaysOld;
        }
        
        /**
         * Constructor for test-purposes.
         *
         * @param maxDaysOld the maximum amount of days a branch or tag can be old.
         */
        public OnlyHeadCommitsNewerThanSCMHeadFilter(int maxDaysOld, Iterable<GHBranch> branches) {
            this.maxDaysOld = maxDaysOld;
            this.branches = branches;
        }
        
        /**
         * Returns the maximum days a branch or tag can be old.
         *
         * @return the maximum days a branch or tag can be old.
         */
        public int getMaxDaysOld() {
            return maxDaysOld;
        }
        
        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isExcluded(@NonNull SCMSourceRequest request, @NonNull SCMHead head) {
            long lastModified = 0;
            try {
                if (head instanceof BranchSCMHead && request instanceof GitHubSCMSourceRequest) {
                    Iterable<GHBranch> ghBranches = null;
                    if (this.branches == null) { 
                        ghBranches = ((GitHubSCMSourceRequest) request).getBranches();   
                    } else {
                        ghBranches = branches;
                    } 
                    for (GHBranch b : ghBranches) {
                        if (b.getName().equals(head.getName())) {
                            lastModified = b.getOwner().getCommit(b.getSHA1()).getCommitDate().getTime();
                        }
                    }
                } else if (head instanceof TagSCMHead && request instanceof GitHubSCMSourceRequest) {
                    lastModified = ((TagSCMHead)head).getTimestamp();
                }

            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Could not get commit date of latest commit in branch {0}",
                        new Object[]{head.getName()}
                        );
                return false;
            }

            long daysOld = TimeUnit.MILLISECONDS.toDays(new Date().getTime() - lastModified);
            if (lastModified != 0 && 
                    maxDaysOld > 0 && 
                    daysOld > maxDaysOld) {
                return true;
            }               

            return false;
        }
    }
}
