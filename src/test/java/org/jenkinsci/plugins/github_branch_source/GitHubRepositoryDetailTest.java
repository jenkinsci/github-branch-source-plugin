package org.jenkinsci.plugins.github_branch_source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.AssertionsKt.assertNull;
import static org.mockito.Mockito.*;

import hudson.model.Run;
import java.lang.reflect.Method;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.mockito.MockedStatic;

class GitHubRepositoryDetailTest {

    private String methodName;

    @BeforeEach
    void setup(TestInfo testInfo) {
        methodName = testInfo.getTestMethod().map(Method::getName).orElse("unknown");
    }

    GitHubSCMSource load() {
        return (GitHubSCMSource) Jenkins.XSTREAM2.fromXML(
                getClass().getResource(getClass().getSimpleName() + "/" + methodName + ".xml"));
    }

    @Test
    void gitHubRepositoryDetail_showsDetails() {
        GitHubSCMSource instance = load();
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
