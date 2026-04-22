package org.jenkinsci.plugins.github_branch_source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

import java.net.URL;
import java.util.Arrays;
import jenkins.model.Jenkins;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.htmlunit.util.NameValuePair;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.kohsuke.stapler.StaplerRequest2;

public class GitHubAppCredentialsDescriptorTest {

    private static final String DESCRIPTOR_URL =
            "descriptorByName/org.jenkinsci.plugins.github_branch_source.GitHubAppCredentials/testConnection";

    @Rule
    public final JenkinsRule j = new JenkinsRule();

    @Before
    public void setUp() {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        MockAuthorizationStrategy auth = new MockAuthorizationStrategy();
        auth.grant(Jenkins.MANAGE).everywhere().to("alice");
        auth.grant(Jenkins.READ).everywhere().toEveryone();
        j.jenkins.setAuthorizationStrategy(auth);
    }

    @Test
    @Issue("SECURITY-3702")
    public void cantPostAsAnonymous_doTestConnection() throws Exception {
        try {
            post(DESCRIPTOR_URL, null);
            fail("Should not be able to do that");
        } catch (FailingHttpStatusCodeException e) {
            assertEquals(403, e.getStatusCode());
        }
    }

    @Test
    @Issue("SECURITY-3702")
    public void cantPostAsReadOnly_doTestConnection() throws Exception {
        // "bob" has only Overall/Read — must be rejected
        try {
            post(DESCRIPTOR_URL, "bob");
            fail("Should not be able to do that");
        } catch (FailingHttpStatusCodeException e) {
            assertEquals(403, e.getStatusCode());
        }
    }

    @Test
    @Issue("SECURITY-3702")
    public void canPostAsManage_doTestConnection() throws Exception {
        // "alice" has Overall/Manage — must not receive a 403/405
        // The call will fail trying to reach GitHub, but permission is not the obstacle.
        try {
            Page page = post(DESCRIPTOR_URL, "alice");
            assertEquals(200, page.getWebResponse().getStatusCode());
        } catch (FailingHttpStatusCodeException e) {
            assertNotEquals("alice should not be denied by permission check", 403, e.getStatusCode());
        }
    }

    private Page post(String relative, String userName) throws Exception {
        final JenkinsRule.WebClient client;
        if (userName != null) {
            client = j.createWebClient().login(userName);
        } else {
            client = j.createWebClient();
        }
        client.getOptions().setThrowExceptionOnFailingStatusCode(true);
        final WebRequest request = new WebRequest(new URL(client.getContextPath() + relative), HttpMethod.POST);
        request.setAdditionalHeader("Accept", client.getBrowserVersion().getHtmlAcceptHeader());
        request.setRequestParameters(Arrays.asList(new NameValuePair(
                hudson.Functions.getCrumbRequestField(), hudson.Functions.getCrumb((StaplerRequest2) null))));
        return client.getPage(request);
    }
}
