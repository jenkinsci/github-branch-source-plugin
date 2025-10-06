package org.jenkinsci.plugins.github_branch_source;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import hudson.model.Job;
import hudson.model.Run;
import jenkins.scm.api.metadata.ObjectMetadataAction;
import org.junit.jupiter.api.Test;

class GitHubBranchDetailTest {

    @Test
    void displaysAsExpected() {
        Run run = mock(Run.class);
        Job job = mock(Job.class);
        when(run.getParent()).thenReturn(job);
        ObjectMetadataAction metadata = mock(ObjectMetadataAction.class);
        when(metadata.getObjectDisplayName()).thenReturn("main");
        when(metadata.getObjectUrl()).thenReturn("https://github.com/octo/hello-world/tree/main");
        when(job.getAction(ObjectMetadataAction.class)).thenReturn(metadata);

        GitHubBranchDetail detail = new GitHubBranchDetail(run);

        assertEquals("main", detail.getDisplayName());
        assertEquals("https://github.com/octo/hello-world/tree/main", detail.getLink());
    }
}
