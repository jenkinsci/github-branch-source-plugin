package org.jenkinsci.plugins.github_branch_source;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import hudson.model.Job;
import java.util.function.Supplier;
import jenkins.model.experimentalflags.UserExperimentalFlag;
import org.junit.jupiter.api.Test;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;
import org.mockito.MockedStatic;

class GitHubLinkTest {

    private static final String ICON = "symbol-logo-github plugin-ionicons-api";

    private final GitHubLink link = new GitHubLink("https://github.com/owner/repo");

    @Test
    void hidden_onJobPageWithFlagEnabled() {
        assertNull(iconFileName(true, Boolean.TRUE));
        assertNull(iconClassName(true, Boolean.TRUE));
    }

    @Test
    void shown_onJobPageWithFlagDisabled() {
        assertEquals(ICON, iconFileName(true, Boolean.FALSE));
    }

    @Test
    void shown_whenFlagCannotBeRead() {
        assertEquals(ICON, iconFileName(true, null));
    }

    @Test
    void shown_outsideOfJobWithFlagEnabled() {
        assertEquals(ICON, iconFileName(false, Boolean.TRUE));
    }

    @Test
    void shown_outsideOfRequest() {
        try (MockedStatic<Stapler> stapler = mockStatic(Stapler.class)) {
            stapler.when(Stapler::getCurrentRequest2).thenReturn(null);

            assertEquals(ICON, link.getIconFileName());
        }
    }

    private String iconFileName(boolean underJob, Boolean flagValue) {
        return withPage(underJob, flagValue, link::getIconFileName);
    }

    private String iconClassName(boolean underJob, Boolean flagValue) {
        return withPage(underJob, flagValue, link::getIconClassName);
    }

    private String withPage(boolean underJob, Boolean flagValue, Supplier<String> icon) {
        StaplerRequest2 request = mock(StaplerRequest2.class);
        when(request.findAncestorObject(Job.class)).thenReturn(underJob ? mock(Job.class) : null);

        try (MockedStatic<Stapler> stapler = mockStatic(Stapler.class);
                MockedStatic<UserExperimentalFlag> flag = mockStatic(UserExperimentalFlag.class)) {
            stapler.when(Stapler::getCurrentRequest2).thenReturn(request);
            flag.when(() -> UserExperimentalFlag.getFlagValueForCurrentUser(
                            "jenkins.model.experimentalflags.NewJobPageUserExperimentalFlag"))
                    .thenReturn(flagValue);

            return icon.get();
        }
    }
}
