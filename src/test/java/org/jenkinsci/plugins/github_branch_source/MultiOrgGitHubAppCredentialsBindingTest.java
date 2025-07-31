package org.jenkinsci.plugins.github_branch_source;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.Domain;
import hudson.util.Secret;
import java.util.Set;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Before;
import org.junit.Test;

/**
 * Simple test cases for MultiOrgGitHubAppCredentialsBinding class.
 */
public class MultiOrgGitHubAppCredentialsBindingTest extends AbstractGitHubWireMockTest {

    private static final String TEST_APP_ID = "12345";
    private static final String TEST_PRIVATE_KEY = GitHubApp.getPrivateKey();
    private static final String TEST_CREDENTIALS_ID = "test-multi-org-binding";

    private MultiOrgGitHubAppCredentials credentials;
    private CredentialsStore store;

    @Before
    public void setUp() throws Exception {
        // Create test credentials
        credentials = new MultiOrgGitHubAppCredentials(
                CredentialsScope.GLOBAL,
                TEST_CREDENTIALS_ID,
                "Test Multi-Org GitHub App Credentials",
                TEST_APP_ID,
                Secret.fromString(TEST_PRIVATE_KEY));

        // Add to credentials store
        store = CredentialsProvider.lookupStores(r.jenkins).iterator().next();
        store.addCredentials(Domain.global(), credentials);

        // Manually set some test organizations to avoid API calls
        setTestOrganizations();
    }

    private void setTestOrganizations() throws Exception {
        // Use reflection to set test organizations directly
        var orgField = MultiOrgGitHubAppCredentials.class.getDeclaredField("availableOrganizations");
        orgField.setAccessible(true);
        var organizations = new java.util.ArrayList<String>();
        organizations.add("testorg1");
        organizations.add("testorg2");
        orgField.set(credentials, organizations);

        // Set refresh time to avoid automatic refresh
        var timeField = MultiOrgGitHubAppCredentials.class.getDeclaredField("lastRefreshTime");
        timeField.setAccessible(true);
        timeField.set(credentials, System.currentTimeMillis());
    }

    @Test
    public void testConstructorAndGetters() {
        // Test manual mode constructor
        MultiOrgGitHubAppCredentialsBinding binding =
                new MultiOrgGitHubAppCredentialsBinding("GITHUB_TOKEN", "testorg1", TEST_CREDENTIALS_ID);

        assertThat(binding.getTokenVariable(), equalTo("GITHUB_TOKEN"));
        assertThat(binding.getOrgName(), equalTo("testorg1"));
        assertThat(binding.getCredentialsId(), equalTo(TEST_CREDENTIALS_ID));
        assertThat(binding.isAutomaticMode(), is(false));
    }

    @Test
    public void testAutomaticModeConstructor() {
        // Test automatic mode constructor (no parameters)
        MultiOrgGitHubAppCredentialsBinding binding =
                new MultiOrgGitHubAppCredentialsBinding(null, null, TEST_CREDENTIALS_ID);

        assertThat(binding.getTokenVariable(), is(nullValue()));
        assertThat(binding.getOrgName(), is(nullValue()));
        assertThat(binding.getCredentialsId(), equalTo(TEST_CREDENTIALS_ID));
        assertThat(binding.isAutomaticMode(), is(true));
    }

    @Test
    public void testType() {
        MultiOrgGitHubAppCredentialsBinding binding =
                new MultiOrgGitHubAppCredentialsBinding("GITHUB_TOKEN", "testorg1", TEST_CREDENTIALS_ID);

        assertThat(binding.type(), equalTo(MultiOrgGitHubAppCredentials.class));
    }

    @Test
    public void testVariablesInAutomaticMode() throws Exception {
        MultiOrgGitHubAppCredentialsBinding binding =
                new MultiOrgGitHubAppCredentialsBinding(null, null, TEST_CREDENTIALS_ID);

        // Create a mock build
        WorkflowJob job = r.jenkins.createProject(WorkflowJob.class, "test-job");
        job.setDefinition(new CpsFlowDefinition("echo 'test'", true));
        var build = job.scheduleBuild2(0).get();

        Set<String> variables = binding.variables(build);

        // Should include GITHUB_ORGS and org-specific tokens
        assertThat(variables, hasItem("GITHUB_ORGS"));
        assertThat(variables, hasItem("GITHUB_TOKEN_TESTORG1"));
        assertThat(variables, hasItem("GITHUB_TOKEN_TESTORG2"));
    }

