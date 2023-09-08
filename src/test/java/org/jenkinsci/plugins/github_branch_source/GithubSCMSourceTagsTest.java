package org.jenkinsci.plugins.github_branch_source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import jenkins.scm.api.SCMHeadObserver;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.kohsuke.github.GHException;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHRef;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.PagedIterable;
import org.kohsuke.github.PagedIterator;
import org.mockito.Mockito;

public class GithubSCMSourceTagsTest extends GitSCMSourceBase {

    public GithubSCMSourceTagsTest() {
        this.source = new GitHubSCMSource("cloudbeers", "yolo", null, false);
    }

    @Test
    @Issue("JENKINS-54403")
    public void testMissingSingleTag() throws IOException {
        // Scenario: a single tag which does not exist
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);

        Mockito.when(mockSCMHeadObserver.getIncludes())
                .thenReturn(
                        Collections.singleton(new GitHubTagSCMHead("non-existent-tag", System.currentTimeMillis())));
        GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, mockSCMHeadObserver);
        context.wantTags(true);
        GitHubSCMSourceRequest request =
                context.newRequest(new GitHubSCMSource("cloudbeers", "yolo", null, false), null);
        Iterator<GHRef> tags = new GitHubSCMSource.LazyTags(request, repo).iterator();

        // Expected: No tag is found so an empty list
        assertFalse(tags.hasNext());
    }

    @Test
    @Issue("JENKINS-54403")
    public void testExistentSingleTag() throws IOException {
        // Scenario: A single tag which does exist
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);

        Mockito.when(mockSCMHeadObserver.getIncludes())
                .thenReturn(Collections.singleton(new GitHubTagSCMHead("existent-tag", System.currentTimeMillis())));
        GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, mockSCMHeadObserver);
        context.wantTags(true);
        GitHubSCMSourceRequest request =
                context.newRequest(new GitHubSCMSource("cloudbeers", "yolo", null, false), null);
        Iterator<GHRef> tags = new GitHubSCMSource.LazyTags(request, repo).iterator();

        // Expected: single tag is found and is named existent-tag
        assertTrue(tags.hasNext());
        assertEquals("refs/tags/existent-tag", tags.next().getRef());
        assertFalse(tags.hasNext());
    }

    @Test
    public void testThrownErrorSingleTagGHFileNotFound() throws IOException {
        // Scenario: A single tag but getting back a FileNotFound when calling getRef
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);
        Error e = new Error("Bad Tag Request", new GHFileNotFoundException());
        Mockito.when(mockSCMHeadObserver.getIncludes())
                .thenReturn(Collections.singleton(new GitHubTagSCMHead("existent-tag", System.currentTimeMillis())));
        GHRepository repoSpy = Mockito.spy(repo);
        GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, mockSCMHeadObserver);
        context.wantTags(true);
        GitHubSCMSourceRequest request =
                context.newRequest(new GitHubSCMSource("cloudbeers", "yolo", null, false), null);
        Mockito.doThrow(e).when(repoSpy).getRef("tags/existent-tag");
        Iterator<GHRef> tags = new GitHubSCMSource.LazyTags(request, repoSpy).iterator();

        // Expected: No tag is found so an empty list
        assertFalse(tags.hasNext());
    }

    @Test
    public void testThrownErrorSingleTagOtherException() throws IOException {
        // Scenario: A single tag but getting back another Error when calling getRef
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);
        Error expectedError = new Error("Bad Tag Request", new RuntimeException());
        Mockito.when(mockSCMHeadObserver.getIncludes())
                .thenReturn(Collections.singleton(new GitHubTagSCMHead("existent-tag", System.currentTimeMillis())));
        GHRepository repoSpy = Mockito.spy(repo);
        GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, mockSCMHeadObserver);
        context.wantTags(true);
        GitHubSCMSourceRequest request =
                context.newRequest(new GitHubSCMSource("cloudbeers", "yolo", null, false), null);
        Mockito.doThrow(expectedError).when(repoSpy).getRef("tags/existent-tag");

        // Expected: When getting the tag, an error is thrown so we have to catch it
        try {
            Iterator<GHRef> tags = new GitHubSCMSource.LazyTags(request, repoSpy).iterator();
            fail("This should throw an exception");
        } catch (Error e) {
            // Error is expected here so this is "success"
            assertEquals("Bad Tag Request", e.getMessage());
        }
    }

    @Test
    public void testExistingMultipleTags() throws IOException {
        // Scenario: Requesting multiple tags
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);

        Mockito.when(mockSCMHeadObserver.getIncludes())
                .thenReturn(new HashSet<>(Arrays.asList(
                        new GitHubTagSCMHead("existent-multiple-tags1", System.currentTimeMillis()),
                        new GitHubTagSCMHead("existent-multiple-tags2", System.currentTimeMillis()))));
        GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, mockSCMHeadObserver);
        context.wantTags(true);
        GitHubSCMSourceRequest request =
                context.newRequest(new GitHubSCMSource("cloudbeers", "yolo", null, false), null);
        Iterator<GHRef> tags = new GitHubSCMSource.LazyTags(request, repo).iterator();

        // Expected: When getting the tags, we should get both tags and then we are failing on trying to
        // get a 3rd tag or remove the iterator
        assertTrue(tags.hasNext());
        assertEquals("refs/tags/existent-multiple-tags1", tags.next().getRef());
        assertTrue(tags.hasNext());
        assertEquals("refs/tags/existent-multiple-tags2", tags.next().getRef());
        assertFalse(tags.hasNext());
        try {
            tags.next();
            fail("This should throw an exception");
        } catch (NoSuchElementException e) {
            // Error is expected here so this is "success"
            assertNotEquals("This should throw an exception", e.getMessage());
        }
        try {
            tags.remove();
            fail("This should throw an exception");
        } catch (UnsupportedOperationException e) {
            // Error is expected here so this is "success"
            assertNotEquals("This should throw an exception", e.getMessage());
        }
    }

    @Test
    public void testExistingMultipleTagsGHFileNotFoundExceptionIterable() throws IOException {
        // Scenario: Requesting multiple tags but a FileNotFound is thrown
        // on the first returning the iterator and then an IO error is thrown on the iterator creation
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);
        Error expectedError = new Error("Bad Tag Request", new GHFileNotFoundException());
        Error expectedError2 = new Error("Bad Tag Request IOError", new IOException());
        Mockito.when(mockSCMHeadObserver.getIncludes())
                .thenReturn(new HashSet<>(Arrays.asList(
                        new GitHubTagSCMHead("existent-multiple-tags1", System.currentTimeMillis()),
                        new GitHubTagSCMHead("existent-multiple-tags2", System.currentTimeMillis()))));
        GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, mockSCMHeadObserver);
        context.wantTags(true);
        GitHubSCMSourceRequest request =
                context.newRequest(new GitHubSCMSource("cloudbeers", "yolo", null, false), null);
        GHRepository repoSpy = Mockito.spy(repo);
        PagedIterable<GHRef> iterableSpy = (PagedIterable<GHRef>) Mockito.mock(PagedIterable.class);
        Mockito.when(repoSpy.listRefs("tags")).thenReturn(iterableSpy);
        Mockito.when(iterableSpy.iterator()).thenThrow(expectedError).thenThrow(expectedError2);

        // Expected: When initially getting multiple tags, there will then be a thrown filenotfound
        // which returns an empty list
        // Then for the second tag iterator created it returns an IO error
        Iterator<GHRef> tags = new GitHubSCMSource.LazyTags(request, repoSpy).iterator();
        assertEquals(Collections.emptyIterator(), tags);
        try {
            Iterator<GHRef> tags2 = new GitHubSCMSource.LazyTags(request, repoSpy).iterator();
            fail("This should throw an exception");
        } catch (Error e) {
            assertEquals("Bad Tag Request IOError", e.getMessage());
        }
    }

    @Test
    public void testExistingMultipleTagsIteratorGHFileNotFoundExceptionOnHasNext() throws IOException {
        // Scenario: multiple tags but returns a filenotfound on the first hasNext
        // and returns a IO error on the second hasNext
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);
        Error expectedError = new Error("Bad Tag Request", new GHFileNotFoundException());
        Error expectedError2 = new Error("Bad Tag Request IOError", new IOException());
        Mockito.when(mockSCMHeadObserver.getIncludes())
                .thenReturn(new HashSet<>(Arrays.asList(
                        new GitHubTagSCMHead("existent-multiple-tags1", System.currentTimeMillis()),
                        new GitHubTagSCMHead("existent-multiple-tags2", System.currentTimeMillis()))));
        GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, mockSCMHeadObserver);
        context.wantTags(true);
        GitHubSCMSourceRequest request =
                context.newRequest(new GitHubSCMSource("cloudbeers", "yolo", null, false), null);
        GHRepository repoSpy = Mockito.spy(repo);
        PagedIterable<GHRef> iterableSpy = (PagedIterable<GHRef>) Mockito.mock(PagedIterable.class);
        Mockito.when(repoSpy.listRefs("tags")).thenReturn(iterableSpy);

        PagedIterator<GHRef> iteratorSpy = (PagedIterator<GHRef>) Mockito.mock(PagedIterator.class);
        Mockito.when(iterableSpy.iterator()).thenReturn(iteratorSpy);
        Mockito.when(iteratorSpy.hasNext()).thenThrow(expectedError).thenThrow(expectedError2);

        // Expected: When initially getting multiple tags, return a filenotfound on hasNext which means
        // it will get an empty list
        // Then return a IO error on the second hasNext
        Iterator<GHRef> tags = new GitHubSCMSource.LazyTags(request, repoSpy).iterator();
        Iterator<GHRef> tags2 = new GitHubSCMSource.LazyTags(request, repoSpy).iterator();
        assertFalse(tags.hasNext());
        try {
            tags.hasNext();
            fail("This should throw an exception");
        } catch (Error e) {
            assertEquals("Bad Tag Request IOError", e.getMessage());
        }
    }

    @Test
    public void testExistingMultipleTagsIteratorGHExceptionOnHasNextAndHasAtLeastOne() throws IOException {
        // Scenario: multiple tags but returns a GHException and found at least one tag
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);
        Exception expectedError = new GHException("Bad Tag Request");
        Mockito.when(mockSCMHeadObserver.getIncludes())
                .thenReturn(new HashSet<>(Arrays.asList(
                        new GitHubTagSCMHead("existent-multiple-tags1", System.currentTimeMillis()),
                        new GitHubTagSCMHead("existent-multiple-tags2", System.currentTimeMillis()))));
        GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, mockSCMHeadObserver);
        context.wantTags(true);
        GitHubSCMSourceRequest request =
                context.newRequest(new GitHubSCMSource("cloudbeers", "yolo", null, false), null);
        GHRepository repoSpy = Mockito.spy(repo);
        PagedIterable<GHRef> iterableSpy = (PagedIterable<GHRef>) Mockito.mock(PagedIterable.class);
        Mockito.when(repoSpy.listRefs("tags")).thenReturn(iterableSpy);

        PagedIterator<GHRef> iteratorSpy = (PagedIterator<GHRef>) Mockito.mock(PagedIterator.class);
        Mockito.when(iterableSpy.iterator()).thenReturn(iteratorSpy);
        Iterator<GHRef> tags = new GitHubSCMSource.LazyTags(request, repoSpy).iterator();
        Mockito.when(iteratorSpy.hasNext()).thenReturn(true).thenThrow(expectedError);

        // Expected: First call to hasNext should work true and then will throw an error
        try {
            // First Call is fine
            tags.hasNext();
            // Second Call fails
            tags.hasNext();
            fail("This should throw an exception");
        } catch (GHException e) {
            assertEquals("Bad Tag Request", e.getMessage());
        }
    }

    @Test
    public void testExistingMultipleTagsIteratorGHExceptionOnHasNextAndDoesNotHaveOne() throws IOException {
        // Scenario: multiple tags but returns a GHException on the first tag
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);
        Exception expectedError = new GHException("Bad Tag Request");
        Mockito.when(mockSCMHeadObserver.getIncludes())
                .thenReturn(new HashSet<>(Arrays.asList(
                        new GitHubTagSCMHead("existent-multiple-tags1", System.currentTimeMillis()),
                        new GitHubTagSCMHead("existent-multiple-tags2", System.currentTimeMillis()))));
        GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, mockSCMHeadObserver);
        context.wantTags(true);
        GitHubSCMSourceRequest request =
                context.newRequest(new GitHubSCMSource("cloudbeers", "yolo", null, false), null);
        GHRepository repoSpy = Mockito.spy(repo);
        PagedIterable<GHRef> iterableSpy = (PagedIterable<GHRef>) Mockito.mock(PagedIterable.class);
        Mockito.when(repoSpy.listRefs("tags")).thenReturn(iterableSpy).thenCallRealMethod();

        PagedIterator<GHRef> iteratorSpy = (PagedIterator<GHRef>) Mockito.mock(PagedIterator.class);
        Mockito.when(iterableSpy.iterator()).thenReturn(iteratorSpy);
        Iterator<GHRef> tags = new GitHubSCMSource.LazyTags(request, repoSpy).iterator();
        Mockito.when(iteratorSpy.hasNext()).thenThrow(expectedError);

        // Expected: First call to hasNext throws the GHException
        try {
            tags.hasNext();
            fail("This should throw an exception");
        } catch (GHException e) {
            assertEquals("Bad Tag Request", e.getMessage());
        }
    }

    @Test
    public void testExistingMultipleTagsIteratorGHExceptionOnHasNextButThrowsFileNotFoundOnGetRefs()
            throws IOException {
        // Scenario: multiple tags but catches a GH exception on hasNext and then
        // FilenotFound on getRefs
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);
        Exception expectedError = new GHException("Bad Tag Request");
        Exception expectedGetRefError = new FileNotFoundException("Bad Tag Ref Request");
        Mockito.when(mockSCMHeadObserver.getIncludes())
                .thenReturn(new HashSet<>(Arrays.asList(
                        new GitHubTagSCMHead("existent-multiple-tags1", System.currentTimeMillis()),
                        new GitHubTagSCMHead("existent-multiple-tags2", System.currentTimeMillis()))));
        GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, mockSCMHeadObserver);
        context.wantTags(true);
        GitHubSCMSourceRequest request =
                context.newRequest(new GitHubSCMSource("cloudbeers", "yolo", null, false), null);
        GHRepository repoSpy = Mockito.spy(repo);
        PagedIterable<GHRef> iterableSpy = (PagedIterable<GHRef>) Mockito.mock(PagedIterable.class);
        Mockito.when(repoSpy.listRefs("tags")).thenReturn(iterableSpy);

        PagedIterator<GHRef> iteratorSpy = (PagedIterator<GHRef>) Mockito.mock(PagedIterator.class);
        Mockito.when(iterableSpy.iterator()).thenReturn(iteratorSpy);
        Iterator<GHRef> tags = new GitHubSCMSource.LazyTags(request, repoSpy).iterator();
        Mockito.when(iteratorSpy.hasNext()).thenThrow(expectedError);
        Mockito.when(repoSpy.getRefs("tags")).thenThrow(expectedGetRefError);

        // Expected: First call to hasNext throws a GHException and then returns a FileNotFound on
        // getRefs so it returns an empty list
        assertFalse(tags.hasNext());
    }

    @Test
    public void testExistingMultipleTagsIteratorGHExceptionOnHasNextButThrowsIOErrorOnGetRefs() throws IOException {
        // Scenario: making a request for a multiple tags but catches a GH exception on hasNext
        // and then throws on IO error getRefs
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);
        Exception expectedError = new GHException("Bad Tag Request");
        Exception expectedGetRefError = new IOException("Bad Tag Ref Request");
        Mockito.when(mockSCMHeadObserver.getIncludes())
                .thenReturn(new HashSet<>(Arrays.asList(
                        new GitHubTagSCMHead("existent-multiple-tags1", System.currentTimeMillis()),
                        new GitHubTagSCMHead("existent-multiple-tags2", System.currentTimeMillis()))));
        GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, mockSCMHeadObserver);
        context.wantTags(true);
        GitHubSCMSourceRequest request =
                context.newRequest(new GitHubSCMSource("cloudbeers", "yolo", null, false), null);
        GHRepository repoSpy = Mockito.spy(repo);
        PagedIterable<GHRef> iterableSpy = (PagedIterable<GHRef>) Mockito.mock(PagedIterable.class);
        Mockito.when(repoSpy.listRefs("tags")).thenReturn(iterableSpy);

        PagedIterator<GHRef> iteratorSpy = (PagedIterator<GHRef>) Mockito.mock(PagedIterator.class);
        Mockito.when(iterableSpy.iterator()).thenReturn(iteratorSpy);
        Iterator<GHRef> tags = new GitHubSCMSource.LazyTags(request, repoSpy).iterator();
        Mockito.when(iteratorSpy.hasNext()).thenThrow(expectedError);
        Mockito.when(repoSpy.getRefs("tags")).thenThrow(expectedGetRefError);

        // Expected: First call to hasNext throws a GHException and then returns a IO exception on
        // getRefs so it returns an error
        try {
            tags.hasNext();
            fail("This should throw an exception");
        } catch (GHException e) {
            // We suppress the new GetRef error and then throw the original one
            assertEquals("Bad Tag Request", e.getMessage());
        }
    }
}
