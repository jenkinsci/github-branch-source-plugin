/*
 * The MIT License
 *
 * Copyright (c) 2017 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 */

package org.jenkinsci.plugins.github_branch_source;

import com.cloudbees.jenkins.GitHubWebHook;
import jenkins.scm.api.SCMNavigatorOwner;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mockito;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

public class WebhookTest {
    /**
     * All tests in this class only use Jenkins for the extensions
     */
    @ClassRule
    public static JenkinsRule r = new JenkinsRule();

    private static String EXCEPTION_MESSAGE = "custom exception message";

    @Test
    public void registerHook() throws Exception {
        checkNavigator();
        checkSource();
    }

    private void checkNavigator() {
        GitHubSCMNavigator navigator = mock(GitHubSCMNavigator.class, Mockito.CALLS_REAL_METHODS);
        SCMNavigatorOwner owner = mock(SCMNavigatorOwner.class);

        GitHubWebHook hook = mock(GitHubWebHook.class);
        doThrow(new RuntimeException(EXCEPTION_MESSAGE)).when(hook).registerHookFor(owner);
        when(navigator.getHook()).thenReturn(hook);

        try {
            navigator.afterSave(owner);
            fail("Webhook should be registered when GitHubSCMNavigator is saved");
        } catch (final RuntimeException ex) {
            assertTrue(ex.getMessage().contains(EXCEPTION_MESSAGE));
        }
    }

    private void checkSource() {
        GitHubSCMSource source = mock(GitHubSCMSource.class, Mockito.CALLS_REAL_METHODS);

        SCMNavigatorOwner owner = mock(SCMNavigatorOwner.class);
        GitHubWebHook hook = mock(GitHubWebHook.class);
        doThrow(new RuntimeException(EXCEPTION_MESSAGE)).when(hook).registerHookFor(owner);

        when(source.getHook()).thenReturn(hook);
        when(source.getOwnerNotSynchronized()).thenReturn(owner);

        try {
            source.afterSave();
            fail("Webhook should be registered when GitHubSCMSource is saved");
        } catch (final RuntimeException ex) {
            assertTrue(ex.getMessage().contains(EXCEPTION_MESSAGE));
        }
    }

}