    @Test
    public void testVariablesInManualMode() throws Exception {
        MultiOrgGitHubAppCredentialsBinding binding =
                new MultiOrgGitHubAppCredentialsBinding("MY_TOKEN", "testorg1", TEST_CREDENTIALS_ID);

        // Create a mock build
        WorkflowJob job = r.jenkins.createProject(WorkflowJob.class, "test-job-manual");
        job.setDefinition(new CpsFlowDefinition("echo 'test'", true));
        var build = job.scheduleBuild2(0).get();

        Set<String> variables = binding.variables(build);

        // Should include GITHUB_ORGS and the specified token variable
        assertThat(variables, hasItem("GITHUB_ORGS"));
        assertThat(variables, hasItem("MY_TOKEN"));

        // Should not include automatic org-specific tokens
        assertThat(variables, not(hasItem("GITHUB_TOKEN_TESTORG1")));
        assertThat(variables, not(hasItem("GITHUB_TOKEN_TESTORG2")));
    }

    @Test
    public void testDescriptor() {
        MultiOrgGitHubAppCredentialsBinding.DescriptorImpl descriptor =
                new MultiOrgGitHubAppCredentialsBinding.DescriptorImpl();

        assertThat(descriptor.getDisplayName(), equalTo("Multi-Organization GitHub App credentials"));
    }

    @Test
    public void testDescriptorCredentialsIdFill() {
        MultiOrgGitHubAppCredentialsBinding.DescriptorImpl descriptor =
                new MultiOrgGitHubAppCredentialsBinding.DescriptorImpl();

        // Test with proper parameters as defined in the descriptor
        var items = descriptor.doFillCredentialsIdItems(null, TEST_CREDENTIALS_ID);

        // Should have at least one item
        assertThat(items.size(), greaterThan(0));
    }

    @Test
    public void testEmptyTokenVariableIsAutomatic() {
        // Test that empty string is treated as automatic mode
        MultiOrgGitHubAppCredentialsBinding binding =
                new MultiOrgGitHubAppCredentialsBinding("", "", TEST_CREDENTIALS_ID);

        assertThat(binding.isAutomaticMode(), is(true));
    }

    @Test
    public void testWhitespaceTokenVariableIsAutomatic() {
        // Test that whitespace-only string is treated as automatic mode
        MultiOrgGitHubAppCredentialsBinding binding =
                new MultiOrgGitHubAppCredentialsBinding("   ", "  ", TEST_CREDENTIALS_ID);

        assertThat(binding.isAutomaticMode(), is(true));
    }

    @Test
    public void testValidTokenVariableIsManual() {
        // Test that non-empty token variable triggers manual mode
        MultiOrgGitHubAppCredentialsBinding binding =
                new MultiOrgGitHubAppCredentialsBinding("MY_TOKEN", "testorg1", TEST_CREDENTIALS_ID);

        assertThat(binding.isAutomaticMode(), is(false));
    }

    @Test
    public void testConstructorValidation() {
        // Test validation: both parameters must be provided together
        try {
            new MultiOrgGitHubAppCredentialsBinding("MY_TOKEN", null, TEST_CREDENTIALS_ID);
            assertThat("Should have thrown IllegalArgumentException", false);
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), containsString("Both tokenVariable and orgName must be provided together"));
        }

        try {
            new MultiOrgGitHubAppCredentialsBinding(null, "testorg1", TEST_CREDENTIALS_ID);
            assertThat("Should have thrown IllegalArgumentException", false);
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), containsString("Both tokenVariable and orgName must be provided together"));
        }

        try {
            new MultiOrgGitHubAppCredentialsBinding("MY_TOKEN", "", TEST_CREDENTIALS_ID);
            assertThat("Should have thrown IllegalArgumentException", false);
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), containsString("Both tokenVariable and orgName must be provided together"));
        }
    }
}
