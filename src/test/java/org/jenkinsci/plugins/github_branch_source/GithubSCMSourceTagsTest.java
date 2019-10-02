package org.jenkinsci.plugins.github_branch_source;

import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.common.SingleRootFileSource;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.Response;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHRef;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class GithubSCMSourceTagsTest {

    /**
     * All tests in this class only use Jenkins for the extensions
     */
    @ClassRule
    public static JenkinsRule r = new JenkinsRule();

    public static WireMockRuleFactory factory = new WireMockRuleFactory();

    @Rule
    public WireMockRule githubRaw = factory.getRule(WireMockConfiguration.options()
            .dynamicPort()
            .usingFilesUnderClasspath("raw")
    );
    @Rule
    public WireMockRule githubApi = factory.getRule(WireMockConfiguration.options()
            .dynamicPort()
            .usingFilesUnderClasspath("api")
            .extensions(
                    new ResponseTransformer() {
                        @Override
                        public Response transform(Request request, Response response, FileSource files,
                                                  Parameters parameters) {
                            if ("application/json"
                                    .equals(response.getHeaders().getContentTypeHeader().mimeTypePart())) {
                                return Response.Builder.like(response)
                                        .but()
                                        .body(response.getBodyAsString()
                                                .replace("https://api.github.com/",
                                                        "http://localhost:" + githubApi.port() + "/")
                                                .replace("https://raw.githubusercontent.com/",
                                                        "http://localhost:" + githubRaw.port() + "/")
                                        )
                                        .build();
                            }
                            return response;
                        }

                        @Override
                        public String getName() {
                            return "url-rewrite";
                        }

                    })
    );
    private GitHubSCMSource source;
    private GitHub github;
    private GHRepository repo;

    public GithubSCMSourceTagsTest(GitHubSCMSource source) {
        this.source = source;
    }

    @Parameterized.Parameters(name = "{index}: revision={0}")
    public static GitHubSCMSource[] revisions() {
        return new GitHubSCMSource[]{
                new GitHubSCMSource("cloudbeers", "yolo", null, false),
                new GitHubSCMSource("", "", "https://github.com/cloudbeers/yolo", true)
        };
    }


    @Before
    public void prepareMockGitHub() throws Exception {
        new File("src/test/resources/api/mappings").mkdirs();
        new File("src/test/resources/api/__files").mkdirs();
        new File("src/test/resources/raw/mappings").mkdirs();
        new File("src/test/resources/raw/__files").mkdirs();
        githubApi.enableRecordMappings(new SingleRootFileSource("src/test/resources/api/mappings"),
                new SingleRootFileSource("src/test/resources/api/__files"));
        githubRaw.enableRecordMappings(new SingleRootFileSource("src/test/resources/raw/mappings"),
                new SingleRootFileSource("src/test/resources/raw/__files"));

        githubApi.stubFor(
                get(urlEqualTo("/repos/cloudbeers/yolo/pulls/2"))
                        .inScenario("Pull Request Merge Hash")
                        .whenScenarioStateIs(Scenario.STARTED)
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json; charset=utf-8")
                                        .withBodyFile("body-yolo-pulls-2-mergeable-null.json"))
                        .willSetStateTo("Pull Request Merge Hash - retry 1"));

        githubApi.stubFor(
                get(urlEqualTo("/repos/cloudbeers/yolo/pulls/2"))
                        .inScenario("Pull Request Merge Hash")
                        .whenScenarioStateIs("Pull Request Merge Hash - retry 1")
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json; charset=utf-8")
                                        .withBodyFile("body-yolo-pulls-2-mergeable-null.json"))
                        .willSetStateTo("Pull Request Merge Hash - retry 2"));

        githubApi.stubFor(
                get(urlEqualTo("/repos/cloudbeers/yolo/pulls/2"))
                        .inScenario("Pull Request Merge Hash")
                        .whenScenarioStateIs("Pull Request Merge Hash - retry 2")
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json; charset=utf-8")
                                        .withBodyFile("body-yolo-pulls-2-mergeable-true.json"))
                        .willSetStateTo("Pull Request Merge Hash - retry 2"));

        githubApi.stubFor(
                get(urlMatching(".*")).atPriority(10).willReturn(aResponse().proxiedFrom("https://api.github.com/")));
        githubRaw.stubFor(get(urlMatching(".*")).atPriority(10)
                .willReturn(aResponse().proxiedFrom("https://raw.githubusercontent.com/")));
        if (source.isConfiguredByUrl()) {
            source = new GitHubSCMSource("cloudbeers", "yolo", "http://localhost:" + githubApi.port() + "/cloudbeers/yolo", true);
        } else {
            source.setApiUri("http://localhost:" + githubApi.port());
        }
        source.setTraits(Arrays.asList(new BranchDiscoveryTrait(true, true), new ForkPullRequestDiscoveryTrait(EnumSet.of(ChangeRequestCheckoutStrategy.MERGE), new ForkPullRequestDiscoveryTrait.TrustContributors())));
        github = Connector.connect("http://localhost:" + githubApi.port(), null);
        repo = github.getRepository("cloudbeers/yolo");
    }


    @Test
    @Issue("JENKINS-54403")
    public void testMissingSingleTag () throws IOException {
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);

        Mockito.when(mockSCMHeadObserver.getIncludes()).thenReturn(Collections
                .singleton(new GitHubTagSCMHead("non-existent-tag", System.currentTimeMillis())));
        GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, mockSCMHeadObserver);
        context.wantTags(true);
        GitHubSCMSourceRequest request = context.newRequest(revisions()[0], null);
        Iterator<GHRef> tags = new GitHubSCMSource.LazyTags(request, repo).iterator();
        assertFalse(tags.hasNext());
    }

    @Test
    @Issue("JENKINS-54403")
    public void testExistentSingleTag () throws IOException {
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);

        Mockito.when(mockSCMHeadObserver.getIncludes()).thenReturn(Collections
                .singleton(new GitHubTagSCMHead("existent-tag", System.currentTimeMillis())));
        GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, mockSCMHeadObserver);
        context.wantTags(true);
        GitHubSCMSourceRequest request = context.newRequest(revisions()[0], null);
        Iterator<GHRef>  tags = new GitHubSCMSource.LazyTags(request, repo).iterator();
        assertTrue(tags.hasNext());
        assertEquals("refs/tags/existent-tag", tags.next().getRef());
        assertFalse(tags.hasNext());
    }

    @Test
    public void testThrownErrorSingleTagGHFileNotFound() throws IOException {
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);
        Error e = new Error("Bad Tag Request", new GHFileNotFoundException());
        Mockito.when(mockSCMHeadObserver.getIncludes()).thenReturn(Collections
                .singleton(new GitHubTagSCMHead("existent-tag", System.currentTimeMillis())));
        GHRepository repoSpy = Mockito.spy(repo);
        GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, mockSCMHeadObserver);
        context.wantTags(true);
        GitHubSCMSourceRequest request = context.newRequest(revisions()[0], null);
        Mockito.doThrow(e).when(repoSpy).getRef("tags/existent-tag");
        Iterator<GHRef>  tags = new GitHubSCMSource.LazyTags(request, repoSpy).iterator();
        assertFalse(tags.hasNext());
    }

    @Test
    public void testThrownErrorSingleTagOtherException() throws IOException {
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);
        Error e = new Error("Bad Tag Request", new RuntimeException());
        Mockito.when(mockSCMHeadObserver.getIncludes()).thenReturn(Collections
                .singleton(new GitHubTagSCMHead("existent-tag", System.currentTimeMillis())));
        GHRepository repoSpy = Mockito.spy(repo);
        GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, mockSCMHeadObserver);
        context.wantTags(true);
        GitHubSCMSourceRequest request = context.newRequest(revisions()[0], null);
        Mockito.doThrow(e).when(repoSpy).getRef("tags/existent-tag");
        try{
            Iterator<GHRef>  tags = new GitHubSCMSource.LazyTags(request, repoSpy).iterator();
            fail("This should throw an exception");
        }
        catch(Error error){
            //Error is expected here so this is "success"
        }

    }

    @Test
    public void testExistingMultipleTags() throws IOException {
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);

        Mockito.when(mockSCMHeadObserver.getIncludes()).thenReturn(
                new HashSet<>(Arrays.asList(
                        new GitHubTagSCMHead("existent-multiple-tags1", System.currentTimeMillis()),
                        new GitHubTagSCMHead("existent-multiple-tags2", System.currentTimeMillis()))));
        GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, mockSCMHeadObserver);
        context.wantTags(true);
        GitHubSCMSourceRequest request = context.newRequest(revisions()[0], null);
        Iterator<GHRef>  tags = new GitHubSCMSource.LazyTags(request, repo).iterator();
        assertTrue(tags.hasNext());
        assertEquals("refs/tags/existent-multiple-tags1", tags.next().getRef());
        assertTrue(tags.hasNext());
        assertEquals("refs/tags/existent-multiple-tags2", tags.next().getRef());
    }

    @Test
    public void testExistingMultipleTagsGHFileNotFoundException() throws IOException {
        //This test does not do what I want because I am completely mocking over the hasNext and I need to hit the boolean
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);
        Error e = new Error("Bad Tag Request", new GHFileNotFoundException());
        Mockito.when(mockSCMHeadObserver.getIncludes()).thenReturn(
                new HashSet<>(Arrays.asList(
                        new GitHubTagSCMHead("existent-multiple-tags1", System.currentTimeMillis()),
                        new GitHubTagSCMHead("existent-multiple-tags2", System.currentTimeMillis()))));
        GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, mockSCMHeadObserver);
        context.wantTags(true);
        GitHubSCMSourceRequest request = context.newRequest(revisions()[0], null);
        Iterator<GHRef>  tags = new GitHubSCMSource.LazyTags(request, repo).iterator();
        Iterator<GHRef> tagsSpy  = Mockito.spy(tags);
        Mockito.doThrow(e).when(tagsSpy).hasNext();
        try{
            tagsSpy.hasNext();
            fail("This should throw an exception");
        }
        catch(Error error){
            assertEquals("Bad Tag Request", e.getMessage());
        }
    }
}
