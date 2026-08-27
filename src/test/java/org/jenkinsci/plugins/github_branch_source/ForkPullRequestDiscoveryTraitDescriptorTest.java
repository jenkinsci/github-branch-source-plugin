package org.jenkinsci.plugins.github_branch_source;

import static org.junit.Assert.assertEquals;
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
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * Checks that the auto-approval users form validation only answers POST requests from someone
 * allowed to configure it.
 */
public class ForkPullRequestDiscoveryTraitDescriptorTest {

    private static final String CHECK_URL =
            "descriptorByName/org.jenkinsci.plugins.github_branch_source.ForkPullRequestDiscoveryTrait$TrustExternalApproval/checkAutoApprovalUsers?value=alice";

    @Rule
    public final JenkinsRule j = new JenkinsRule();

    @Before
    public void setUp() {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        MockAuthorizationStrategy auth = new MockAuthorizationStrategy();
        auth.grant(Jenkins.ADMINISTER).everywhere().to("alice");
        auth.grant(Jenkins.READ).everywhere().toEveryone();
        j.jenkins.setAuthorizationStrategy(auth);
    }

    @Test
    public void getIsNotAnswered() throws Exception {
        try {
            Page page = request(HttpMethod.GET, "alice");
            fail("GET should not reach the check method, got "
                    + page.getWebResponse().getStatusCode());
        } catch (FailingHttpStatusCodeException e) {
            assertEquals(404, e.getStatusCode());
        }
    }

    @Test
    public void postAsAdminIsAnswered() throws Exception {
        Page page = request(HttpMethod.POST, "alice");
        assertEquals(200, page.getWebResponse().getStatusCode());
    }

    @Test
    public void postAsReadOnlyIsRejected() throws Exception {
        // "bob" has only Overall/Read, and there is no job in the path to fall back on.
        try {
            request(HttpMethod.POST, "bob");
            fail("Should not be able to do that");
        } catch (FailingHttpStatusCodeException e) {
            assertEquals(403, e.getStatusCode());
        }
    }

    private Page request(HttpMethod method, String userName) throws Exception {
        JenkinsRule.WebClient client = j.createWebClient().login(userName);
        client.getOptions().setThrowExceptionOnFailingStatusCode(true);
        WebRequest request = new WebRequest(new URL(client.getContextPath() + CHECK_URL), method);
        request.setAdditionalHeader("Accept", client.getBrowserVersion().getHtmlAcceptHeader());
        if (method == HttpMethod.POST) {
            request.setRequestParameters(Arrays.asList(new NameValuePair(
                    hudson.Functions.getCrumbRequestField(), hudson.Functions.getCrumb((StaplerRequest2) null))));
        }
        return client.getPage(request);
    }
}
