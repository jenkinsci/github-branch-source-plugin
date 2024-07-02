package org.jenkinsci.plugins.github_branch_source;

/**
 * GitHub App credentials supplier.
 */
public interface GitHubAppAuthentication {

    GitHubAppCredentials getCredentials();
}
