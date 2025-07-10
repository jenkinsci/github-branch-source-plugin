/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jenkinsci.plugins.github_branch_source;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class GitHubClientMonitoringTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void testIntrospectionCode() throws Exception {
        new GitHubClientMonitoring();
    }
}
