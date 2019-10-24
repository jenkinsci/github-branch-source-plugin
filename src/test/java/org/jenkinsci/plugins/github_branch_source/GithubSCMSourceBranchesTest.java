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
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
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
import org.kohsuke.github.GHException;
import org.kohsuke.github.GHRef;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GHBranch;
import org.kohsuke.github.PagedIterator;
import org.kohsuke.github.PagedIterable;
import org.mockito.Mockito;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.*;

public class GithubSCMSourceBranchesTest {

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

    public GithubSCMSourceBranchesTest() {
        this.source = new GitHubSCMSource("cloudbeers", "yolo", null, false);
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
    public void testMissingSingleBranch () throws IOException {
        // Situation: Hitting the Github API for a branch and getting a 404
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);
        Mockito.when(mockSCMHeadObserver.getIncludes()).thenReturn(Collections
                .singleton(new BranchSCMHead("non-existent-branch")));
        GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, mockSCMHeadObserver);
        context.wantBranches(true);
        GitHubSCMSourceRequest request = context.newRequest(new GitHubSCMSource("cloudbeers", "yolo", null, false), null);
        Iterator<GHBranch> branches = new GitHubSCMSource.LazyBranches(request, repo).iterator();
        // Expected: In the iterator will be empty
        assertFalse(branches.hasNext());
    }

    @Test
    public void testExistentSingleBranch () throws IOException {
        // Situation: Hitting the Github API for a branch and getting an existing branch
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);
        Mockito.when(mockSCMHeadObserver.getIncludes()).thenReturn(Collections
                .singleton(new BranchSCMHead("existent-branch")));
        GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, mockSCMHeadObserver);
        context.wantBranches(true);
        GitHubSCMSourceRequest request = context.newRequest(new GitHubSCMSource("cloudbeers", "yolo", null, false), null);
        Iterator<GHBranch> branches = new GitHubSCMSource.LazyBranches(request, repo).iterator();
        // Expected: In the iterator will be a single branch named existent-branch
        assertTrue(branches.hasNext());
        assertEquals("existent-branch", branches.next().getName());
        assertFalse(branches.hasNext());
    }

    @Test
    public void testThrownErrorSingleBranchException() throws IOException {
        // Situation: When sending a request for a branch which exists, throw a GHNotFoundException
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);
        Error e = new Error("Bad Branch Request", new GHFileNotFoundException());
        Mockito.when(mockSCMHeadObserver.getIncludes()).thenReturn(Collections
                .singleton(new BranchSCMHead("existent-branch")));
        GHRepository repoSpy = Mockito.spy(repo);
        GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, mockSCMHeadObserver);
        context.wantBranches(true);
        GitHubSCMSourceRequest request = context.newRequest(new GitHubSCMSource("cloudbeers", "yolo", null, false), null);
        Mockito.doThrow(e).when(repoSpy).getRef("branches/existent-branch");
        // Expected: This will throw an error when requesting a branch
        try{
            Iterator<GHBranch>  branches = new GitHubSCMSource.LazyBranches(request, repoSpy).iterator();
            fail("This should throw an exception");
        }
        catch(Error error){
            //Error is expected here so this is "success"
            assertEquals("Bad Branch Request", e.getMessage());
        }

    }

    @Test
    public void testExistingMultipleBranchesWithDefaultInPosition1() throws IOException {
        // Situation: Hitting github and getting back multiple branches where master is first in the lst position
        githubApi.stubFor(
                get(urlEqualTo("/repos/cloudbeers/yolo/branches"))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "mapping-yolo-branches-existent-multiple-branches-master1.json; charset=utf-8")
                                        .withBodyFile("body-yolo-branches-existent-multiple-branches-master1.json")));
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);
        GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, mockSCMHeadObserver);
        context.wantBranches(true);
        GitHubSCMSourceRequest request = context.newRequest(new GitHubSCMSource("cloudbeers", "yolo", null, false), null);
        Iterator<GHBranch> branches = new GitHubSCMSource.LazyBranches(request, repo).iterator();
        // Expected: In the iterator will be a multiple branches named nexistent-branch1(because it is alphabetically sorted first)
        // and master
        assertTrue(branches.hasNext());
        assertEquals("master", branches.next().getName());
        assertTrue(branches.hasNext());
        assertEquals("nexistent-branch1", branches.next().getName());
        assertFalse(branches.hasNext());
    }

    @Test
    public void testExistingMultipleBranchesWithDefaultInPosition2() throws IOException {
        // Situation: Hitting github and getting back multiple branches where master is first in the 2nd position
        githubApi.stubFor(
                get(urlEqualTo("/repos/cloudbeers/yolo/branches"))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "mapping-yolo-branches-existent-multiple-branches-master2.json; charset=utf-8")
                                        .withBodyFile("body-yolo-branches-existent-multiple-branches-master2.json")));
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);
        GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, mockSCMHeadObserver);
        context.wantTags(true);
        GitHubSCMSourceRequest request = context.newRequest(new GitHubSCMSource("cloudbeers", "yolo", null, false), null);
        Iterator<GHBranch> branches = new GitHubSCMSource.LazyBranches(request, repo).iterator();
        // Expected: In the iterator will be a multiple branches named existent-branch2 and master
        assertTrue(branches.hasNext());
        assertEquals("master", branches.next().getName());
        assertTrue(branches.hasNext());
        assertEquals("existent-branch2", branches.next().getName());
    }

    @Test
    public void testExistingMultipleBranchesWithNoDefault() throws IOException {
        // Situation: Hitting github and getting back multiple branches where master is not in the list
        githubApi.stubFor(
                get(urlEqualTo("/repos/cloudbeers/yolo/branches"))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "mapping-yolo-branches-existent-multiple-branches-no-master.json; charset=utf-8")
                                        .withBodyFile("body-yolo-branches-existent-multiple-branches-no-master.json")));
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);
        GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, mockSCMHeadObserver);
        context.wantTags(true);
        GitHubSCMSourceRequest request = context.newRequest(new GitHubSCMSource("cloudbeers", "yolo", null, false), null);
        Iterator<GHBranch> branches = new GitHubSCMSource.LazyBranches(request, repo).iterator();
        // Expected: In the iterator will be a multiple branches named existent-branch2 and existent-branch1
        assertTrue(branches.hasNext());
        assertEquals("existent-branch1", branches.next().getName());
        assertTrue(branches.hasNext());
        assertEquals("existent-branch2", branches.next().getName());
    }

    @Test
    public void testExistingMultipleBranchesWithThrownError() throws IOException {
        // Situation: Hitting github and getting back multiple branches but throws an I/O error
        githubApi.stubFor(
                get(urlEqualTo("/repos/cloudbeers/yolo/branches"))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "mapping-yolo-branches-existent-multiple-branches-no-master.json; charset=utf-8")
                                        .withBodyFile("body-yolo-branches-existent-multiple-branches-no-master.json")));
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);
        GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, mockSCMHeadObserver);
        context.wantTags(true);
        GitHubSCMSourceRequest request = context.newRequest(new GitHubSCMSource("cloudbeers", "yolo", null, false), null);
        GHRepository repoSpy = Mockito.spy(repo);
        IOException error = new IOException("Thrown Branch Error");
        Mockito.when(repoSpy.getBranches()).thenThrow(error);
        // Expected: In the iterator will throw an error when calling getBranches
        try{
            Iterator<GHBranch> branches = new GitHubSCMSource.LazyBranches(request, repoSpy).iterator();
            fail("This should throw an exception");
        }
        catch(Exception e){
            //We swallow the new GetRef error and then throw the original one for some reason...
            assertEquals("java.io.IOException: Thrown Branch Error", e.getMessage());
        }

    }
}
