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
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

/**
 * Trait that filters out pull requests that have had no activity for a specified number of days.
 * Staleness is measured against the PR's last-updated timestamp (new commits, comments, reviews,
 * labels, etc. all count as activity), which GitHub returns as part of the PR listing — this filter
 * makes no additional GitHub API calls.
 * Stale pull requests are excluded from Jenkins indexing so they no longer appear as jobs.
 *
 * <p>Optional {@code includeRegex} scopes the filter to only matching PR head names (e.g. {@code PR-42}).
 * Optional {@code excludeRegex} exempts matching PR head names from stale filtering entirely.
 */
public class StalePullRequestFilterTrait extends SCMSourceTrait {

    /** Number of days of inactivity after which a pull request is considered stale. */
    private final int daysStale;

    /**
     * If set, stale filtering is only applied to PRs whose head name matches this regex.
     * PRs that do not match are never excluded.
     */
    @CheckForNull
    private String includeRegex;

    /**
     * If set, PRs whose head name matches this regex are always kept, regardless of age.
     */
    @CheckForNull
    private String excludeRegex;

    @DataBoundConstructor
    public StalePullRequestFilterTrait(int daysStale) {
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
     * When {@code true}, stale pull requests are logged as "WOULD filter" but not actually excluded.
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
            @Override
            public boolean isExcluded(@NonNull SCMSourceRequest request, @NonNull SCMHead head) throws IOException {
                if (!(request instanceof GitHubSCMSourceRequest) || !(head instanceof PullRequestSCMHead)) {
                    return false;
                }
                GitHubSCMSourceRequest githubRequest = (GitHubSCMSourceRequest) request;
                String headName = head.getName();
                int prNumber = ((PullRequestSCMHead) head).getNumber();

                if (includePattern != null && !includePattern.matcher(headName).matches()) {
                    return false;
                }
                if (excludePattern != null && excludePattern.matcher(headName).matches()) {
                    return false;
                }

                request.listener()
                        .getLogger()
                        .format(
                                "%n    [StalePRFilter] Checking %s (PR #%d), threshold=%d day(s)%n",
                                headName, prNumber, daysStale);
                boolean prFound = false;
                for (GHPullRequest pr : githubRequest.getPullRequests()) {
                    if (pr.getNumber() != prNumber) {
                        continue;
                    }
                    prFound = true;

                    // pr.getUpdatedAt() is already populated from the PR listing response,
                    // so no additional API call is needed here.
                    Date lastUpdated = pr.getUpdatedAt();
                    if (lastUpdated == null) {
                        request.listener()
                                .getLogger()
                                .format(
                                        "%n    [StalePRFilter] Updated-at date is null for PR #%d — skipping filter%n",
                                        prNumber);
                        return false;
                    }

                    long ageInDays = (System.currentTimeMillis() - lastUpdated.getTime()) / (1000L * 60 * 60 * 24);
                    request.listener()
                            .getLogger()
                            .format(
                                    "%n    [StalePRFilter] %s last updated %d day(s) ago (threshold: %d day(s))%n",
                                    headName, ageInDays, daysStale);
                    if (ageInDays >= daysStale) {
                        if (dryRun) {
                            request.listener()
                                    .getLogger()
                                    .format(
                                            "%n    [stale-dry-run] WOULD filter pull request %s. Last updated %d day(s) ago"
                                                    + " (stale threshold: %d day(s)).%n",
                                            headName, ageInDays, daysStale);
                            return false;
                        }
                        request.listener()
                                .getLogger()
                                .format(
                                        "%n    Won't build pull request %s. Last updated %d day(s) ago"
                                                + " (stale threshold: %d day(s)).%n",
                                        headName, ageInDays, daysStale);
                        return true;
                    }
                    return false;
                }
                if (!prFound) {
                    request.listener()
                            .getLogger()
                            .format("%n    [StalePRFilter] PR #%d not found in getPullRequests() list%n", prNumber);
                }
                return false;
            }
        });
    }

    @Symbol("gitHubStalePullRequestFilter")
    @Extension
    @Selection
    public static class DescriptorImpl extends SCMSourceTraitDescriptor {

        @Override
        public String getDisplayName() {
            return Messages.StalePullRequestFilterTrait_DisplayName();
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
                Pattern.compile(trimmed);
                return FormValidation.ok();
            } catch (java.util.regex.PatternSyntaxException e) {
                return FormValidation.error("Invalid regular expression: " + e.getMessage());
            }
        }
    }
}
