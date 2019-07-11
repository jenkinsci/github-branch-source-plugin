/*
 * The MIT License
 *
 * Copyright 2015-2017 CloudBees, Inc.
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
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.impl.trait.WildcardSCMHeadFilterTrait;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class GitHubSCMSource extends AbstractGitHubSCMSource {

    //////////////////////////////////////////////////////////////////////
    // Legacy Configuration fields
    //////////////////////////////////////////////////////////////////////

    /**
     * Legacy field.
     */
    @Deprecated
    private transient String scanCredentialsId;
    /**
     * Legacy field.
     */
    @Deprecated
    private transient String checkoutCredentialsId;
    /**
     * Legacy field.
     */
    @Deprecated
    private String includes;
    /**
     * Legacy field.
     */
    @Deprecated
    private String excludes;
    /**
     * Legacy field.
     */
    @Deprecated
    private transient Boolean buildOriginBranch;
    /**
     * Legacy field.
     */
    @Deprecated
    private transient Boolean buildOriginBranchWithPR;
    /**
     * Legacy field.
     */
    @Deprecated
    private transient Boolean buildOriginPRMerge;
    /**
     * Legacy field.
     */
    @Deprecated
    private transient Boolean buildOriginPRHead;
    /**
     * Legacy field.
     */
    @Deprecated
    private transient Boolean buildForkPRMerge;
    /**
     * Legacy field.
     */
    @Deprecated
    private transient Boolean buildForkPRHead;


    /**
     * Constructor, defaults to {@link #GITHUB_URL} as the end-point, and anonymous access, does not default any
     * {@link SCMSourceTrait} behaviours.
     *
     * @param repoOwner the repository owner.
     * @param repository the repository name.
     * @since 2.2.0
     */
    @DataBoundConstructor
    public GitHubSCMSource(@NonNull String repoOwner, @NonNull String repository) {
        super(repoOwner, repository);
    }

    /**
     * Legacy constructor.
     * @param id the source id.
     * @param apiUri the GitHub endpoint.
     * @param checkoutCredentialsId the checkout credentials id or {@link DescriptorImpl#SAME} or
     * {@link DescriptorImpl#ANONYMOUS}.
     * @param scanCredentialsId the scan credentials id or {@code null}.
     * @param repoOwner the repository owner.
     * @param repository the repository name.
     */
    @Deprecated
    public GitHubSCMSource(@CheckForNull String id, @CheckForNull String apiUri, @NonNull String checkoutCredentialsId,
                           @CheckForNull String scanCredentialsId, @NonNull String repoOwner,
                           @NonNull String repository) {
        super(id, apiUri, checkoutCredentialsId, scanCredentialsId, repoOwner, repository);
    }

    @Symbol("github")
    @Extension
    public static class DescriptorImpl extends DescriptorAbstract {
        @Override
        public String getDisplayName() {
            return Messages.GitHubSCMSource_DisplayName();
        }

    }

    /**
     * Use defaults for old settings.
     */
    @SuppressWarnings("ConstantConditions")
    @SuppressFBWarnings(value="RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE", justification="Only non-null after we set them here!")
    private Object readResolve() {
        if (scanCredentialsId != null) {
            credentialsId = scanCredentialsId;
        }
        if (pullRequestMetadataCache == null) {
            pullRequestMetadataCache = new ConcurrentHashMap<>();
        }
        if (pullRequestContributorCache == null) {
            pullRequestContributorCache = new ConcurrentHashMap<>();
        }
        if (traits == null) {
            boolean buildOriginBranch = this.buildOriginBranch == null || this.buildOriginBranch;
            boolean buildOriginBranchWithPR = this.buildOriginBranchWithPR == null || this.buildOriginBranchWithPR;
            boolean buildOriginPRMerge = this.buildOriginPRMerge != null && this.buildOriginPRMerge;
            boolean buildOriginPRHead = this.buildOriginPRHead != null && this.buildOriginPRHead;
            boolean buildForkPRMerge = this.buildForkPRMerge == null || this.buildForkPRMerge;
            boolean buildForkPRHead = this.buildForkPRHead != null && this.buildForkPRHead;
            List<SCMSourceTrait> traits = new ArrayList<>();
            if (buildOriginBranch || buildOriginBranchWithPR) {
                traits.add(new BranchDiscoveryTrait(buildOriginBranch, buildOriginBranchWithPR));
            }
            if (buildOriginPRMerge || buildOriginPRHead) {
                EnumSet<ChangeRequestCheckoutStrategy> s = EnumSet.noneOf(ChangeRequestCheckoutStrategy.class);
                if (buildOriginPRMerge) {
                    s.add(ChangeRequestCheckoutStrategy.MERGE);
                }
                if (buildOriginPRHead) {
                    s.add(ChangeRequestCheckoutStrategy.HEAD);
                }
                traits.add(new OriginPullRequestDiscoveryTrait(s));
            }
            if (buildForkPRMerge || buildForkPRHead) {
                EnumSet<ChangeRequestCheckoutStrategy> s = EnumSet.noneOf(ChangeRequestCheckoutStrategy.class);
                if (buildForkPRMerge) {
                    s.add(ChangeRequestCheckoutStrategy.MERGE);
                }
                if (buildForkPRHead) {
                    s.add(ChangeRequestCheckoutStrategy.HEAD);
                }
                traits.add(new ForkPullRequestDiscoveryTrait(s, new ForkPullRequestDiscoveryTrait.TrustPermission()));
            }
            if (!"*".equals(includes) || !"".equals(excludes)) {
                traits.add(new WildcardSCMHeadFilterTrait(includes, excludes));
            }
            if (checkoutCredentialsId != null
                    && !DescriptorAbstract.SAME.equals(checkoutCredentialsId)
                    && !checkoutCredentialsId.equals(scanCredentialsId)) {
                traits.add(new SSHCheckoutTrait(checkoutCredentialsId));
            }
            this.traits = traits;
        }
        if (!StringUtils.equals(apiUri, GitHubConfiguration.normalizeApiUri(apiUri))) {
            setApiUri(apiUri);
        }
        return this;
    }


}
