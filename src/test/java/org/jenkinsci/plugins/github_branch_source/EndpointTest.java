package org.jenkinsci.plugins.github_branch_source;

import static org.junit.jupiter.api.Assertions.*;

import hudson.ExtensionList;
import hudson.Functions;
import hudson.Util;
import hudson.model.UnprotectedRootAction;
import hudson.security.csrf.CrumbExclusion;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import jenkins.model.Jenkins;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.util.NameValuePair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

@WithJenkins
class EndpointTest {

    private String testUrl;

    private JenkinsRule j;

    @BeforeEach
    void beforeEach(JenkinsRule rule) throws Exception {
        j = rule;
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        MockAuthorizationStrategy auth = new MockAuthorizationStrategy();
        auth.grant(Jenkins.MANAGE).everywhere().to("alice");
        auth.grant(Jenkins.READ).everywhere().toEveryone();
        j.jenkins.setAuthorizationStrategy(auth);
        testUrl = Util.rawEncode(j.getURL().toString() + "testroot/");
    }

    @Test
    @Issue("SECURITY-806")
    void cantGet_doCheckApiUri() {
        FailingHttpStatusCodeException e = assertThrows(FailingHttpStatusCodeException.class, () -> j.createWebClient()
                .goTo(
                        appendCrumb(
                                "descriptorByName/org.jenkinsci.plugins.github_branch_source.Endpoint/checkApiUri?apiUri="
                                        + testUrl),
                        "Should not be able to do that"));
        assertEquals(405, e.getStatusCode());
        assertFalse(TestRoot.get().visited);
    }

    @Test
    @Issue("SECURITY-806")
    void cantPostAsAnonymous_doCheckApiUri() {
        FailingHttpStatusCodeException e = assertThrows(
                FailingHttpStatusCodeException.class,
                () -> post(
                        "descriptorByName/org.jenkinsci.plugins.github_branch_source.Endpoint/checkApiUri?apiUri="
                                + testUrl,
                        null),
                "Should not be able to do that");
        assertEquals(403, e.getStatusCode());
        assertFalse(TestRoot.get().visited);
    }

    @Test
    @Issue("SECURITY-806")
    void canPostAsAdmin_doCheckApiUri() throws Exception {
        post(
                "descriptorByName/org.jenkinsci.plugins.github_branch_source.Endpoint/checkApiUri?apiUri=" + testUrl,
                "alice");
        assertTrue(TestRoot.get().visited);
    }

    @Test
    @Issue("JENKINS-73053")
    void manageCanSetupEndpoints() throws Exception {
        HtmlPage htmlPage = j.createWebClient().login("alice").goTo("manage/configure");
        assertTrue(htmlPage.getVisibleText().contains("GitHub Enterprise Servers"));
    }

    private String appendCrumb(String url) {
        return url + "&" + getCrumb();
    }

    private String getCrumb() {
        return Functions.getCrumbRequestField() + "=" + Functions.getCrumb((StaplerRequest2) null);
    }

    private Page post(String relative, String userName) throws Exception {
        final JenkinsRule.WebClient client;
        if (userName != null) {
            client = j.createWebClient().login(userName);
        } else {
            client = j.createWebClient();
        }

        final WebRequest request = new WebRequest(new URL(client.getContextPath() + relative), HttpMethod.POST);
        request.setAdditionalHeader("Accept", client.getBrowserVersion().getHtmlAcceptHeader());
        request.setRequestParameters(Arrays.asList(
                new NameValuePair(Functions.getCrumbRequestField(), Functions.getCrumb((StaplerRequest2) null))));
        return client.getPage(request);
    }

    @TestExtension
    public static class TestRoot implements UnprotectedRootAction {

        boolean visited = false;

        @Override
        public String getIconFileName() {
            return null;
        }

        @Override
        public String getDisplayName() {
            return null;
        }

        @Override
        public String getUrlName() {
            return "testroot";
        }

        public void doIndex(StaplerRequest2 request, StaplerResponse2 response) throws IOException {
            visited = true;
            response.getWriter().println("OK");
        }

        static TestRoot get() {
            return ExtensionList.lookup(UnprotectedRootAction.class).get(TestRoot.class);
        }
    }

    @TestExtension
    public static class CrumbExcluder extends CrumbExclusion {
        @Override
        public boolean process(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            final String pathInfo = request.getPathInfo();
            if (pathInfo == null || !pathInfo.contains("testroot")) {
                return false;
            }
            chain.doFilter(request, response);
            return true;
        }
    }
}
