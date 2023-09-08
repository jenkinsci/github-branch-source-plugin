package org.jenkinsci.plugins.github_branch_source;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import jenkins.scm.api.SCMHeadObserver;
import org.junit.Test;
import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHRepository;
import org.mockito.Mockito;

public class GithubSCMSourceBranchesTest extends GitSCMSourceBase {

    public GithubSCMSourceBranchesTest() {
        this.source = new GitHubSCMSource("cloudbeers", "yolo", null, false);
    }

    @Test
    public void testMissingSingleBranch() throws IOException {
        // Situation: Hitting the Github API for a branch and getting a 404
        githubApi.stubFor(get(urlEqualTo("/repos/cloudbeers/yolo/branches/non-existent-branch"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBodyFile("../branches/_files/body-yolo-branches-non-existent-branch.json")));
        // stubFor($TYPE(branch/PR/tag), $STATUS, $SCENARIO_NAME)
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);
        Mockito.when(mockSCMHeadObserver.getIncludes())
                .thenReturn(Collections.singleton(new BranchSCMHead("non-existent-branch")));
        GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, mockSCMHeadObserver);
        context.wantBranches(true);
        GitHubSCMSourceRequest request =
                context.newRequest(new GitHubSCMSource("cloudbeers", "yolo", null, false), null);
        Iterator<GHBranch> branches = new GitHubSCMSource.LazyBranches(request, repo).iterator();
        // Expected: In the iterator will be empty
        assertFalse(branches.hasNext());
    }

    @Test
    public void testExistentSingleBranch() throws IOException {
        // Situation: Hitting the Github API for a branch and getting an existing branch
        githubApi.stubFor(get(urlEqualTo("/repos/cloudbeers/yolo/branches/existent-branch"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBodyFile("../branches/_files/body-yolo-branches-existent-branch.json")));
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);
        Mockito.when(mockSCMHeadObserver.getIncludes())
                .thenReturn(Collections.singleton(new BranchSCMHead("existent-branch")));
        GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, mockSCMHeadObserver);
        context.wantBranches(true);
        GitHubSCMSourceRequest request =
                context.newRequest(new GitHubSCMSource("cloudbeers", "yolo", null, false), null);
        Iterator<GHBranch> branches = new GitHubSCMSource.LazyBranches(request, repo).iterator();
        // Expected: In the iterator will be a single branch named existent-branch
        assertTrue(branches.hasNext());
        assertEquals("existent-branch", branches.next().getName());
        assertFalse(branches.hasNext());
    }

    @Test
    public void testThrownErrorSingleBranchException() throws IOException {
        // Situation: When sending a request for a branch which exists, throw a GHNotFoundException
        githubApi.stubFor(get(urlEqualTo("/repos/cloudbeers/yolo/branches/existent-branch"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBodyFile("../branches/_files/body-yolo-branches-existent-branch.json")));
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);
        Error e = new Error("Bad Branch Request", new GHFileNotFoundException());
        Mockito.when(mockSCMHeadObserver.getIncludes())
                .thenReturn(Collections.singleton(new BranchSCMHead("existent-branch")));
        GHRepository repoSpy = Mockito.spy(repo);
        GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, mockSCMHeadObserver);
        context.wantBranches(true);
        GitHubSCMSourceRequest request =
                context.newRequest(new GitHubSCMSource("cloudbeers", "yolo", null, false), null);
        Mockito.doThrow(e).when(repoSpy).getRef("branches/existent-branch");
        // Expected: This will throw an error when requesting a branch
        try {
            Iterator<GHBranch> branches = new GitHubSCMSource.LazyBranches(request, repoSpy).iterator();
            fail("This should throw an exception");
        } catch (Error error) {
            // Error is expected here so this is "success"
            assertEquals("Bad Branch Request", e.getMessage());
        }
    }

    @Test
    public void testExistingMultipleBranchesWithDefaultInPosition1() throws IOException {
        // Situation: Hitting github and getting back multiple branches where master is first in the lst
        // position
        githubApi.stubFor(get(urlEqualTo("/repos/cloudbeers/yolo/branches"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBodyFile(
                                "../branches/_files/body-yolo-branches-existent-multiple-branches-master1.json")));
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);
        GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, mockSCMHeadObserver);
        context.wantBranches(true);
        GitHubSCMSourceRequest request =
                context.newRequest(new GitHubSCMSource("cloudbeers", "yolo", null, false), null);
        Iterator<GHBranch> branches = new GitHubSCMSource.LazyBranches(request, repo).iterator();
        // Expected: In the iterator will be a multiple branches named nexistent-branch1(because it is
        // alphabetically sorted first)
        // and master
        assertTrue(branches.hasNext());
        assertEquals("master", branches.next().getName());
        assertTrue(branches.hasNext());
        assertEquals("nexistent-branch1", branches.next().getName());
        assertFalse(branches.hasNext());
    }

    @Test
    public void testExistingMultipleBranchesWithDefaultInPosition2() throws IOException {
        // Situation: Hitting github and getting back multiple branches where master is first in the 2nd
        // position
        githubApi.stubFor(get(urlEqualTo("/repos/cloudbeers/yolo/branches"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBodyFile(
                                "../branches/_files/body-yolo-branches-existent-multiple-branches-master2.json")));
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);
        GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, mockSCMHeadObserver);
        context.wantTags(true);
        GitHubSCMSourceRequest request =
                context.newRequest(new GitHubSCMSource("cloudbeers", "yolo", null, false), null);
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
        githubApi.stubFor(get(urlEqualTo("/repos/cloudbeers/yolo/branches"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBodyFile(
                                "../branches/_files/body-yolo-branches-existent-multiple-branches-no-master.json")));
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);
        GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, mockSCMHeadObserver);
        context.wantTags(true);
        GitHubSCMSourceRequest request =
                context.newRequest(new GitHubSCMSource("cloudbeers", "yolo", null, false), null);
        Iterator<GHBranch> branches = new GitHubSCMSource.LazyBranches(request, repo).iterator();
        // Expected: In the iterator will be a multiple branches named existent-branch2 and
        // existent-branch1
        assertTrue(branches.hasNext());
        assertEquals("existent-branch1", branches.next().getName());
        assertTrue(branches.hasNext());
        assertEquals("existent-branch2", branches.next().getName());
    }

    @Test
    public void testExistingMultipleBranchesWithThrownError() throws IOException {
        // Situation: Hitting github and getting back multiple branches but throws an I/O error
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);
        githubApi.stubFor(get(urlEqualTo("/repos/cloudbeers/yolo/branches"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBodyFile(
                                "../branches/_files/body-yolo-branches-existent-multiple-branches-no-master.json")));
        GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, mockSCMHeadObserver);
        context.wantTags(true);
        GitHubSCMSourceRequest request =
                context.newRequest(new GitHubSCMSource("cloudbeers", "yolo", null, false), null);
        GHRepository repoSpy = Mockito.spy(repo);
        IOException error = new IOException("Thrown Branch Error");
        Mockito.when(repoSpy.getBranches()).thenThrow(error);
        // Expected: In the iterator will throw an error when calling getBranches
        try {
            Iterator<GHBranch> branches = new GitHubSCMSource.LazyBranches(request, repoSpy).iterator();
            fail("This should throw an exception");
        } catch (Exception e) {
            // We swallow the new GetRef error and then throw the original one for some reason...
            assertEquals("java.io.IOException: Thrown Branch Error", e.getMessage());
        }
    }
}
