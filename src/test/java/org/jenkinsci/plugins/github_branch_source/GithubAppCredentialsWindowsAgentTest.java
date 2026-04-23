package org.jenkinsci.plugins.github_branch_source;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for the Windows Credential Manager cache-clearing logic added to
 * {@link GitHubAppCredentials}.
 *
 * <p>These tests exercise the static helper methods
 * ({@link GitHubAppCredentials#deriveGitHostFromApiUri} and
 * {@link GitHubAppCredentials#clearWindowsCredentialManagerCache}) and verify that the right
 * credential keys are evicted. They run on any OS because the {@link
 * GitHubAppCredentials#windowsCredentialCleaner} field is replaced with a recording stub.
 */
public class GithubAppCredentialsWindowsAgentTest {

    private Consumer<String> originalCleaner;
    private boolean originalClearFlag;
    private List<String> deletedKeys;

    @Before
    public void setUp() {
        originalCleaner = GitHubAppCredentials.windowsCredentialCleaner;
        originalClearFlag = GitHubAppCredentials.CLEAR_WINDOWS_CREDENTIAL_MANAGER_CACHE;
        deletedKeys = new ArrayList<>();
        GitHubAppCredentials.windowsCredentialCleaner = deletedKeys::add;
        GitHubAppCredentials.CLEAR_WINDOWS_CREDENTIAL_MANAGER_CACHE = true;
    }

    @After
    public void tearDown() {
        GitHubAppCredentials.windowsCredentialCleaner = originalCleaner;
        GitHubAppCredentials.CLEAR_WINDOWS_CREDENTIAL_MANAGER_CACHE = originalClearFlag;
    }

    // -------------------------------------------------------------------------
    // deriveGitHostFromApiUri
    // -------------------------------------------------------------------------

    @Test
    public void deriveGitHost_standardGitHub() {
        assertThat(GitHubAppCredentials.deriveGitHostFromApiUri("https://api.github.com"), is("github.com"));
    }

    @Test
    public void deriveGitHost_githubEnterprise() {
        assertThat(
                GitHubAppCredentials.deriveGitHostFromApiUri("https://ghe.example.com/api/v3"),
                is("ghe.example.com"));
    }

    @Test
    public void deriveGitHost_enterpriseWithPort() {
        assertThat(
                GitHubAppCredentials.deriveGitHostFromApiUri("https://github.corp.example.com:8443/api/v3"),
                is("github.corp.example.com"));
    }

    @Test
    public void deriveGitHost_malformedUri_fallsBackToGithubCom() {
        assertThat(GitHubAppCredentials.deriveGitHostFromApiUri("not a uri ://???"), is("github.com"));
    }

    @Test
    public void deriveGitHost_emptyString_fallsBackToGithubCom() {
        assertThat(GitHubAppCredentials.deriveGitHostFromApiUri(""), is("github.com"));
    }

    // -------------------------------------------------------------------------
    // clearWindowsCredentialManagerCache – key format
    // -------------------------------------------------------------------------

    @Test
    public void clearCache_standardGitHub_deletesExpectedKeys() {
        GitHubAppCredentials.clearWindowsCredentialManagerCache("https://api.github.com");

        assertThat(
                deletedKeys,
                contains("git:https://github.com", "LegacyGenericCredential:https://github.com"));
    }

    @Test
    public void clearCache_githubEnterprise_deletesExpectedKeys() {
        GitHubAppCredentials.clearWindowsCredentialManagerCache("https://ghe.example.com/api/v3");

        assertThat(
                deletedKeys,
                contains(
                        "git:https://ghe.example.com",
                        "LegacyGenericCredential:https://ghe.example.com"));
    }

    @Test
    public void clearCache_alwaysDeletesBothKeyFormats() {
        GitHubAppCredentials.clearWindowsCredentialManagerCache("https://api.github.com");

        assertThat("Both wincred and GCM key formats must be cleared", deletedKeys.size(), is(2));
    }

    // -------------------------------------------------------------------------
    // CLEAR_WINDOWS_CREDENTIAL_MANAGER_CACHE flag
    // -------------------------------------------------------------------------

    @Test
    public void clearCache_flagDisabled_doesNotDeleteKeys() {
        GitHubAppCredentials.CLEAR_WINDOWS_CREDENTIAL_MANAGER_CACHE = false;

        // Simulate what getPassword() does when the flag is false
        if (GitHubAppCredentials.CLEAR_WINDOWS_CREDENTIAL_MANAGER_CACHE) {
            GitHubAppCredentials.clearWindowsCredentialManagerCache("https://api.github.com");
        }

        assertThat(deletedKeys, is(empty()));
    }
}
