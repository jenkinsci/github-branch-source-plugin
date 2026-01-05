package org.jenkinsci.plugins.github_branch_source.app_credentials;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import hudson.ExtensionList;
import hudson.security.ACL;
import org.jenkinsci.plugins.github_branch_source.GitHubAppCredentials;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;

@WithJenkins
class MigrationAdminMonitorTest {

    private JenkinsRule r;

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        r = rule;
    }

    // Checks the migration behavior for credentials created prior to the introduction of the repository access
    // strategy and the default permissions strategy.
    @Test
    @LocalData
    void smokes() {
        // LocalData based on the following code at commit 50351eb
        /*
        var store = CredentialsProvider.lookupStores(r.jenkins).iterator().next();
        var credentials = GitHubApp.createCredentials(myAppCredentialsId);
        credentials.setOwner("cloudBeers");
        store.addCredentials(Domain.global(), credentials);
        credentials = GitHubApp.createCredentials(credentials);
        store.addCredentials(Domain.global(), credentials);
        */
        var credentialsWithOwner = CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentialsInItemGroup(GitHubAppCredentials.class, r.jenkins, ACL.SYSTEM2),
                CredentialsMatchers.withId("old-credentials-with-owner"));
        assertThat(credentialsWithOwner.getOwner(), nullValue());
        var strategyWithOwner = (AccessSpecifiedRepositories) credentialsWithOwner.getRepositoryAccessStrategy();
        assertThat(strategyWithOwner.getOwner(), is("cloudBeers"));
        assertThat(strategyWithOwner.getRepositories(), empty());
        assertThat(credentialsWithOwner.getDefaultPermissionsStrategy(), is(DefaultPermissionsStrategy.INHERIT_ALL));

        var credentialsNoOwner = CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentialsInItemGroup(GitHubAppCredentials.class, r.jenkins, ACL.SYSTEM2),
                CredentialsMatchers.withId("old-credentials-no-owner"));
        assertThat(credentialsNoOwner.getOwner(), nullValue());
        assertThat(credentialsNoOwner.getRepositoryAccessStrategy(), instanceOf(AccessSpecifiedRepositories.class));
        var strategyWithNoOwner = (AccessSpecifiedRepositories) credentialsNoOwner.getRepositoryAccessStrategy();
        assertThat(strategyWithNoOwner.getOwner(), nullValue());
        assertThat(strategyWithNoOwner.getRepositories(), empty());
        assertThat(credentialsNoOwner.getDefaultPermissionsStrategy(), is(DefaultPermissionsStrategy.INHERIT_ALL));

        var monitor = ExtensionList.lookupSingleton(MigrationAdminMonitor.class);
        assertTrue(monitor.isActivated());
        assertThat(
                monitor.getMigratedCredentialIds(),
                containsInAnyOrder("old-credentials-with-owner", "old-credentials-no-owner"));
    }
}
