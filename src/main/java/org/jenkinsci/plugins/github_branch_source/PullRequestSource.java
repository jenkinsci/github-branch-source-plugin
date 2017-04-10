package org.jenkinsci.plugins.github_branch_source;

/**
 * Used for data migration for a 1.x upgrade.
 * @since 2.1.0
 */
@Deprecated // TODO remove once migration from 1.x is no longer supported
class PullRequestSource {
    private final String sourceOwner;
    private final String sourceRepo;
    private final String sourceBranch;

    PullRequestSource(String sourceOwner, String sourceRepo, String sourceBranch) {
        this.sourceOwner = sourceOwner;
        this.sourceRepo = sourceRepo;
        this.sourceBranch = sourceBranch;
    }

    public String getSourceOwner() {
        return sourceOwner;
    }

    public String getSourceRepo() {
        return sourceRepo;
    }

    public String getSourceBranch() {
        return sourceBranch;
    }
}
