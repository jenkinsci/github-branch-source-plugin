package org.jenkinsci.plugins.github_branch_source;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.util.FormValidation;
import java.io.IOException;
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
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

/**
 * Trait that filters out tags whose creation date is older than a specified number of days.
 * Stale tags are excluded from Jenkins indexing so they no longer appear as jobs.
 *
 * <p>The tag date is read from the already-discovered {@link GitHubTagSCMHead#getTimestamp()}
 * (computed once during discovery), so this filter makes no additional GitHub API calls.
 *
 * <p>Optional {@code includeRegex} scopes the filter to only matching tag names.
 * Optional {@code excludeRegex} exempts matching tag names from stale filtering entirely.
 */
public class StaleTagFilterTrait extends SCMSourceTrait {

    /** Number of days after which a tag is considered stale. */
    private final int daysStale;

    /**
     * If set, stale filtering is only applied to tags whose name matches this regex.
     * Tags that do not match are never excluded.
     */
    @CheckForNull
    private String includeRegex;

    /**
     * If set, tags whose name matches this regex are always kept, regardless of age.
     */
    @CheckForNull
    private String excludeRegex;

    @DataBoundConstructor
    public StaleTagFilterTrait(int daysStale) {
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
     * When {@code true}, stale tags are logged as "WOULD filter" but not actually excluded.
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
                if (!(request instanceof GitHubSCMSourceRequest) || !(head instanceof GitHubTagSCMHead)) {
                    return false;
                }
                String headName = head.getName();

                if (includePattern != null && !includePattern.matcher(headName).matches()) {
                    return false;
                }
                if (excludePattern != null && excludePattern.matcher(headName).matches()) {
                    return false;
                }

                // The timestamp was already resolved once during discovery
                // (see GitHubSCMSource's tag retrieval), so no API call is needed here.
                long timestamp = ((GitHubTagSCMHead) head).getTimestamp();
                if (timestamp <= 0L) {
                    // unknown date; don't exclude
                    return false;
                }

                long ageInDays = (System.currentTimeMillis() - timestamp) / (1000L * 60 * 60 * 24);
                if (ageInDays >= daysStale) {
                    if (dryRun) {
                        request.listener()
                                .getLogger()
                                .format(
                                        "%n    [stale-dry-run] WOULD filter tag %s. Tag is %d day(s) old"
                                                + " (stale threshold: %d day(s)).%n",
                                        headName, ageInDays, daysStale);
                        return false;
                    }
                    request.listener()
                            .getLogger()
                            .format(
                                    "%n    Won't build tag %s. Tag is %d day(s) old"
                                            + " (stale threshold: %d day(s)).%n",
                                    headName, ageInDays, daysStale);
                    return true;
                }
                return false;
            }
        });
    }

    @Symbol("gitHubStaleTagFilter")
    @Extension
    @Selection
    public static class DescriptorImpl extends SCMSourceTraitDescriptor {

        @Override
        public String getDisplayName() {
            return Messages.StaleTagFilterTrait_DisplayName();
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
