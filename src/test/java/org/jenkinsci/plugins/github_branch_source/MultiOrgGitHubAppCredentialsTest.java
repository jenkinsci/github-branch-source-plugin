package org.jenkinsci.plugins.github_branch_source;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.*;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.Domain;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Simple test cases for {@link MultiOrgGitHubAppCredentials}.
 */
public class MultiOrgGitHubAppCredentialsTest extends AbstractGitHubWireMockTest {

    private static final String TEST_APP_ID = "12345";
    private static final String TEST_CREDENTIAL_ID = "test-multi-org-app-creds";
    private static final String TEST_PRIVATE_KEY = GitHubApp.getPrivateKey();

    private static CredentialsStore store;
    private MultiOrgGitHubAppCredentials credentials;

    @BeforeClass
    public static void setUpJenkins() throws Exception {
        store = CredentialsProvider.lookupStores(r.jenkins).iterator().next();
    }

    @Before
    public void setUp() throws Exception {
        // Create fresh credentials for each test
        credentials = new MultiOrgGitHubAppCredentials(
                CredentialsScope.GLOBAL,
                TEST_CREDENTIAL_ID,
                "Test Multi-Org GitHub App",
                TEST_APP_ID,
                Secret.fromString(TEST_PRIVATE_KEY));
        credentials.setApiUri(githubApi.baseUrl());

        // Set up WireMock stubs for GitHub API responses
        setupGitHubApiStubs();
    }

