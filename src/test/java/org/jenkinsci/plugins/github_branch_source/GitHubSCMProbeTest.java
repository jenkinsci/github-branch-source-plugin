package org.jenkinsci.plugins.github_branch_source;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.*;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import java.io.File;
import java.io.IOException;
import jenkins.scm.api.SCMHeadOrigin;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

public class GitHubSCMProbeTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    public static WireMockRuleFactory factory = new WireMockRuleFactory();

    @Rule
    public WireMockRule githubApi = factory.getRule(WireMockConfiguration.options()
            .dynamicPort()
            .usingFilesUnderClasspath("cache_failure")
            .extensions(ResponseTemplateTransformer.builder()
                    .global(true)
                    .maxCacheEntries(0L)
                    .build()));

    private GitHubSCMProbe probe;

    @Before
    public void setUp() throws Exception {
        // Clear all caches before each test
        File cacheBaseDir = new File(j.jenkins.getRootDir(), GitHubSCMProbe.class.getName() + ".cache");
        if (cacheBaseDir.exists()) {
            FileUtils.cleanDirectory(cacheBaseDir);
        }

        githubApi.stubFor(get(urlEqualTo("/repos/cloudbeers/yolo"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBodyFile("body-cloudbeers-yolo-PucD6.json")));

        // Return 404 for /rate_limit
        githubApi.stubFor(get(urlEqualTo("/rate_limit")).willReturn(aResponse().withStatus(404)));

        // validate api url
        githubApi.stubFor(get(urlEqualTo("/"))
                .willReturn(aResponse().withBody("{\"rate_limit_url\": \"https://localhost/placeholder/\"}")));
    }

    void createProbeForPR(int number) throws IOException {
        final GitHub github = Connector.connect("http://localhost:" + githubApi.port(), null);

        final GHRepository repo = github.getRepository("cloudbeers/yolo");
        final PullRequestSCMHead head = new PullRequestSCMHead(
                "PR-" + number,
                "cloudbeers",
                "yolo",
                "b",
                number,
                new BranchSCMHead("master"),
                new SCMHeadOrigin.Fork("rsandell"),
                ChangeRequestCheckoutStrategy.MERGE);
        probe = new GitHubSCMProbe(
                "http://localhost:" + githubApi.port(), null, repo, head, new PullRequestSCMRevision(head, "a", "b"));
    }

    @Issue("JENKINS-54126")
    @Test
    public void statWhenRootIs404() throws Exception {
        githubApi.stubFor(get(urlEqualTo("/repos/cloudbeers/yolo/contents/?ref=refs%2Fpull%2F1%2Fmerge"))
                .willReturn(aResponse().withStatus(404))
                .atPriority(0));

        createProbeForPR(1);

        assertFalse(probe.stat("Jenkinsfile").exists());
    }

    @Issue("JENKINS-54126")
    @Test
    public void statWhenDirIs404() throws Exception {
        githubApi.stubFor(get(urlEqualTo("/repos/cloudbeers/yolo/contents/subdir?ref=refs%2Fpull%2F1%2Fmerge"))
                .willReturn(aResponse().withStatus(404))
                .atPriority(0));

        createProbeForPR(1);

        assertTrue(probe.stat("README.md").exists());
        assertFalse(probe.stat("subdir").exists());
        assertFalse(probe.stat("subdir/Jenkinsfile").exists());
    }

    @Issue("JENKINS-54126")
    @Test
    public void statWhenRoot404andThenIncorrectCached() throws Exception {
        GitHubSCMSource.setCacheSize(10);

        createProbeForPR(9);

        // JENKINS-54126 happens when:
        // 1. client asks for a resource "Z" that doesn't exist
        // ---> client receives a 404 response from github and caches it.
        // ---> Important: GitHub does not send ETag header for 404 responses.
        // 2. Resource "Z" gets created on GitHub but some means.
        // 3. client (eventually) asks for the resource "Z" again.
        // ---> Since the the client has a cached response without ETag, it sends "If-Modified-Since"
        // header
        // ---> Resource has changed (it was created).
        //
        // ---> EXPECTED: GitHub should respond with 200 and data.
        // ---> ACTUAL: GitHub server lies, responds with incorrect 304 response, telling client that
        // the cached data is still valid.
        // ---> THE BAD: Client cache believes GitHub - uses the previously cached 404 (and even adds
        // the ETag).
        // ---> THE UGLY: Client is now stuck with a bad cached 404, and can't get rid of it until the
        // resource is _updated_ again or the cache is cleared manually.
        //
        // This is the cause of JENKINS-54126. This is a pervasive GitHub server problem.
        // We see it mostly in this one scenario, but it will happen anywhere the server returns a 404.
        // It cannot be reliably detected or mitigated at the level of this plugin.
        //
        // WORKAROUND (implemented in the github-api library):
        // 4. the github-api library recognizes any 404 with ETag as invalid. Does not return it to the
        // client.
        // ---> The github-api library automatically retries the request with "no-cache" to force
        // refresh with valid data.

        // 1.
        assertFalse(probe.stat("README.md").exists());

        // 3.
        // Without 4. this would return false and would stay false.
        assertTrue(probe.stat("README.md").exists());

        // 5. Verify caching is working
        assertTrue(probe.stat("README.md").exists());

        // Verify the expected requests were made
        if (hudson.Functions.isWindows()) {
            // On windows caching is disabled by default, so the work around doesn't happen
            githubApi.verify(
                    3,
                    RequestPatternBuilder.newRequestPattern(
                                    RequestMethod.GET, urlPathEqualTo("/repos/cloudbeers/yolo/contents/"))
                            .withHeader("Cache-Control", equalTo("max-age=0"))
                            .withHeader("If-Modified-Since", absent())
                            .withHeader("If-None-Match", absent()));
        } else {
            // 1.
            githubApi.verify(RequestPatternBuilder.newRequestPattern(
                            RequestMethod.GET, urlPathEqualTo("/repos/cloudbeers/yolo/contents/"))
                    .withHeader("Cache-Control", equalTo("max-age=0"))
                    .withHeader("If-None-Match", absent())
                    .withHeader("If-Modified-Since", absent()));

            // 3.
            githubApi.verify(RequestPatternBuilder.newRequestPattern(
                            RequestMethod.GET, urlPathEqualTo("/repos/cloudbeers/yolo/contents/"))
                    .withHeader("Cache-Control", containing("max-age"))
                    .withHeader("If-None-Match", absent())
                    .withHeader("If-Modified-Since", containing("GMT")));

            // 4.
            githubApi.verify(RequestPatternBuilder.newRequestPattern(
                            RequestMethod.GET, urlPathEqualTo("/repos/cloudbeers/yolo/contents/"))
                    .withHeader("Cache-Control", equalTo("no-cache"))
                    .withHeader("If-Modified-Since", absent())
                    .withHeader("If-None-Match", absent()));

            // 5.
            githubApi.verify(RequestPatternBuilder.newRequestPattern(
                            RequestMethod.GET, urlPathEqualTo("/repos/cloudbeers/yolo/contents/"))
                    .withHeader("Cache-Control", equalTo("max-age=0"))
                    .withHeader("If-None-Match", equalTo("\"d3be5b35b8d84ef7ac03c0cc9c94ed81\"")));
        }
    }
}
