package org.jenkinsci.plugins.github_branch_source;

import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.common.SingleRootFileSource;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.Response;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHException;
import org.kohsuke.github.GHRef;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.PagedIterator;
import org.kohsuke.github.PagedIterable;
import org.mockito.Mockito;


import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.*;

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

    public GithubSCMSourceTagsTest() {
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
        source.setApiUri("http://localhost:" + githubApi.port());
        source.setTraits(Arrays.asList(new BranchDiscoveryTrait(true, true), new ForkPullRequestDiscoveryTrait(EnumSet.of(ChangeRequestCheckoutStrategy.MERGE), new ForkPullRequestDiscoveryTrait.TrustContributors())));
        github = Connector.connect("http://localhost:" + githubApi.port(), null);
        repo = github.getRepository("cloudbeers/yolo");
    }

    @Test
    @Issue("JENKINS-54403")
    public void testMissingSingleTag() throws IOException {
        // Scenario: a single tag which does not exist
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);

        Mockito.when(mockSCMHeadObserver.getIncludes()).thenReturn(Collections
                .singleton(new GitHubTagSCMHead("non-existent-tag", System.currentTimeMillis())));
        GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, mockSCMHeadObserver);
        context.wantTags(true);
        GitHubSCMSourceRequest request = context.newRequest(new GitHubSCMSource("cloudbeers", "yolo", null, false), null);
        Iterator<GHRef> tags = new GitHubSCMSource.LazyTags(request, repo).iterator();

        //Expected: No tag is found so an empty list
        assertFalse(tags.hasNext());
    }

    @Test
    @Issue("JENKINS-54403")
    public void testExistentSingleTag() throws IOException {
        // Scenario: A single tag which does exist
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);

        Mockito.when(mockSCMHeadObserver.getIncludes()).thenReturn(Collections
                .singleton(new GitHubTagSCMHead("existent-tag", System.currentTimeMillis())));
        GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, mockSCMHeadObserver);
        context.wantTags(true);
        GitHubSCMSourceRequest request = context.newRequest(new GitHubSCMSource("cloudbeers", "yolo", null, false), null);
        Iterator<GHRef> tags = new GitHubSCMSource.LazyTags(request, repo).iterator();

        //Expected: single tag is found and is named existent-tag
        assertTrue(tags.hasNext());
        assertEquals("refs/tags/existent-tag", tags.next().getRef());
        assertFalse(tags.hasNext());
    }

    @Test
    public void testThrownErrorSingleTagGHFileNotFound() throws IOException {
        // Scenario: A single tag but getting back a FileNotFound when calling getRef
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);
        Error e = new Error("Bad Tag Request", new GHFileNotFoundException());
        Mockito.when(mockSCMHeadObserver.getIncludes()).thenReturn(Collections
                .singleton(new GitHubTagSCMHead("existent-tag", System.currentTimeMillis())));
        GHRepository repoSpy = Mockito.spy(repo);
        GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, mockSCMHeadObserver);
        context.wantTags(true);
        GitHubSCMSourceRequest request = context.newRequest(new GitHubSCMSource("cloudbeers", "yolo", null, false), null);
        Mockito.doThrow(e).when(repoSpy).getRef("tags/existent-tag");
        Iterator<GHRef> tags = new GitHubSCMSource.LazyTags(request, repoSpy).iterator();

        //Expected: No tag is found so an empty list
        assertFalse(tags.hasNext());
    }

    @Test
    public void testThrownErrorSingleTagOtherException() throws IOException {
        // Scenario: A single tag but getting back another Error when calling getRef
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);
        Error expectedError = new Error("Bad Tag Request", new RuntimeException());
        Mockito.when(mockSCMHeadObserver.getIncludes()).thenReturn(Collections
                .singleton(new GitHubTagSCMHead("existent-tag", System.currentTimeMillis())));
        GHRepository repoSpy = Mockito.spy(repo);
        GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, mockSCMHeadObserver);
        context.wantTags(true);
        GitHubSCMSourceRequest request = context.newRequest(new GitHubSCMSource("cloudbeers", "yolo", null, false), null);
        Mockito.doThrow(expectedError).when(repoSpy).getRef("tags/existent-tag");

        //Expected: When getting the tag, an error is thrown so we have to catch it
        try{
            Iterator<GHRef> tags = new GitHubSCMSource.LazyTags(request, repoSpy).iterator();
            fail("This should throw an exception");
        }
        catch(Error e){
            //Error is expected here so this is "success"
            assertEquals("Bad Tag Request", e.getMessage());
        }

    }

    @Test
    public void testExistingMultipleTags() throws IOException {
        // Scenario: Requesting multiple tags
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);

        Mockito.when(mockSCMHeadObserver.getIncludes()).thenReturn(
                new HashSet<>(Arrays.asList(
                        new GitHubTagSCMHead("existent-multiple-tags1", System.currentTimeMillis()),
                        new GitHubTagSCMHead("existent-multiple-tags2", System.currentTimeMillis()))));
        GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, mockSCMHeadObserver);
        context.wantTags(true);
        GitHubSCMSourceRequest request = context.newRequest(new GitHubSCMSource("cloudbeers", "yolo", null, false), null);
        Iterator<GHRef> tags = new GitHubSCMSource.LazyTags(request, repo).iterator();

        //Expected: When getting the tags, we should get both tags and then we are failing on trying to get a 3rd tag or remove the iterator
        assertTrue(tags.hasNext());
        assertEquals("refs/tags/existent-multiple-tags1", tags.next().getRef());
        assertTrue(tags.hasNext());
        assertEquals("refs/tags/existent-multiple-tags2", tags.next().getRef());
        assertFalse(tags.hasNext());
        try{
            tags.next();
            fail("This should throw an exception");
        }
        catch(NoSuchElementException e){
            //Error is expected here so this is "success"
            assertNotEquals("This should throw an exception", e.getMessage());
        }
        try{
            tags.remove();
            fail("This should throw an exception");
        }
        catch(UnsupportedOperationException e){
            //Error is expected here so this is "success"
            assertNotEquals("This should throw an exception", e.getMessage());
        }
    }

    @Test
    public void testExistingMultipleTagsGHFileNotFoundExceptionIteratable() throws IOException {
        // Scenario: Requesting multiple tags but a FileNotFound is thrown
        // on the first returning the iterator and then an IO error is thrown on the iterator creation
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);
        Error expectedError = new Error("Bad Tag Request", new GHFileNotFoundException());
        Error expectedError2 = new Error("Bad Tag Request IOError", new IOException());
        Mockito.when(mockSCMHeadObserver.getIncludes()).thenReturn(
                new HashSet<>(Arrays.asList(
                        new GitHubTagSCMHead("existent-multiple-tags1", System.currentTimeMillis()),
                        new GitHubTagSCMHead("existent-multiple-tags2", System.currentTimeMillis()))));
        GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, mockSCMHeadObserver);
        context.wantTags(true);
        GitHubSCMSourceRequest request = context.newRequest(new GitHubSCMSource("cloudbeers", "yolo", null, false), null);
        GHRepository repoSpy = Mockito.spy(repo);
        PagedIterable<GHRef> iterableSpy = Mockito.spy(repoSpy.listRefs("tags"));
        Mockito.when(repoSpy.listRefs("tags")).thenReturn(iterableSpy);
        Mockito.when(iterableSpy.iterator()).thenThrow(expectedError).thenThrow(expectedError2);

        //Expected: When initially getting multiple tags, there will then be a thrown filenotfound which returns an empty list
        //Then for the second tag iterator created it returns an IO error
        Iterator<GHRef> tags = new GitHubSCMSource.LazyTags(request, repoSpy).iterator();
        assertEquals(Collections.<GHRef>emptyList().iterator(), tags);
        try{
            Iterator<GHRef> tags2 = new GitHubSCMSource.LazyTags(request, repoSpy).iterator();
            fail("This should throw an exception");
        }
        catch(Error e){
            assertEquals("Bad Tag Request IOError", e.getMessage());
        }
    }

    @Test
    public void testExistingMultipleTagsIteratorGHFileNotFoundExceptionOnHasNext() throws IOException {
        // Scenario: multiple tags but returns a filenotfound on the first hasNext
        //and returns a IO error on the second hasNext
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);
        Error expectedError = new Error("Bad Tag Request", new GHFileNotFoundException());
        Error expectedError2 = new Error("Bad Tag Request IOError", new IOException());
        Mockito.when(mockSCMHeadObserver.getIncludes()).thenReturn(
                new HashSet<>(Arrays.asList(
                        new GitHubTagSCMHead("existent-multiple-tags1", System.currentTimeMillis()),
                        new GitHubTagSCMHead("existent-multiple-tags2", System.currentTimeMillis()))));
        GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, mockSCMHeadObserver);
        context.wantTags(true);
        GitHubSCMSourceRequest request = context.newRequest(new GitHubSCMSource("cloudbeers", "yolo", null, false), null);
        GHRepository repoSpy = Mockito.spy(repo);
        PagedIterable<GHRef> iterableSpy = Mockito.spy(repoSpy.listRefs("tags"));
        Mockito.when(repoSpy.listRefs("tags")).thenReturn(iterableSpy);

        PagedIterator<GHRef> iteratorSpy = Mockito.spy(iterableSpy.iterator());
        Mockito.when(iterableSpy.iterator()).thenReturn(iteratorSpy);
        Mockito.when(iteratorSpy.hasNext()).thenThrow(expectedError).thenThrow(expectedError2);

        //Expected: When initially getting multiple tags, return a filenotfound on hasNext which means it will get an empty list
        //Then return a IO error on the second hasNext
        Iterator<GHRef> tags = new GitHubSCMSource.LazyTags(request, repoSpy).iterator();
        Iterator<GHRef> tags2 = new GitHubSCMSource.LazyTags(request, repoSpy).iterator();
        assertFalse(tags.hasNext());
        try{
            tags.hasNext();
            fail("This should throw an exception");
        }
        catch(Error e){
            assertEquals("Bad Tag Request IOError", e.getMessage());
        }
    }

    @Test
    public void testExistingMultipleTagsIteratorGHExceptionOnHasNextAndHasAtLeastOne() throws IOException {
        // Scenario: multiple tags but returns a GHException and found at least one tag
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);
        Exception expectedError = new GHException("Bad Tag Request");
        Mockito.when(mockSCMHeadObserver.getIncludes()).thenReturn(
                new HashSet<>(Arrays.asList(
                        new GitHubTagSCMHead("existent-multiple-tags1", System.currentTimeMillis()),
                        new GitHubTagSCMHead("existent-multiple-tags2", System.currentTimeMillis()))));
        GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, mockSCMHeadObserver);
        context.wantTags(true);
        GitHubSCMSourceRequest request = context.newRequest(new GitHubSCMSource("cloudbeers", "yolo", null, false), null);
        GHRepository repoSpy = Mockito.spy(repo);
        PagedIterable<GHRef> iterableSpy = Mockito.spy(repoSpy.listRefs("tags"));
        Mockito.when(repoSpy.listRefs("tags")).thenReturn(iterableSpy);

        PagedIterator<GHRef> iteratorSpy = Mockito.spy(iterableSpy.iterator());
        Mockito.when(iterableSpy.iterator()).thenReturn(iteratorSpy);
        Iterator<GHRef> tags = new GitHubSCMSource.LazyTags(request, repoSpy).iterator();
        Mockito.when(iteratorSpy.hasNext()).thenReturn(true).thenThrow(expectedError);

        //Expected: First call to hasNext should work true and then will throw an error
        try{
            //First Call is fine
            tags.hasNext();
            //Second Call fails
            tags.hasNext();
            fail("This should throw an exception");
        }
        catch(GHException e){
            assertEquals("Bad Tag Request", e.getMessage());
        }
    }

    @Test
    public void testExistingMultipleTagsIteratorGHExceptionOnHasNextAndDoesNotHaveOne() throws IOException {
        // Scenario: multiple tags but returns a GHException on the first tag
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);
        Exception expectedError = new GHException("Bad Tag Request");
        Mockito.when(mockSCMHeadObserver.getIncludes()).thenReturn(
                new HashSet<>(Arrays.asList(
                        new GitHubTagSCMHead("existent-multiple-tags1", System.currentTimeMillis()),
                        new GitHubTagSCMHead("existent-multiple-tags2", System.currentTimeMillis()))));
        GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, mockSCMHeadObserver);
        context.wantTags(true);
        GitHubSCMSourceRequest request = context.newRequest(new GitHubSCMSource("cloudbeers", "yolo", null, false), null);
        GHRepository repoSpy = Mockito.spy(repo);
        PagedIterable<GHRef> iterableSpy = Mockito.spy(repoSpy.listRefs("tags"));
        Mockito.when(repoSpy.listRefs("tags")).thenReturn(iterableSpy);

        PagedIterator<GHRef> iteratorSpy = Mockito.spy(iterableSpy.iterator());
        Mockito.when(iterableSpy.iterator()).thenReturn(iteratorSpy);
        Iterator<GHRef> tags = new GitHubSCMSource.LazyTags(request, repoSpy).iterator();
        Mockito.when(iteratorSpy.hasNext()).thenThrow(expectedError);

        //Expected: First call to hasNext throws the GHException
        try{
            tags.hasNext();
            fail("This should throw an exception");
        }
        catch(GHException e){
            assertEquals("Bad Tag Request", e.getMessage());
        }
    }

    @Test
    public void testExistingMultipleTagsIteratorGHExceptionOnHasNextButThrowsFileNotFoundOnGetRefs() throws IOException {
        // Scenario: multiple tags but catches a GH exception on hasNext and then
        // FilenotFound on getRefs
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);
        Exception expectedError = new GHException("Bad Tag Request");
        Exception expectedGetRefError = new FileNotFoundException("Bad Tag Ref Request");
        Mockito.when(mockSCMHeadObserver.getIncludes()).thenReturn(
                new HashSet<>(Arrays.asList(
                        new GitHubTagSCMHead("existent-multiple-tags1", System.currentTimeMillis()),
                        new GitHubTagSCMHead("existent-multiple-tags2", System.currentTimeMillis()))));
        GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, mockSCMHeadObserver);
        context.wantTags(true);
        GitHubSCMSourceRequest request = context.newRequest(new GitHubSCMSource("cloudbeers", "yolo", null, false), null);
        GHRepository repoSpy = Mockito.spy(repo);
        PagedIterable<GHRef> iterableSpy = Mockito.spy(repoSpy.listRefs("tags"));
        Mockito.when(repoSpy.listRefs("tags")).thenReturn(iterableSpy);

        PagedIterator<GHRef> iteratorSpy = Mockito.spy(iterableSpy.iterator());
        Mockito.when(iterableSpy.iterator()).thenReturn(iteratorSpy);
        Iterator<GHRef> tags = new GitHubSCMSource.LazyTags(request, repoSpy).iterator();
        Mockito.when(iteratorSpy.hasNext()).thenThrow(expectedError);
        Mockito.when(repoSpy.getRefs("tags")).thenThrow(expectedGetRefError);

        //Expected: First call to hasNext throws a GHException and then returns a FileNotFound on getRefs so it returns an empty list
        assertFalse(tags.hasNext());
    }

    @Test
    public void testExistingMultipleTagsIteratorGHExceptionOnHasNextButThrowsIOErrorOnGetRefs() throws IOException {
        // Scenario: making a request for a multiple tags but catches a GH exception on hasNext
        // and then throws on IO error getRefs
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);
        Exception expectedError = new GHException("Bad Tag Request");
        Exception expectedGetRefError = new IOException("Bad Tag Ref Request");
        Mockito.when(mockSCMHeadObserver.getIncludes()).thenReturn(
                new HashSet<>(Arrays.asList(
                        new GitHubTagSCMHead("existent-multiple-tags1", System.currentTimeMillis()),
                        new GitHubTagSCMHead("existent-multiple-tags2", System.currentTimeMillis()))));
        GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, mockSCMHeadObserver);
        context.wantTags(true);
        GitHubSCMSourceRequest request = context.newRequest(new GitHubSCMSource("cloudbeers", "yolo", null, false), null);
        GHRepository repoSpy = Mockito.spy(repo);
        PagedIterable<GHRef> iterableSpy = Mockito.spy(repoSpy.listRefs("tags"));
        Mockito.when(repoSpy.listRefs("tags")).thenReturn(iterableSpy);

        PagedIterator<GHRef> iteratorSpy = Mockito.spy(iterableSpy.iterator());
        Mockito.when(iterableSpy.iterator()).thenReturn(iteratorSpy);
        Iterator<GHRef> tags = new GitHubSCMSource.LazyTags(request, repoSpy).iterator();
        Mockito.when(iteratorSpy.hasNext()).thenThrow(expectedError);
        Mockito.when(repoSpy.getRefs("tags")).thenThrow(expectedGetRefError);

        //Expected: First call to hasNext throws a GHException and then returns a IO exception on getRefs so it returns an error
        try{
            tags.hasNext();
            fail("This should throw an exception");
        }
        catch(GHException e){
            //We suppress the new GetRef error and then throw the original one
            assertEquals("Bad Tag Request", e.getMessage());
        }
    }
}
