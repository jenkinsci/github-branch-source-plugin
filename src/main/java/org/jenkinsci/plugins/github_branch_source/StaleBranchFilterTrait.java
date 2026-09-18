package org.jenkinsci.plugins.github_branch_source;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.util.FormValidation;
import java.io.IOException;
import java.util.Date;
import java.util.regex.Pattern;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.trait.SCMHeadFilter;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceRequest;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import jenkins.scm.impl.trait.Selection;
import org.jenkinsci.Symbol;
import org.kohsuke.github.GHBranch;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

/**
 * Trait that filters out branches whose last commit is older than a specified number of days.
 * Stale branches are excluded from Jenkins indexing so they no longer appear as jobs.
 *
 * <p>Optional {@code includeRegex} scopes the filter to only matching branch names.
 * Optional {@code excludeRegex} exempts matching branch names from stale filtering entirely.
 */
public class StaleBranchFilterTrait extends SCMSourceTrait {

    /** Number of days after which a branch with no new commits is considered stale. */
    private final int daysStale;

    /**
     * If set, stale filtering is only applied to branches whose name matches this regex.
     * Branches that do not match are never excluded.
     */
    @CheckForNull
    private String includeRegex;

    /**
     * If set, branches whose name matches this regex are always kept, regardless of age.
     */
    @CheckForNull
    private String excludeRegex;

    @DataBoundConstructor
    public StaleBranchFilterTrait(int daysStale) {
        this.daysStale = Math.max(1, daysStale);
    }

    public int getDaysStale() {
        return daysStale;
    }

    @CheckForNull
    public String getIncludeRegex() {
        return includeRegex;
    }

    @DataBoundSetter
    public void setIncludeRegex(@CheckForNull String includeRegex) {
        this.includeRegex = Util.fixEmptyAndTrim(includeRegex);
    }

    @CheckForNull
    public String getExcludeRegex() {
        return excludeRegex;
    }

    @DataBoundSetter
    public void setExcludeRegex(@CheckForNull String excludeRegex) {
        this.excludeRegex = Util.fixEmptyAndTrim(excludeRegex);
    }

    /**
     * When {@code true}, stale branches are logged as "WOULD filter" but not actually excluded.
     * Lets you preview the impact of the filter before enabling real filtering.
     */
    private boolean dryRun;

    public boolean isDryRun() {
        return dryRun;
    }

    @DataBoundSetter
    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    @Override
    protected void decorateContext(SCMSourceContext<?, ?> context) {
        final Pattern includePattern = includeRegex != null ? Pattern.compile(includeRegex) : null;
        final Pattern excludePattern = excludeRegex != null ? Pattern.compile(excludeRegex) : null;

        context.withFilter(new SCMHeadFilter() {
            // Cache the default branch name so we don't call getDefaultBranch() for every head.
            private String defaultBranch = null;
            private boolean defaultBranchFetched = false;

            private String getDefaultBranch(GitHubSCMSourceRequest githubRequest) throws IOException {
                if (!defaultBranchFetched) {
                    defaultBranch = githubRequest.getRepository().getDefaultBranch();
                    defaultBranchFetched = true;
                }
                return defaultBranch;
            }

            @Override
            public boolean isExcluded(@NonNull SCMSourceRequest request, @NonNull SCMHead head) throws IOException {
                if (!(request instanceof GitHubSCMSourceRequest) || !(head instanceof BranchSCMHead)) {
                    return false;
                }
                GitHubSCMSourceRequest githubRequest = (GitHubSCMSourceRequest) request;
                String headName = head.getName();

                if (includePattern != null && !includePattern.matcher(headName).matches()) {
                    return false;
                }
                if (excludePattern != null && excludePattern.matcher(headName).matches()) {
                    return false;
                }

                for (GHBranch branch : githubRequest.getBranches()) {
                    if (!branch.getName().equals(headName)) {
                        continue;
                    }
                    if (branch.getName().equals(getDefaultBranch(githubRequest))) {
                        return false;
                    }
                    if (branch.isProtected()) {
                        request.listener()
                                .getLogger()
                                .format("%n    Won't filter branch %s: it is a protected branch.%n", headName);
                        return false;
                    }
                    Date lastCommitDate = githubRequest
                            .getRepository()
                            .getCommit(branch.getSHA1())
                            .getCommitDate();
                    if (lastCommitDate == null) {
                        return false;
                    }
                    long ageInDays = (System.currentTimeMillis() - lastCommitDate.getTime()) / (1000L * 60 * 60 * 24);
                    if (ageInDays >= daysStale) {
                        if (dryRun) {
                            request.listener()
                                    .getLogger()
                                    .format(
                                            "%n    [stale-dry-run] WOULD filter branch %s. Last commit was %d day(s) ago"
                                                    + " (stale threshold: %d day(s)).%n",
                                            headName, ageInDays, daysStale);
                            return false;
                        }
                        request.listener()
                                .getLogger()
                                .format(
                                        "%n    Won't build branch %s. Last commit was %d day(s) ago"
                                                + " (stale threshold: %d day(s)).%n",
                                        headName, ageInDays, daysStale);
                        return true;
                    }
                    return false;
                }
                return false;
            }
        });
    }

    @Symbol("gitHubStaleBranchFilter")
    @Extension
    @Selection
    public static class DescriptorImpl extends SCMSourceTraitDescriptor {

        @Override
        public String getDisplayName() {
            return Messages.StaleBranchFilterTrait_DisplayName();
        }

        @Override
        public Class<? extends SCMSourceContext> getContextClass() {
            return GitHubSCMSourceContext.class;
        }

        @Override
        public Class<? extends SCMSource> getSourceClass() {
            return GitHubSCMSource.class;
        }

        public FormValidation doCheckDaysStale(@QueryParameter int value) {
            if (value < 1) {
                return FormValidation.error("Days stale must be a positive number.");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckIncludeRegex(@QueryParameter String value) {
            return validateRegex(value);
        }

        public FormValidation doCheckExcludeRegex(@QueryParameter String value) {
            return validateRegex(value);
        }

        private FormValidation validateRegex(String value) {
            String trimmed = Util.fixEmptyAndTrim(value);
            if (trimmed == null) {
                return FormValidation.ok();
            }
            try {
                java.util.regex.Pattern.compile(trimmed);
                return FormValidation.ok();
            } catch (java.util.regex.PatternSyntaxException e) {
                return FormValidation.error("Invalid regular expression: " + e.getMessage());
            }
        }
    }
}
