package org.jenkinsci.plugins.github_branch_source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.AssertionsKt.assertNull;
import static org.mockito.Mockito.*;

import hudson.model.Run;
import jenkins.scm.api.SCMSource;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class GitHubRepositoryDetailTest {

    @Test
    void gitHubRepositoryDetail_showsDetails() {
        GitHubSCMSource instance = mock(GitHubSCMSource.class);
        when(instance.getRepoOwner()).thenReturn("cloudbeers");
        when(instance.getRepository()).thenReturn("stunning-adventure");
        when(instance.getRepositoryUrl()).thenReturn("https://github.com/cloudbeers/stunning-adventure");
        Run<?, ?> run = mock(Run.class);

        try (MockedStatic<SCMSource.SourceByItem> mocked = mockStatic(SCMSource.SourceByItem.class)) {
            mocked.when(() -> SCMSource.SourceByItem.findSource(any())).thenReturn(instance);

            GitHubRepositoryDetail detail = new GitHubRepositoryDetail(run);

            assertEquals("cloudbeers/stunning-adventure", detail.getDisplayName());
            assertEquals("https://github.com/cloudbeers/stunning-adventure", detail.getLink());
        }
    }

    @Test
    void gitHubRepositoryDetail_noConfiguredSource() {
        Run<?, ?> run = mock(Run.class);

        GitHubRepositoryDetail detail = new GitHubRepositoryDetail(run);

        assertNull(detail.getDisplayName());
        assertNull(detail.getLink());
    }
}
