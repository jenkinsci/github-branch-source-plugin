/*
 * The MIT License
 *
 * Copyright 2018 CloudBees, Inc.
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
 */
package org.jenkinsci.plugins.github_branch_source;

import static org.junit.Assert.*;

import java.util.Collections;
import java.util.List;
import jenkins.branch.BranchSource;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.mixin.ChangeRequestSCMHead2;
import jenkins.scm.api.trait.SCMHeadAuthority;
import jenkins.scm.api.trait.SCMSourceTrait;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.JenkinsRule;

@For(ForkPullRequestDiscoveryTrait.class)
public class ForkPullRequestDiscoveryTrait2Test {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Ignore(
            "These tests fail because users get automatically migrated to URL-based configuration if they re-save the GitHubSCMSource")
    @Test
    public void configRoundtrip() throws Exception {
        WorkflowMultiBranchProject p = r.createProject(WorkflowMultiBranchProject.class);

        assertRoundTrip(p, new ForkPullRequestDiscoveryTrait.TrustNobody(), false);
        assertRoundTrip(p, new ForkPullRequestDiscoveryTrait.TrustEveryone(), false);
        assertRoundTrip(p, new ForkPullRequestDiscoveryTrait.TrustContributors(), false);
        assertRoundTrip(p, new ForkPullRequestDiscoveryTrait.TrustPermission(), false);
    }

    @Test
    public void configRoundtripWithRawUrl() throws Exception {
        WorkflowMultiBranchProject p = r.createProject(WorkflowMultiBranchProject.class);

        assertRoundTrip(p, new ForkPullRequestDiscoveryTrait.TrustNobody(), true);
        assertRoundTrip(p, new ForkPullRequestDiscoveryTrait.TrustEveryone(), true);
        assertRoundTrip(p, new ForkPullRequestDiscoveryTrait.TrustContributors(), true);
        assertRoundTrip(p, new ForkPullRequestDiscoveryTrait.TrustPermission(), true);
    }

    private void assertRoundTrip(
            WorkflowMultiBranchProject p,
            SCMHeadAuthority<? super GitHubSCMSourceRequest, ? extends ChangeRequestSCMHead2, ? extends SCMRevision>
                    trust,
            boolean configuredByUrl)
            throws Exception {

        GitHubSCMSource s;
        if (configuredByUrl) s = new GitHubSCMSource("", "", "https://github.com/nobody/nowhere", true);
        else s = new GitHubSCMSource("nobody", "nowhere", null, false);

        p.setSourcesList(Collections.singletonList(new BranchSource(s)));
        s.setTraits(Collections.singletonList(new ForkPullRequestDiscoveryTrait(0, trust)));
        r.configRoundtrip(p);
        List<SCMSourceTrait> traits =
                ((GitHubSCMSource) p.getSourcesList().get(0).getSource()).getTraits();
        assertEquals(1, traits.size());
        assertEquals(
                trust.getClass(),
                ((ForkPullRequestDiscoveryTrait) traits.get(0)).getTrust().getClass());
    }
}
