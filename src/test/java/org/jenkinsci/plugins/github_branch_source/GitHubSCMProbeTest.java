package org.jenkinsci.plugins.github_branch_source;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
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

import java.io.File;

import static org.junit.Assert.*;
import static com.github.tomakehurst.wiremock.client.WireMock.*;

public class GitHubSCMProbeTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();
    public static WireMockRuleFactory factory = new WireMockRuleFactory();
    @Rule
    public WireMockRule githubApi = factory.getRule(WireMockConfiguration.options().dynamicPort().usingFilesUnderClasspath("api"));
    private GitHubSCMProbe probe;

    @Before
    public void setUp() throws Exception {
        // Clear all caches before each test
        File cacheBaseDir = new File(j.jenkins.getRootDir(),
            GitHubSCMProbe.class.getName() + ".cache");
        if (cacheBaseDir.exists()) {
            FileUtils.cleanDirectory(cacheBaseDir);
        }

        final GitHub github = Connector.connect("http://localhost:" + githubApi.port(), null);

        githubApi.stubFor(
                get(urlEqualTo("/repos/cloudbeers/yolo"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBodyFile("body-cloudbeers-yolo-PucD6.json"))
        );
        final GHRepository repo = github.getRepository("cloudbeers/yolo");
        final PullRequestSCMHead head = new PullRequestSCMHead("PR-1", "cloudbeers", "yolo", "b", 1, new BranchSCMHead("master"), new SCMHeadOrigin.Fork("rsandell"), ChangeRequestCheckoutStrategy.MERGE);
        probe = new GitHubSCMProbe(github, repo,
                head,
                new PullRequestSCMRevision(head, "a", "b"));
    }

    @Issue("JENKINS-54126")
    @Test
    public void statWhenRootIs404() throws Exception {
        githubApi.stubFor(get(urlPathEqualTo("/repos/cloudbeers/yolo/contents/")).willReturn(aResponse().withStatus(404)));
        assertFalse(probe.stat("Jenkinsfile").exists());
    }

    @Issue("JENKINS-54126")
    @Test()
    public void statWhenDirAndRootIs404() throws Exception {
        githubApi.stubFor(get(urlPathEqualTo("/repos/cloudbeers/yolo/contents/")).willReturn(aResponse().withStatus(200)));
        githubApi.stubFor(get(urlPathEqualTo("/repos/cloudbeers/yolo/contents/subdir")).willReturn(aResponse().withStatus(404)));
        assertFalse(probe.stat("subdir/Jenkinsfile").exists());
    }

    @Issue("JENKINS-54126")
    @Test
    public void statWhenDirIs404() throws Exception {
        githubApi.stubFor(get(urlPathEqualTo("/repos/cloudbeers/yolo/contents/subdir")).willReturn(aResponse().withStatus(404)));
        githubApi.stubFor(get(urlPathEqualTo("/repos/cloudbeers/yolo/contents/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBodyFile("body-yolo-contents-8rd37.json")));
        assertFalse(probe.stat("subdir/Jenkinsfile").exists());
    }

    @Issue("JENKINS-54126")
    @Test
    public void statWhenRootIs404AndCacheOnThenOff() throws Exception {
        GitHubSCMSource.setCacheSize(10);

        // JENKINS-54126 happens when:
        // 1. client asks for a resource "Z" that doesn't exist
        // ---> client receives a 404 response from github and caches it.
        // ---> Important: GitHub does not send ETag header for 404 responses.
        // 2. Resource "Z" gets created on GitHub but some means.
        // 3. client (eventually) asks for the resource "Z" again.
        // ---> Since the the client has a cached response without ETag, it sends "If-Modified-Since" header
        // ---> Resource has changed (it was created).
        //
        // ---> EXPECTED: GitHub should respond with 200 and data.
        // ---> ACTUAL: GitHub server lies, responds with incorrect 304 response, telling client that the cached data is still valid.
        // ---> THE BAD: Client cache believes GitHub - uses the previously cached 404 (and even adds the ETag).
        // ---> THE UGLY: Client is now stuck with a bad cached 404, and can't get rid of it until the resource is _updated_ again or the cache is cleared manually.
        //
        // This is the cause of JENKINS-54126. This is a pervasive GitHub server problem.
        // We see it mostly in this one scenario, but it will happen anywhere the server returns a 404.
        // It cannot be reliably detected or mitigated at the level of this plugin.
        //
        // WORKAROUND (implemented in the github-api library):
        // 4. the github-api library recognizes any 404 with ETag as invalid. Does not return it to the client.
        // ---> The github-api library automatically retries the request with "no-cache" to force refresh with valid data.

        // 1.
        githubApi.stubFor(get(urlPathEqualTo("/repos/cloudbeers/yolo/contents/"))
            .withHeader("Cache-Control", containing("max-age"))
            .withHeader("If-None-Match", absent())
            .withHeader("If-Modified-Since", absent())
            .willReturn(aResponse().withStatus(404)
                .withHeader("Date", "Tue, 06 Dec 2019 15:06:25 GMT")));

        // 2. - happens elsewhere

        // 3.
        githubApi.stubFor(get(urlPathEqualTo("/repos/cloudbeers/yolo/contents/"))
            .withHeader("Cache-Control", containing("max-age"))
            .withHeader("If-None-Match", absent())
            .withHeader("If-Modified-Since", equalTo("Tue, 06 Dec 2019 15:06:25 GMT"))
            .willReturn(aResponse().withStatus(304)
                .withHeader("Date", "Tue, 06 Dec 2019 15:06:25 GMT")
                .withHeader("ETag", "something")));

        // 4.
        githubApi.stubFor(get(urlPathEqualTo("/repos/cloudbeers/yolo/contents/"))
            .withHeader("Cache-Control", containing("no-cache"))
            .withHeader("If-Modified-Since", absent())
            .withHeader("If-None-Match", absent())
            .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBodyFile("body-yolo-contents-8rd37.json")));

        // 1.
        assertFalse(probe.stat("README.md").exists());

        // 3.
        // Without 4. this would return false and would stay false.
        assertTrue(probe.stat("README.md").exists());


        // Verify the expected requests were made
        // 1.
        githubApi.verify(RequestPatternBuilder.newRequestPattern(RequestMethod.GET, urlPathEqualTo("/repos/cloudbeers/yolo/contents/"))
                .withHeader("Cache-Control", containing("max-age"))
                .withHeader("If-None-Match", absent())
                .withHeader("If-Modified-Since", absent())
        );

        // 3.
        githubApi.verify(RequestPatternBuilder.newRequestPattern(RequestMethod.GET, urlPathEqualTo("/repos/cloudbeers/yolo/contents/"))
            .withHeader("Cache-Control", containing("max-age"))
            .withHeader("If-None-Match", absent())
            .withHeader("If-Modified-Since", equalTo("Tue, 06 Dec 2019 15:06:25 GMT"))
        );

        // 4.
        githubApi.verify(RequestPatternBuilder.newRequestPattern(RequestMethod.GET, urlPathEqualTo("/repos/cloudbeers/yolo/contents/"))
            .withHeader("Cache-Control", containing("no-cache"))
            .withHeader("If-Modified-Since", absent())
            .withHeader("If-None-Match", absent())
        );
    }

}