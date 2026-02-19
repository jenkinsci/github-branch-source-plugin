package org.jenkinsci.plugins.github_branch_source;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import hudson.model.Run;
import java.util.List;
import jenkins.model.details.Detail;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMRevisionAction;
import jenkins.scm.api.SCMSource;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class GitHubDetailFactoryTest {

    private final GitHubDetailFactory factory = new GitHubDetailFactory();

    @Test
    void returnsEmptyList_whenNoScmRevisionAction() {
        Run<?, ?> run = mock(Run.class);
        when(run.getAction(SCMRevisionAction.class)).thenReturn(null);

        List<? extends Detail> details = factory.createFor(run);

        assertNotNull(details);
        assertTrue(details.isEmpty());
    }

    @Test
    void createsPrBranchCommitRepo_whenPullRequestRevision() {
        Run<?, ?> run = mock(Run.class);
        SCMRevisionAction action = mock(SCMRevisionAction.class);
        PullRequestSCMRevision prRevision = mock(PullRequestSCMRevision.class);
        when(run.getAction(SCMRevisionAction.class)).thenReturn(action);
        when(action.getRevision()).thenReturn(prRevision);

        try (MockedStatic<SCMSource.SourceByItem> mocked = mockStatic(SCMSource.SourceByItem.class)) {
            mocked.when(() -> SCMSource.SourceByItem.findSource(any())).thenReturn(mock(GitHubSCMSource.class));
            List<? extends Detail> details = factory.createFor(run);

            assertEquals(3, details.size());
            assertInstanceOf(GitHubPullRequestDetail.class, details.get(0));
            assertInstanceOf(GitHubCommitDetail.class, details.get(1));
            assertInstanceOf(GitHubRepositoryDetail.class, details.get(2));
        }
    }

    @Test
    void createsBranchCommitRepo_whenNonPullRequestRevision() {
        Run<?, ?> run = mock(Run.class);
        SCMRevisionAction action = mock(SCMRevisionAction.class);
        SCMRevision nonPrRevision = mock(SCMRevision.class);
        when(run.getAction(SCMRevisionAction.class)).thenReturn(action);
        when(action.getRevision()).thenReturn(nonPrRevision);

        try (MockedStatic<SCMSource.SourceByItem> mocked = mockStatic(SCMSource.SourceByItem.class)) {
            mocked.when(() -> SCMSource.SourceByItem.findSource(any())).thenReturn(mock(GitHubSCMSource.class));

            List<? extends Detail> details = factory.createFor(run);

            assertEquals(3, details.size());
            assertInstanceOf(GitHubBranchDetail.class, details.get(0));
            assertInstanceOf(GitHubCommitDetail.class, details.get(1));
            assertInstanceOf(GitHubRepositoryDetail.class, details.get(2));
        }
    }
}