    private void setupGitHubApiStubs() {
        // Stub for getting the app information
        githubApi.stubFor(get(urlEqualTo("/app"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" + "  \"id\": "
                                + TEST_APP_ID + ",\n" + "  \"name\": \"Test App\",\n"
                                + "  \"owner\": {\n"
                                + "    \"login\": \"test-owner\"\n"
                                + "  }\n"
                                + "}")));

        // Stub for getting app installations
        githubApi.stubFor(get(urlEqualTo("/app/installations"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[\n" + "  {\n"
                                + "    \"id\": 1,\n"
                                + "    \"account\": {\n"
                                + "      \"login\": \"org1\",\n"
                                + "      \"type\": \"Organization\"\n"
                                + "    },\n"
                                + "    \"permissions\": {\n"
                                + "      \"checks\": \"write\",\n"
                                + "      \"pull_requests\": \"write\",\n"
                                + "      \"contents\": \"read\",\n"
                                + "      \"metadata\": \"read\"\n"
                                + "    }\n"
                                + "  },\n"
                                + "  {\n"
                                + "    \"id\": 2,\n"
                                + "    \"account\": {\n"
                                + "      \"login\": \"org2\",\n"
                                + "      \"type\": \"Organization\"\n"
                                + "    },\n"
                                + "    \"permissions\": {\n"
                                + "      \"checks\": \"write\",\n"
                                + "      \"pull_requests\": \"write\",\n"
                                + "      \"contents\": \"read\",\n"
                                + "      \"metadata\": \"read\"\n"
                                + "    }\n"
                                + "  },\n"
                                + "  {\n"
                                + "    \"id\": 3,\n"
                                + "    \"account\": {\n"
                                + "      \"login\": \"user1\",\n"
                                + "      \"type\": \"User\"\n"
                                + "    },\n"
                                + "    \"permissions\": {\n"
                                + "      \"checks\": \"write\",\n"
                                + "      \"pull_requests\": \"write\",\n"
                                + "      \"contents\": \"read\",\n"
                                + "      \"metadata\": \"read\"\n"
                                + "    }\n"
                                + "  }\n"
                                + "]")));

        // Stub for generating installation tokens
        String futureDate = DateTimeFormatter.ISO_INSTANT.format(
                new Date(System.currentTimeMillis() + Duration.ofHours(1).toMillis()).toInstant());

        githubApi.stubFor(post(urlMatching("/app/installations/.*/access_tokens"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" + "  \"token\": \"test-installation-token\",\n"
                                + "  \"expires_at\": \""
                                + futureDate + "\"\n" + "}")));

        // Stub for rate limit check
        githubApi.stubFor(get(urlEqualTo("/rate_limit"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" + "  \"resources\": {\n"
                                + "    \"core\": {\n"
                                + "      \"limit\": 5000,\n"
                                + "      \"remaining\": 4999,\n"
                                + "      \"reset\": "
                                + (System.currentTimeMillis() / 1000 + 3600) + "\n" + "    }\n"
                                + "  }\n"
                                + "}")));
    }

    @Test
    public void testConstructor() {
        assertEquals(TEST_CREDENTIAL_ID, credentials.getId());
        assertEquals("Test Multi-Org GitHub App", credentials.getDescription());
        assertEquals(TEST_APP_ID, credentials.getAppID());
        assertEquals(TEST_PRIVATE_KEY, credentials.getPrivateKey().getPlainText());
    }

    @Test
    public void testGetAvailableOrganizations() {
        List<String> organizations = credentials.getAvailableOrganizations();

        assertNotNull(organizations);
        assertEquals(3, organizations.size());
        assertTrue(organizations.contains("org1"));
        assertTrue(organizations.contains("org2"));
        assertTrue(organizations.contains("user1"));
    }

    @Test
    public void testGetAvailableOrganizationsCaching() {
        // First call should make API request
        List<String> organizations1 = credentials.getAvailableOrganizations();

        // Second call should use cached result
        List<String> organizations2 = credentials.getAvailableOrganizations();

        assertEquals(organizations1, organizations2);

        // Verify only one API call was made to /app/installations
        githubApi.verify(1, getRequestedFor(urlEqualTo("/app/installations")));
    }

    @Test
    public void testRefreshAvailableOrganizations() {
        // Get initial organizations
        credentials.getAvailableOrganizations();

        // Force refresh
        credentials.refreshAvailableOrganizations();

        // Should have made two API calls
        githubApi.verify(2, getRequestedFor(urlEqualTo("/app/installations")));
    }

    @Test
    public void testForceRefreshOrganizations() {
        // Get initial organizations
        credentials.getAvailableOrganizations();

        // Force refresh
        credentials.forceRefreshOrganizations();

        // Get organizations again - should use fresh data
        credentials.getAvailableOrganizations();

        // Should have made exactly two API calls
        githubApi.verify(exactly(2), getRequestedFor(urlEqualTo("/app/installations")));
    }

    @Test
    public void testForOrganization() {
        GitHubAppCredentials orgCredentials = credentials.forOrganization("org2");

        assertNotNull(orgCredentials);
        assertEquals("org2", orgCredentials.getOwner());
        assertEquals(TEST_APP_ID, orgCredentials.getAppID());
    }

    @Test
    public void testForOrganizationNotInList() {
        // This should still work but log a warning
        GitHubAppCredentials orgCredentials = credentials.forOrganization("unknown-org");

        assertNotNull(orgCredentials);
        assertEquals("unknown-org", orgCredentials.getOwner());
    }

    @Test
    public void testDescriptorDisplayName() {
        MultiOrgGitHubAppCredentials.DescriptorImpl descriptor = new MultiOrgGitHubAppCredentials.DescriptorImpl();

        assertEquals("GitHub App (Multi-Organization)", descriptor.getDisplayName());
    }

    @Test
    public void testDescriptorFillOrganizationItems() throws Exception {
        // Add credentials to store for this test
        store.addCredentials(Domain.global(), credentials);

        try {
            MultiOrgGitHubAppCredentials.DescriptorImpl descriptor = new MultiOrgGitHubAppCredentials.DescriptorImpl();

            // Test with valid credential ID
            ListBoxModel result = descriptor.doFillOrganizationItems(null, TEST_CREDENTIAL_ID);

            assertNotNull(result);
            assertTrue(result.size() > 0);
        } finally {
            store.removeCredentials(Domain.global(), credentials);
        }
    }

    @Test
    public void testDescriptorFillOrganizationItemsEmptyCredentialId() {
        MultiOrgGitHubAppCredentials.DescriptorImpl descriptor = new MultiOrgGitHubAppCredentials.DescriptorImpl();

        ListBoxModel result = descriptor.doFillOrganizationItems(null, "");

        assertNotNull(result);
        assertEquals(1, result.size()); // Just the empty value
    }

    @Test
    public void testDescriptorTestMultiOrgConnectionMissingAppId() {
        MultiOrgGitHubAppCredentials.DescriptorImpl descriptor = new MultiOrgGitHubAppCredentials.DescriptorImpl();

        FormValidation result = descriptor.doTestMultiOrgConnection("", TEST_PRIVATE_KEY, githubApi.baseUrl());

        assertEquals(FormValidation.Kind.ERROR, result.kind);
        assertTrue(result.getMessage().contains("App ID is required"));
    }

    @Test
    public void testDescriptorTestMultiOrgConnectionMissingPrivateKey() {
        MultiOrgGitHubAppCredentials.DescriptorImpl descriptor = new MultiOrgGitHubAppCredentials.DescriptorImpl();

        FormValidation result = descriptor.doTestMultiOrgConnection(TEST_APP_ID, "", githubApi.baseUrl());

        assertEquals(FormValidation.Kind.ERROR, result.kind);
        assertTrue(result.getMessage().contains("Private key is required"));
    }

    @Test
    public void testDescriptorTestMultiOrgConnectionNoOrganizations() {
        // Stub for empty installations
        githubApi.stubFor(get(urlEqualTo("/app/installations"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));

        MultiOrgGitHubAppCredentials.DescriptorImpl descriptor = new MultiOrgGitHubAppCredentials.DescriptorImpl();

        FormValidation result = descriptor.doTestMultiOrgConnection(TEST_APP_ID, TEST_PRIVATE_KEY, githubApi.baseUrl());

        assertEquals(FormValidation.Kind.WARNING, result.kind);
        assertTrue(result.getMessage().contains("GitHub App is not installed to any organizations"));
    }

    @Test
    public void testHandleApiFailure() {
        // Create a fresh credential instance that doesn't have cached results
        MultiOrgGitHubAppCredentials freshCredentials = new MultiOrgGitHubAppCredentials(
                CredentialsScope.GLOBAL,
                "fresh-creds",
                "Fresh Creds",
                TEST_APP_ID,
                Secret.fromString(TEST_PRIVATE_KEY));
        freshCredentials.setApiUri(githubApi.baseUrl());

        // Reset all existing stubs and add a failing one
        githubApi.resetAll();

        // Add back the app stub
        githubApi.stubFor(get(urlEqualTo("/app"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" + "  \"id\": "
                                + TEST_APP_ID + ",\n" + "  \"name\": \"Test App\",\n"
                                + "  \"owner\": {\n"
                                + "    \"login\": \"test-owner\"\n"
                                + "  }\n"
                                + "}")));

        // Stub for installations failure
        githubApi.stubFor(get(urlEqualTo("/app/installations"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"Internal Server Error\"}")));

        List<String> organizations = freshCredentials.getAvailableOrganizations();

        // Should return empty list on failure
        assertNotNull(organizations);
        assertEquals(0, organizations.size());
    }

    @Test
    public void testOrganizationCacheExpiry() throws Exception {
        // Get initial organizations
        credentials.getAvailableOrganizations();

        // Manually expire the cache by setting a very old refresh time
        java.lang.reflect.Field lastRefreshTimeField =
                MultiOrgGitHubAppCredentials.class.getDeclaredField("lastRefreshTime");
        lastRefreshTimeField.setAccessible(true);
        lastRefreshTimeField.setLong(credentials, 0L); // Set to epoch

        // Get organizations again - should refresh
        credentials.getAvailableOrganizations();

        // Should have made two API calls due to cache expiry
        githubApi.verify(2, getRequestedFor(urlEqualTo("/app/installations")));
    }
}
