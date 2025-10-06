package org.jenkinsci.plugins.github_branch_source;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import hudson.model.Job;
import hudson.model.Run;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.scm.api.SCMRevisionAction;
import jenkins.scm.api.SCMSource;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class GitHubCommitDetailTest {

    @Test
    void displayName_and_link_forScmRevisionImpl() {
        Run<?, ?> run = mock(Run.class);

        SCMRevisionAction action = mock(SCMRevisionAction.class);
        when(run.getAction(SCMRevisionAction.class)).thenReturn(action);

        AbstractGitSCMSource.SCMRevisionImpl revision = mock(AbstractGitSCMSource.SCMRevisionImpl.class);
        when(revision.getHash()).thenReturn("abcdef1234567890");
        when(action.getRevision()).thenReturn(revision);

        GitHubSCMSource src = mock(GitHubSCMSource.class);
        when(src.getRepositoryUrl()).thenReturn("https://github.com/octo/hello-world.git");

        try (MockedStatic<SCMSource.SourceByItem> mocked = mockStatic(SCMSource.SourceByItem.class)) {
            mocked.when(() -> SCMSource.SourceByItem.findSource(any())).thenReturn(src);

            GitHubCommitDetail detail = new GitHubCommitDetail(run);

            // Only first 7 chars of hash
            assertEquals("abcdef1", detail.getDisplayName());
            assertEquals("https://github.com/octo/hello-world/commit/abcdef1234567890", detail.getLink());
        }
    }

    @Test
    void displayName_and_link_forPullRequestRevision() {
        Run run = mock(Run.class);
        Job job = mock(Job.class);
        when(run.getParent()).thenReturn(job);

        SCMRevisionAction action = mock(SCMRevisionAction.class);
        when(run.getAction(SCMRevisionAction.class)).thenReturn(action);

        PullRequestSCMRevision prRevision = mock(PullRequestSCMRevision.class);
        when(prRevision.getPullHash()).thenReturn("1234567deadbeef");
        when(action.getRevision()).thenReturn(prRevision);

        GitHubLink link = mock(GitHubLink.class);
        when(link.getUrl()).thenReturn("https://github.com/octo/hello-world");
        when(job.getAction(GitHubLink.class)).thenReturn(link);

        GitHubCommitDetail detail = new GitHubCommitDetail(run);

        assertEquals("1234567", detail.getDisplayName());
        assertEquals("https://github.com/octo/hello-world/commits/1234567deadbeef", detail.getLink());
    }

    @Test
    void returnsNull_whenNoRevisionAction() {
        Run<?, ?> run = mock(Run.class);
        when(run.getAction(SCMRevisionAction.class)).thenReturn(null);

        GitHubCommitDetail detail = new GitHubCommitDetail(run);

        assertNull(detail.getDisplayName());
        assertNull(detail.getLink());
    }
}
