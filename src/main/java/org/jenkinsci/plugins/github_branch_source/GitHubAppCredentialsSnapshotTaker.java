package org.jenkinsci.plugins.github_branch_source;

import com.cloudbees.plugins.credentials.CredentialsSnapshotTaker;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsSnapshotTaker;
import hudson.Extension;

/**
 * A {@link CredentialsSnapshotTaker} for {@link GitHubAppCredentials} that is a no-op.
 *
 * <p>As {@code GitHubAppCredentials} tokens are time limited they need to be refreshed
 * periodically. This is currently addressed by its use of the {@code writeReplace()} and {@code
 * readResolve}, but as these credentials are {@link UsernamePasswordCredentials} this behaviour
 * conflicts with the {@link UsernamePasswordCredentialsSnapshotTaker}. This SnapshotTaker restores
 * the status quo allowing the Credentials to be replaced using the existing mechanism.
 */
@Extension
public class GitHubAppCredentialsSnapshotTaker extends CredentialsSnapshotTaker<GitHubAppCredentials> {

    @Override
    public GitHubAppCredentials snapshot(GitHubAppCredentials credentials) {
        return credentials;
    }

    @Override
    public Class<GitHubAppCredentials> type() {
        return GitHubAppCredentials.class;
    }
}
