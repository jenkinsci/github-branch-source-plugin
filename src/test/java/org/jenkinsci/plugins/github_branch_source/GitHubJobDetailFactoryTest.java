package org.jenkinsci.plugins.github_branch_source;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import hudson.model.Job;
import java.util.List;
import jenkins.model.details.Detail;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.metadata.ObjectMetadataAction;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class GitHubJobDetailFactoryTest {

    private final GitHubJobDetailFactory factory = new GitHubJobDetailFactory();

    @Test
    void returnsEmptyList_whenNotGitHubSource() {
        Job<?, ?> job = mock(Job.class);

        try (MockedStatic<SCMSource.SourceByItem> source = mockStatic(SCMSource.SourceByItem.class)) {
            source.when(() -> SCMSource.SourceByItem.findSource(any())).thenReturn(mock(SCMSource.class));

            assertTrue(factory.createFor(job).isEmpty());
        }
    }

    @Test
    void createsRepoAndBranch_whenBranchHead() {
        List<? extends Detail> details = createForHead(mock(SCMHead.class));

        assertEquals(2, details.size());
        assertInstanceOf(GitHubRepositoryDetail.class, details.get(0));
        assertInstanceOf(GitHubBranchDetail.class, details.get(1));
    }

    @Test
    void createsRepoAndPullRequest_whenPullRequestHead() {
        List<? extends Detail> details = createForHead(mock(PullRequestSCMHead.class));

        assertEquals(2, details.size());
        assertInstanceOf(GitHubRepositoryDetail.class, details.get(0));
        assertInstanceOf(GitHubPullRequestDetail.class, details.get(1));
    }

    @Test
    void createsOnlyRepo_whenNoMetadata() {
        Job<?, ?> job = mock(Job.class);

        try (MockedStatic<SCMSource.SourceByItem> source = mockStatic(SCMSource.SourceByItem.class)) {
            source.when(() -> SCMSource.SourceByItem.findSource(any())).thenReturn(mock(GitHubSCMSource.class));

            List<? extends Detail> details = factory.createFor(job);

            assertEquals(1, details.size());
            assertInstanceOf(GitHubRepositoryDetail.class, details.get(0));
        }
    }

    @Test
    void detailsReadFromTheJob() {
        Job<?, ?> job = mock(Job.class);
        when(job.getAction(ObjectMetadataAction.class))
                .thenReturn(new ObjectMetadataAction("main", null, "https://github.com/owner/repo/tree/main"));
        GitHubSCMSource gitHubSource = mock(GitHubSCMSource.class);
        when(gitHubSource.getRepoOwner()).thenReturn("owner");
        when(gitHubSource.getRepository()).thenReturn("repo");

        GitHubBranchDetail branch = new GitHubBranchDetail(job);
        assertEquals("main", branch.getDisplayName());
        assertEquals("https://github.com/owner/repo/tree/main", branch.getLink());

        try (MockedStatic<SCMSource.SourceByItem> source = mockStatic(SCMSource.SourceByItem.class)) {
            source.when(() -> SCMSource.SourceByItem.findSource(job)).thenReturn(gitHubSource);

            assertEquals("owner/repo", new GitHubRepositoryDetail(job).getDisplayName());
        }
    }

    private List<? extends Detail> createForHead(SCMHead head) {
        Job<?, ?> job = mock(Job.class);
        when(job.getAction(ObjectMetadataAction.class)).thenReturn(mock(ObjectMetadataAction.class));

        try (MockedStatic<SCMSource.SourceByItem> source = mockStatic(SCMSource.SourceByItem.class);
                MockedStatic<SCMHead.HeadByItem> headByItem = mockStatic(SCMHead.HeadByItem.class)) {
            source.when(() -> SCMSource.SourceByItem.findSource(any())).thenReturn(mock(GitHubSCMSource.class));
            headByItem.when(() -> SCMHead.HeadByItem.findHead(any())).thenReturn(head);

            return factory.createFor(job);
        }
    }
}
