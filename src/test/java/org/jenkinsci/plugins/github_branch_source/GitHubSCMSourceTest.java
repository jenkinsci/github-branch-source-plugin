/*
 * The MIT License
 *
 * Copyright (c) 2016 CloudBees, Inc.
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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

import com.cloudbees.jenkins.GitHubRepositoryName;
import com.cloudbees.jenkins.GitHubRepositoryNameContributor;
import com.github.tomakehurst.wiremock.common.SingleRootFileSource;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.ExtensionList;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.TaskListener;
import hudson.model.User;
import hudson.scm.SCM;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.AuthorizationStrategy;
import hudson.security.SecurityRealm;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.LogTaskListener;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.branch.BranchSource;
import jenkins.model.Jenkins;
import jenkins.plugins.git.GitSCMSource;
import jenkins.scm.api.*;
import jenkins.scm.api.metadata.ObjectMetadataAction;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import org.hamcrest.Matchers;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.mockito.Mockito;

@RunWith(Parameterized.class)
public class GitHubSCMSourceTest extends GitSCMSourceBase {

    public GitHubSCMSourceTest(GitHubSCMSource source) {
        this.source = source;
    }

    @Parameterized.Parameters(name = "{index}: revision={0}")
    public static GitHubSCMSource[] revisions() {
        return new GitHubSCMSource[] {
            new GitHubSCMSource("cloudbeers", "yolo", null, false),
            new GitHubSCMSource("", "", "https://github.com/cloudbeers/yolo", true)
        };
    }

    @Before
    public void prepareMockGitHubStubs() throws Exception {
        new File("src/test/resources/api/mappings").mkdirs();
        new File("src/test/resources/api/__files").mkdirs();
        githubApi.enableRecordMappings(
                new SingleRootFileSource("src/test/resources/api/mappings"),
                new SingleRootFileSource("src/test/resources/api/__files"));

        githubApi.stubFor(get(urlEqualTo("/repos/cloudbeers/yolo/pulls/2"))
                .inScenario("Pull Request Merge Hash")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBodyFile("body-yolo-pulls-2-mergeable-null.json"))
                .willSetStateTo("Pull Request Merge Hash - retry 1"));

        githubApi.stubFor(get(urlEqualTo("/repos/cloudbeers/yolo/pulls/2"))
                .inScenario("Pull Request Merge Hash")
                .whenScenarioStateIs("Pull Request Merge Hash - retry 1")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBodyFile("body-yolo-pulls-2-mergeable-null.json"))
                .willSetStateTo("Pull Request Merge Hash - retry 2"));

        githubApi.stubFor(get(urlEqualTo("/repos/cloudbeers/yolo/pulls/2"))
                .inScenario("Pull Request Merge Hash")
                .whenScenarioStateIs("Pull Request Merge Hash - retry 2")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBodyFile("body-yolo-pulls-2-mergeable-true.json"))
                .willSetStateTo("Pull Request Merge Hash - retry 2"));
    }

    SCMHeadEvent<PushGHEventSubscriber> pushEvent =
            new SCMHeadEvent<PushGHEventSubscriber>(SCMEvent.Type.CREATED, System.currentTimeMillis(), null, null) {
                @Override
                public boolean isMatch(@NonNull SCMNavigator scmNavigator) {
                    return false;
                }

                @NonNull
                @Override
                public String getSourceName() {
                    return null;
                }

                @NonNull
                @Override
                public Map<SCMHead, SCMRevision> heads(@NonNull SCMSource scmSource) {
                    return null;
                }

                @Override
                public boolean isMatch(@NonNull SCM scm) {
                    return false;
                }
            };

    SCMHeadEvent<PullRequestGHEventSubscriber> pullRequestEvent =
            new SCMHeadEvent<PullRequestGHEventSubscriber>(
                    SCMEvent.Type.CREATED, System.currentTimeMillis(), null, null) {
                @Override
                public boolean isMatch(@NonNull SCMNavigator scmNavigator) {
                    return false;
                }

                @NonNull
                @Override
                public String getSourceName() {
                    return null;
                }

                @NonNull
                @Override
                public Map<SCMHead, SCMRevision> heads(@NonNull SCMSource scmSource) {
                    return null;
                }

                @Override
                public boolean isMatch(@NonNull SCM scm) {
                    return false;
                }
            };

    @Test
    @Issue("JENKINS-48035")
    public void testGitHubRepositoryNameContributor() throws IOException {
        WorkflowMultiBranchProject job = r.createProject(WorkflowMultiBranchProject.class);
        job.setSourcesList(Arrays.asList(new BranchSource(source)));
        Collection<GitHubRepositoryName> names = GitHubRepositoryNameContributor.parseAssociatedNames(job);
        assertThat(
                names,
                contains(allOf(
                        hasProperty("userName", equalTo("cloudbeers")),
                        hasProperty("repositoryName", equalTo("yolo")))));
        // And specifically...
        names = new ArrayList<>();
        ExtensionList.lookup(GitHubRepositoryNameContributor.class)
                .get(GitHubSCMSourceRepositoryNameContributor.class)
                .parseAssociatedNames(job, names);
        assertThat(
                names,
                contains(allOf(
                        hasProperty("userName", equalTo("cloudbeers")),
                        hasProperty("repositoryName", equalTo("yolo")))));
    }

    @Test
    @Issue("JENKINS-48035")
    public void testGitHubRepositoryNameContributor_When_not_GitHub() throws IOException {
        WorkflowMultiBranchProject job = r.createProject(WorkflowMultiBranchProject.class);
        job.setSourcesList(Arrays.asList(new BranchSource(new GitSCMSource("file://tmp/something"))));
        Collection<GitHubRepositoryName> names = GitHubRepositoryNameContributor.parseAssociatedNames(job);
        assertThat(names, Matchers.empty());
        // And specifically...
        names = new ArrayList<>();
        ExtensionList.lookup(GitHubRepositoryNameContributor.class)
                .get(GitHubSCMSourceRepositoryNameContributor.class)
                .parseAssociatedNames(job, names);
        assertThat(names, Matchers.empty());
    }

    @Test
    @Issue("JENKINS-48035")
    public void testGitHubRepositoryNameContributor_When_not_MultiBranch() throws IOException {
        FreeStyleProject job = r.createProject(FreeStyleProject.class);
        Collection<GitHubRepositoryName> names = GitHubRepositoryNameContributor.parseAssociatedNames((Item) job);
        assertThat(names, Matchers.empty());
        // And specifically...
        names = new ArrayList<>();
        ExtensionList.lookup(GitHubRepositoryNameContributor.class)
                .get(GitHubSCMSourceRepositoryNameContributor.class)
                .parseAssociatedNames((Item) job, names);
        assertThat(names, Matchers.empty());
    }

    @Test
    public void fetchSmokes() throws Exception {
        SCMHeadObserver.Collector collector = SCMHeadObserver.collect();
        source.fetch(
                new SCMSourceCriteria() {
                    @Override
                    public boolean isHead(@NonNull Probe probe, @NonNull TaskListener listener) throws IOException {
                        return probe.stat("README.md").getType() == SCMFile.Type.REGULAR_FILE;
                    }
                },
                collector,
                null,
                null);
        Map<String, SCMHead> byName = new HashMap<>();
        Map<String, SCMRevision> revByName = new HashMap<>();
        for (Map.Entry<SCMHead, SCMRevision> h : collector.result().entrySet()) {
            byName.put(h.getKey().getName(), h.getKey());
            revByName.put(h.getKey().getName(), h.getValue());
        }
        assertThat(byName.keySet(), containsInAnyOrder("PR-2", "PR-3", "PR-4", "master", "stephenc-patch-1"));

        assertThat(byName.get("PR-2"), instanceOf(PullRequestSCMHead.class));
        assertThat(((PullRequestSCMHead) byName.get("PR-2")).isMerge(), is(true));
        assertThat(
                revByName.get("PR-2"),
                is(new PullRequestSCMRevision(
                        (PullRequestSCMHead) (byName.get("PR-2")),
                        "8f1314fc3c8284d8c6d5886d473db98f2126071c",
                        "c0e024f89969b976da165eecaa71e09dc60c3da1",
                        "38814ca33833ff5583624c29f305be9133f27a40")));
        ((PullRequestSCMRevision) revByName.get("PR-2")).validateMergeHash();

        assertThat(byName.get("PR-3"), instanceOf(PullRequestSCMHead.class));
        assertThat(((SCMHeadOrigin.Fork) byName.get("PR-3").getOrigin()).getName(), is("stephenc"));
        assertThat(((PullRequestSCMHead) byName.get("PR-3")).isMerge(), is(true));
        assertThat(
                revByName.get("PR-3"),
                is(new PullRequestSCMRevision(
                        (PullRequestSCMHead) (byName.get("PR-3")),
                        "8f1314fc3c8284d8c6d5886d473db98f2126071c",
                        "c0e024f89969b976da165eecaa71e09dc60c3da1",
                        PullRequestSCMRevision.NOT_MERGEABLE_HASH)));

        // validation should fail for this PR.
        Exception abort = null;
        try {
            ((PullRequestSCMRevision) revByName.get("PR-3")).validateMergeHash();
        } catch (Exception e) {
            abort = e;
        }
        assertThat(abort, instanceOf(AbortException.class));
        assertThat(abort.getMessage(), containsString("Not mergeable"));

        assertThat(byName.get("PR-4"), instanceOf(PullRequestSCMHead.class));
        assertThat(((SCMHeadOrigin.Fork) byName.get("PR-4").getOrigin()).getName(), is("stephenc/jenkins-58450"));

        assertThat(byName.get("master"), instanceOf(BranchSCMHead.class));
        assertThat(revByName.get("master"), hasProperty("hash", is("8f1314fc3c8284d8c6d5886d473db98f2126071c")));
        assertThat(byName.get("stephenc-patch-1"), instanceOf(BranchSCMHead.class));
        assertThat(
                revByName.get("stephenc-patch-1"), hasProperty("hash", is("095e69602bb95a278505e937e41d505ac3cdd263")));
    }

    @Test
    public void fetchSmokes_badMergeCommit() throws Exception {
        // make it so the merge commit is not found returns 404
        // Causes PR 2 to fall back to null merge_commit_sha

        githubApi.stubFor(get(urlEqualTo("/repos/cloudbeers/yolo/commits/38814ca33833ff5583624c29f305be9133f27a40"))
                .inScenario("PR 2 Merge 404")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBodyFile("body-heads-master-notfound.json"))
                .willSetStateTo(Scenario.STARTED));

        SCMHeadObserver.Collector collector = SCMHeadObserver.collect();
        source.fetch(
                new SCMSourceCriteria() {
                    @Override
                    public boolean isHead(@NonNull Probe probe, @NonNull TaskListener listener) throws IOException {
                        return probe.stat("README.md").getType() == SCMFile.Type.REGULAR_FILE;
                    }
                },
                collector,
                null,
                null);
        Map<String, SCMHead> byName = new HashMap<>();
        Map<String, SCMRevision> revByName = new HashMap<>();
        for (Map.Entry<SCMHead, SCMRevision> h : collector.result().entrySet()) {
            byName.put(h.getKey().getName(), h.getKey());
            revByName.put(h.getKey().getName(), h.getValue());
        }

        assertThat(byName.keySet(), containsInAnyOrder("PR-2", "PR-3", "PR-4", "master", "stephenc-patch-1"));

        assertThat(byName.get("PR-2"), instanceOf(PullRequestSCMHead.class));
        assertThat(((PullRequestSCMHead) byName.get("PR-2")).isMerge(), is(true));
        assertThat(
                revByName.get("PR-2"),
                is(new PullRequestSCMRevision(
                        (PullRequestSCMHead) (byName.get("PR-2")),
                        "8f1314fc3c8284d8c6d5886d473db98f2126071c",
                        "c0e024f89969b976da165eecaa71e09dc60c3da1",
                        null)));
        ((PullRequestSCMRevision) revByName.get("PR-2")).validateMergeHash();

        assertThat(byName.get("PR-3"), instanceOf(PullRequestSCMHead.class));
        assertThat(((PullRequestSCMHead) byName.get("PR-3")).isMerge(), is(true));
        assertThat(((SCMHeadOrigin.Fork) byName.get("PR-3").getOrigin()).getName(), is("stephenc"));
        assertThat(
                revByName.get("PR-3"),
                is(new PullRequestSCMRevision(
                        (PullRequestSCMHead) (byName.get("PR-3")),
                        "8f1314fc3c8284d8c6d5886d473db98f2126071c",
                        "c0e024f89969b976da165eecaa71e09dc60c3da1",
                        PullRequestSCMRevision.NOT_MERGEABLE_HASH)));

        // validation should fail for this PR.
        Exception abort = null;
        try {
            ((PullRequestSCMRevision) revByName.get("PR-3")).validateMergeHash();
        } catch (Exception e) {
            abort = e;
        }
        assertThat(abort, instanceOf(AbortException.class));
        assertThat(abort.getMessage(), containsString("Not mergeable"));

        assertThat(byName.get("PR-4"), instanceOf(PullRequestSCMHead.class));
        assertThat(((SCMHeadOrigin.Fork) byName.get("PR-4").getOrigin()).getName(), is("stephenc/jenkins-58450"));

        assertThat(byName.get("master"), instanceOf(BranchSCMHead.class));
        assertThat(revByName.get("master"), hasProperty("hash", is("8f1314fc3c8284d8c6d5886d473db98f2126071c")));
        assertThat(byName.get("stephenc-patch-1"), instanceOf(BranchSCMHead.class));
        assertThat(
                revByName.get("stephenc-patch-1"), hasProperty("hash", is("095e69602bb95a278505e937e41d505ac3cdd263")));
    }

    @Test
    public void fetchSmokes_badUser() throws Exception {
        // make it so PR-2 returns a file not found for user
        githubApi.stubFor(get(urlMatching("(/api/v3)?/repos/cloudbeers/yolo/pulls/2"))
                .inScenario("Pull Request Merge Hash")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBodyFile("body-yolo-pulls-2-bad-user.json")));
        githubApi.stubFor(get(urlMatching("(/api/v3)?/repos/cloudbeers/yolo/pulls\\?state=open"))
                .inScenario("Pull Request Merge Hash")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBodyFile("body-yolo-pulls-bad-user.json")));

        SCMHeadObserver.Collector collector = SCMHeadObserver.collect();
        source.fetch(
                new SCMSourceCriteria() {
                    @Override
                    public boolean isHead(@NonNull Probe probe, @NonNull TaskListener listener) throws IOException {
                        return probe.stat("README.md").getType() == SCMFile.Type.REGULAR_FILE;
                    }
                },
                collector,
                null,
                null);
        Map<String, SCMHead> byName = new HashMap<>();
        Map<String, SCMRevision> revByName = new HashMap<>();
        for (Map.Entry<SCMHead, SCMRevision> h : collector.result().entrySet()) {
            byName.put(h.getKey().getName(), h.getKey());
            revByName.put(h.getKey().getName(), h.getValue());
        }
        assertThat(byName.keySet(), containsInAnyOrder("PR-3", "PR-4", "master", "stephenc-patch-1"));

        // PR-2 fails to find user and throws file not found for user.
        // Caught and handled by removing PR-2 but scan continues.

        assertThat(byName.get("PR-3"), instanceOf(PullRequestSCMHead.class));
        assertThat(((SCMHeadOrigin.Fork) byName.get("PR-3").getOrigin()).getName(), is("stephenc"));
        assertThat(((PullRequestSCMHead) byName.get("PR-3")).isMerge(), is(true));
        assertThat(
                revByName.get("PR-3"),
                is(new PullRequestSCMRevision(
                        (PullRequestSCMHead) (byName.get("PR-3")),
                        "8f1314fc3c8284d8c6d5886d473db98f2126071c",
                        "c0e024f89969b976da165eecaa71e09dc60c3da1",
                        PullRequestSCMRevision.NOT_MERGEABLE_HASH)));

        // validation should fail for this PR.
        Exception abort = null;
        try {
            ((PullRequestSCMRevision) revByName.get("PR-3")).validateMergeHash();
        } catch (Exception e) {
            abort = e;
        }
        assertThat(abort, instanceOf(AbortException.class));
        assertThat(abort.getMessage(), containsString("Not mergeable"));

        assertThat(byName.get("PR-4"), instanceOf(PullRequestSCMHead.class));
        assertThat(((SCMHeadOrigin.Fork) byName.get("PR-4").getOrigin()).getName(), is("stephenc/jenkins-58450"));

        assertThat(byName.get("master"), instanceOf(BranchSCMHead.class));
        assertThat(revByName.get("master"), hasProperty("hash", is("8f1314fc3c8284d8c6d5886d473db98f2126071c")));
        assertThat(byName.get("stephenc-patch-1"), instanceOf(BranchSCMHead.class));
        assertThat(
                revByName.get("stephenc-patch-1"), hasProperty("hash", is("095e69602bb95a278505e937e41d505ac3cdd263")));
    }

    @Test
    public void fetchSmokes_badTarget() throws Exception {
        // make it so the merge commit is not found returns 404
        // Causes PR 2 to fall back to null merge_commit_sha
        // Then make it so refs/heads/master returns 404 for first call
        // Causes PR 2 to fail because it cannot determine base commit.
        githubApi.stubFor(
                get(urlMatching("(/api/v3)?/repos/cloudbeers/yolo/commits/38814ca33833ff5583624c29f305be9133f27a40"))
                        .inScenario("PR 2 Merge 404")
                        .whenScenarioStateIs(Scenario.STARTED)
                        .willReturn(aResponse()
                                .withStatus(404)
                                .withHeader("Content-Type", "application/json; charset=utf-8")
                                .withBodyFile("body-heads-master-notfound.json"))
                        .willSetStateTo(Scenario.STARTED));

        githubApi.stubFor(get(urlMatching("(/api/v3)?/repos/cloudbeers/yolo/git/refs/heads/master"))
                .inScenario("PR 2 Master 404")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBodyFile("body-heads-master-notfound.json"))
                .willSetStateTo("Master 200"));

        githubApi.stubFor(get(urlMatching("(/api/v3)?/repos/cloudbeers/yolo/git/refs/heads/master"))
                .inScenario("PR 2 Master 404")
                .whenScenarioStateIs("Master 200")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBodyFile("body-heads-master.json"))
                .willSetStateTo("Master 200"));

        SCMHeadObserver.Collector collector = SCMHeadObserver.collect();
        source.fetch(
                new SCMSourceCriteria() {
                    @Override
                    public boolean isHead(@NonNull Probe probe, @NonNull TaskListener listener) throws IOException {
                        return probe.stat("README.md").getType() == SCMFile.Type.REGULAR_FILE;
                    }
                },
                collector,
                null,
                null);
        Map<String, SCMHead> byName = new HashMap<>();
        Map<String, SCMRevision> revByName = new HashMap<>();
        for (Map.Entry<SCMHead, SCMRevision> h : collector.result().entrySet()) {
            byName.put(h.getKey().getName(), h.getKey());
            revByName.put(h.getKey().getName(), h.getValue());
        }
        assertThat(byName.keySet(), containsInAnyOrder("PR-3", "PR-4", "master", "stephenc-patch-1"));

        // PR-2 fails to find master and throws file not found for master.
        // Caught and handled by removing PR-2 but scan continues.

        assertThat(byName.get("PR-3"), instanceOf(PullRequestSCMHead.class));
        assertThat(((SCMHeadOrigin.Fork) byName.get("PR-3").getOrigin()).getName(), is("stephenc"));
        assertThat(((PullRequestSCMHead) byName.get("PR-3")).isMerge(), is(true));
        assertThat(
                revByName.get("PR-3"),
                is(new PullRequestSCMRevision(
                        (PullRequestSCMHead) (byName.get("PR-3")),
                        "8f1314fc3c8284d8c6d5886d473db98f2126071c",
                        "c0e024f89969b976da165eecaa71e09dc60c3da1",
                        PullRequestSCMRevision.NOT_MERGEABLE_HASH)));

        // validation should fail for this PR.
        Exception abort = null;
        try {
            ((PullRequestSCMRevision) revByName.get("PR-3")).validateMergeHash();
        } catch (Exception e) {
            abort = e;
        }
        assertThat(abort, instanceOf(AbortException.class));
        assertThat(abort.getMessage(), containsString("Not mergeable"));

        assertThat(byName.get("PR-4"), instanceOf(PullRequestSCMHead.class));
        assertThat(((SCMHeadOrigin.Fork) byName.get("PR-4").getOrigin()).getName(), is("stephenc/jenkins-58450"));

        assertThat(byName.get("master"), instanceOf(BranchSCMHead.class));
        assertThat(revByName.get("master"), hasProperty("hash", is("8f1314fc3c8284d8c6d5886d473db98f2126071c")));
        assertThat(byName.get("stephenc-patch-1"), instanceOf(BranchSCMHead.class));
        assertThat(
                revByName.get("stephenc-patch-1"), hasProperty("hash", is("095e69602bb95a278505e937e41d505ac3cdd263")));
    }

    @Test
    public void fetchSmokesUnknownMergeable() throws Exception {
        // make it so PR-2 always returns mergeable = null
        githubApi.stubFor(get(urlEqualTo("/repos/cloudbeers/yolo/pulls/2"))
                .inScenario("Pull Request Merge Hash")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBodyFile("body-yolo-pulls-2-mergeable-null.json")));

        SCMHeadObserver.Collector collector = SCMHeadObserver.collect();
        source.fetch(
                new SCMSourceCriteria() {
                    @Override
                    public boolean isHead(@NonNull Probe probe, @NonNull TaskListener listener) throws IOException {
                        return probe.stat("README.md").getType() == SCMFile.Type.REGULAR_FILE;
                    }
                },
                collector,
                null,
                null);
        Map<String, SCMHead> byName = new HashMap<>();
        Map<String, SCMRevision> revByName = new HashMap<>();
        for (Map.Entry<SCMHead, SCMRevision> h : collector.result().entrySet()) {
            byName.put(h.getKey().getName(), h.getKey());
            revByName.put(h.getKey().getName(), h.getValue());
        }

        assertThat(byName.keySet(), containsInAnyOrder("PR-2", "PR-3", "PR-4", "master", "stephenc-patch-1"));

        assertThat(byName.get("PR-2"), instanceOf(PullRequestSCMHead.class));
        assertThat(((PullRequestSCMHead) byName.get("PR-2")).isMerge(), is(true));
        assertThat(
                revByName.get("PR-2"),
                is(new PullRequestSCMRevision(
                        (PullRequestSCMHead) (byName.get("PR-2")),
                        "8f1314fc3c8284d8c6d5886d473db98f2126071c",
                        "c0e024f89969b976da165eecaa71e09dc60c3da1",
                        null)));
        ((PullRequestSCMRevision) revByName.get("PR-2")).validateMergeHash();

        assertThat(byName.get("PR-3"), instanceOf(PullRequestSCMHead.class));
        assertThat(((SCMHeadOrigin.Fork) byName.get("PR-3").getOrigin()).getName(), is("stephenc"));
        assertThat(((PullRequestSCMHead) byName.get("PR-3")).isMerge(), is(true));
        assertThat(
                revByName.get("PR-3"),
                is(new PullRequestSCMRevision(
                        (PullRequestSCMHead) (byName.get("PR-3")),
                        "8f1314fc3c8284d8c6d5886d473db98f2126071c",
                        "c0e024f89969b976da165eecaa71e09dc60c3da1",
                        PullRequestSCMRevision.NOT_MERGEABLE_HASH)));

        // validation should fail for this PR.
        Exception abort = null;
        try {
            ((PullRequestSCMRevision) revByName.get("PR-3")).validateMergeHash();
        } catch (Exception e) {
            abort = e;
        }
        assertThat(abort, instanceOf(AbortException.class));
        assertThat(abort.getMessage(), containsString("Not mergeable"));

        assertThat(byName.get("PR-4"), instanceOf(PullRequestSCMHead.class));
        assertThat(((SCMHeadOrigin.Fork) byName.get("PR-4").getOrigin()).getName(), is("stephenc/jenkins-58450"));

        assertThat(byName.get("master"), instanceOf(BranchSCMHead.class));
        assertThat(revByName.get("master"), hasProperty("hash", is("8f1314fc3c8284d8c6d5886d473db98f2126071c")));
        assertThat(byName.get("stephenc-patch-1"), instanceOf(BranchSCMHead.class));
        assertThat(
                revByName.get("stephenc-patch-1"), hasProperty("hash", is("095e69602bb95a278505e937e41d505ac3cdd263")));
    }

    @Test
    public void fetchSmokesUnknownFork() throws Exception {
        // make it so PR-2 always returns mergeable = null
        githubApi.stubFor(get(urlEqualTo("/repos/cloudbeers/yolo/pulls/4"))
                .inScenario("Pull Request from Deleted Fork")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBodyFile("body-yolo-pulls-4-deleted-fork.json")));
        githubApi.stubFor(get(urlMatching("(/api/v3)?/repos/cloudbeers/yolo/pulls\\?state=open"))
                .inScenario("Pull Request from Deleted Fork")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBodyFile("body-yolo-pulls-deleted-fork.json")));

        SCMHeadObserver.Collector collector = SCMHeadObserver.collect();
        source.fetch(
                new SCMSourceCriteria() {
                    @Override
                    public boolean isHead(@NonNull Probe probe, @NonNull TaskListener listener) throws IOException {
                        return probe.stat("README.md").getType() == SCMFile.Type.REGULAR_FILE;
                    }
                },
                collector,
                null,
                null);
        Map<String, SCMHead> byName = new HashMap<>();
        Map<String, SCMRevision> revByName = new HashMap<>();
        for (Map.Entry<SCMHead, SCMRevision> h : collector.result().entrySet()) {
            byName.put(h.getKey().getName(), h.getKey());
            revByName.put(h.getKey().getName(), h.getValue());
        }

        assertThat(byName.keySet(), hasItem("PR-4"));

        assertThat(byName.get("PR-4"), instanceOf(PullRequestSCMHead.class));
        assertThat(((SCMHeadOrigin.Fork) byName.get("PR-4").getOrigin()).getName(), nullValue());
    }

    @Test
    public void fetchAltConfig() throws Exception {
        source.setBuildForkPRMerge(false);
        source.setBuildForkPRHead(true);
        source.setBuildOriginPRMerge(false);
        source.setBuildOriginPRHead(false);
        SCMHeadObserver.Collector collector = SCMHeadObserver.collect();
        source.fetch(
                new SCMSourceCriteria() {
                    @Override
                    public boolean isHead(@NonNull Probe probe, @NonNull TaskListener listener) throws IOException {
                        return probe.stat("README.md").getType() == SCMFile.Type.REGULAR_FILE;
                    }
                },
                collector,
                null,
                null);
        Map<String, SCMHead> byName = new HashMap<>();
        Map<String, SCMRevision> revByName = new HashMap<>();
        for (Map.Entry<SCMHead, SCMRevision> h : collector.result().entrySet()) {
            byName.put(h.getKey().getName(), h.getKey());
            revByName.put(h.getKey().getName(), h.getValue());
        }
        assertThat(byName.keySet(), containsInAnyOrder("PR-2", "PR-3", "PR-4", "master", "stephenc-patch-1"));
        assertThat(byName.get("PR-2"), instanceOf(PullRequestSCMHead.class));
        assertThat(((PullRequestSCMHead) byName.get("PR-2")).isMerge(), is(false));
        assertThat(
                revByName.get("PR-2"),
                is(new PullRequestSCMRevision(
                        (PullRequestSCMHead) (byName.get("PR-2")),
                        "8f1314fc3c8284d8c6d5886d473db98f2126071c",
                        "c0e024f89969b976da165eecaa71e09dc60c3da1")));
        ((PullRequestSCMRevision) revByName.get("PR-2")).validateMergeHash();

        assertThat(byName.get("PR-3"), instanceOf(PullRequestSCMHead.class));
        assertThat(((SCMHeadOrigin.Fork) byName.get("PR-3").getOrigin()).getName(), is("stephenc"));
        assertThat(((PullRequestSCMHead) byName.get("PR-3")).isMerge(), is(false));
        assertThat(
                revByName.get("PR-3"),
                is(new PullRequestSCMRevision(
                        (PullRequestSCMHead) (byName.get("PR-3")),
                        "8f1314fc3c8284d8c6d5886d473db98f2126071c",
                        "c0e024f89969b976da165eecaa71e09dc60c3da1")));

        assertThat(byName.get("PR-4"), instanceOf(PullRequestSCMHead.class));
        assertThat(((SCMHeadOrigin.Fork) byName.get("PR-4").getOrigin()).getName(), is("stephenc/jenkins-58450"));

        assertThat(byName.get("master"), instanceOf(BranchSCMHead.class));
        assertThat(revByName.get("master"), hasProperty("hash", is("8f1314fc3c8284d8c6d5886d473db98f2126071c")));
        assertThat(byName.get("stephenc-patch-1"), instanceOf(BranchSCMHead.class));
        assertThat(
                revByName.get("stephenc-patch-1"), hasProperty("hash", is("095e69602bb95a278505e937e41d505ac3cdd263")));
    }

    @Test
    public void fetchActions() throws Exception {
        assertThat(
                source.fetchActions(null, null),
                Matchers.containsInAnyOrder(
                        Matchers.is(new ObjectMetadataAction(null, "You only live once", "http://yolo.example.com")),
                        Matchers.is(new GitHubDefaultBranch("cloudbeers", "yolo", "master")),
                        instanceOf(GitHubRepoMetadataAction.class),
                        Matchers.is(new GitHubLink("icon-github-repo", "https://github.com/cloudbeers/yolo"))));
    }

    @Test
    public void getTrustedRevisionReturnsRevisionIfRepoOwnerAndPullRequestBranchOwnerAreSameWithDifferentCase()
            throws Exception {
        source.setBuildOriginPRHead(true);
        PullRequestSCMRevision revision = createRevision("CloudBeers");
        assertThat(
                source.getTrustedRevision(revision, new LogTaskListener(Logger.getAnonymousLogger(), Level.INFO)),
                sameInstance(revision));
    }

    private PullRequestSCMRevision createRevision(String sourceOwner) {
        PullRequestSCMHead head = new PullRequestSCMHead(
                "",
                sourceOwner,
                "yolo",
                "",
                0,
                new BranchSCMHead("non-null"),
                SCMHeadOrigin.DEFAULT,
                ChangeRequestCheckoutStrategy.HEAD);
        return new PullRequestSCMRevision(head, "non-null", null);
    }

    @Test
    public void doFillCredentials() throws Exception {
        final GitHubSCMSource.DescriptorImpl d = r.jenkins.getDescriptorByType(GitHubSCMSource.DescriptorImpl.class);
        final WorkflowMultiBranchProject dummy =
                r.jenkins.add(new WorkflowMultiBranchProject(r.jenkins, "dummy"), "dummy");
        SecurityRealm realm = r.jenkins.getSecurityRealm();
        AuthorizationStrategy strategy = r.jenkins.getAuthorizationStrategy();
        try {
            r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
            MockAuthorizationStrategy mockStrategy = new MockAuthorizationStrategy();
            mockStrategy.grant(Jenkins.ADMINISTER).onRoot().to("admin");
            mockStrategy.grant(Item.CONFIGURE).onItems(dummy).to("bob");
            mockStrategy.grant(Item.EXTENDED_READ).onItems(dummy).to("jim");
            r.jenkins.setAuthorizationStrategy(mockStrategy);
            try (ACLContext ctx = ACL.as(User.getById("admin", true).impersonate())) {
                ListBoxModel rsp = d.doFillCredentialsIdItems(dummy, "", "does-not-exist");
                assertThat("Expecting only the provided value so that form config unchanged", rsp, hasSize(1));
                assertThat(
                        "Expecting only the provided value so that form config unchanged",
                        rsp.get(0).value,
                        Matchers.is("does-not-exist"));
                rsp = d.doFillCredentialsIdItems(null, "", "does-not-exist");
                assertThat("Expecting just the empty entry", rsp, hasSize(1));
                assertThat("Expecting just the empty entry", rsp.get(0).value, Matchers.is(""));
            }
            try (ACLContext ctx = ACL.as(User.getById("bob", true).impersonate())) {
                ListBoxModel rsp = d.doFillCredentialsIdItems(dummy, "", "does-not-exist");
                assertThat("Expecting just the empty entry", rsp, hasSize(1));
                assertThat("Expecting just the empty entry", rsp.get(0).value, Matchers.is(""));
                rsp = d.doFillCredentialsIdItems(null, "", "does-not-exist");
                assertThat("Expecting only the provided value so that form config unchanged", rsp, hasSize(1));
                assertThat(
                        "Expecting only the provided value so that form config unchanged",
                        rsp.get(0).value,
                        Matchers.is("does-not-exist"));
            }
            try (ACLContext ctx = ACL.as(User.getById("jim", true).impersonate())) {
                ListBoxModel rsp = d.doFillCredentialsIdItems(dummy, "", "does-not-exist");
                assertThat("Expecting just the empty entry", rsp, hasSize(1));
                assertThat("Expecting just the empty entry", rsp.get(0).value, Matchers.is(""));
            }
            try (ACLContext ctx = ACL.as(User.getById("sue", true).impersonate())) {
                ListBoxModel rsp = d.doFillCredentialsIdItems(dummy, "", "does-not-exist");
                assertThat("Expecting only the provided value so that form config unchanged", rsp, hasSize(1));
                assertThat(
                        "Expecting only the provided value so that form config unchanged",
                        rsp.get(0).value,
                        Matchers.is("does-not-exist"));
            }
        } finally {
            r.jenkins.setSecurityRealm(realm);
            r.jenkins.setAuthorizationStrategy(strategy);
            r.jenkins.remove(dummy);
        }
    }

    @Test
    @Issue("JENKINS-68633")
    public void doCheckCredentialsId() {
        GitHubSCMSource.DescriptorImpl descriptor = (GitHubSCMSource.DescriptorImpl) source.getDescriptor();

        // If no credentials are supplied, display the warning
        FormValidation test = descriptor.doCheckCredentialsId(null, "", "", "", true);
        assertThat(test.kind, is(FormValidation.Kind.WARNING));
        assertThat(test.getMessage(), is("Credentials are recommended"));
        test = descriptor.doCheckCredentialsId(null, "", "", "", false);
        assertThat(test.kind, is(FormValidation.Kind.WARNING));
        assertThat(test.getMessage(), is("Credentials are recommended"));

        // If configureByUrl and credentials provided, always return OK
        test = descriptor.doCheckCredentialsId(null, "", "", "test", true);
        assertThat(test.kind, is(FormValidation.Kind.OK));
    }

    @Test
    @Issue("JENKINS-65071")
    public void testCheckIncludesBranchSCMHeadType() throws Exception {
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);
        Mockito.when(mockSCMHeadObserver.getIncludes())
                .thenReturn(Collections.singleton(new BranchSCMHead("existent-branch")));

        assertTrue(this.source.checkObserverIncludesType(mockSCMHeadObserver, BranchSCMHead.class));
        assertFalse(this.source.checkObserverIncludesType(mockSCMHeadObserver, PullRequestSCMHead.class));
        assertFalse(this.source.checkObserverIncludesType(mockSCMHeadObserver, GitHubTagSCMHead.class));
    }

    @Test
    @Issue("JENKINS-65071")
    public void testCheckIncludesPullRequestSCMHeadType() throws Exception {
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);
        Mockito.when(mockSCMHeadObserver.getIncludes())
                .thenReturn(Collections.singleton(new PullRequestSCMHead(
                        "PR-1",
                        "rfvm",
                        "http://localhost:" + githubApi.port(),
                        "master",
                        1,
                        new BranchSCMHead("master"),
                        SCMHeadOrigin.DEFAULT,
                        ChangeRequestCheckoutStrategy.MERGE)));

        assertTrue(this.source.checkObserverIncludesType(mockSCMHeadObserver, PullRequestSCMHead.class));
        assertFalse(this.source.checkObserverIncludesType(mockSCMHeadObserver, BranchSCMHead.class));
        assertFalse(this.source.checkObserverIncludesType(mockSCMHeadObserver, GitHubTagSCMHead.class));
    }

    @Test
    @Issue("JENKINS-65071")
    public void testCheckIncludesGitHubTagSCMHeadType() throws Exception {
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);
        Mockito.when(mockSCMHeadObserver.getIncludes())
                .thenReturn(
                        Collections.singleton(new GitHubTagSCMHead("non-existent-tag", System.currentTimeMillis())));

        assertTrue(this.source.checkObserverIncludesType(mockSCMHeadObserver, GitHubTagSCMHead.class));
        assertFalse(this.source.checkObserverIncludesType(mockSCMHeadObserver, PullRequestSCMHead.class));
        assertFalse(this.source.checkObserverIncludesType(mockSCMHeadObserver, BranchSCMHead.class));
    }

    @Test
    @Issue("JENKINS-65071")
    public void testCheckIncludesEmpty() throws Exception {
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);
        Mockito.when(mockSCMHeadObserver.getIncludes()).thenReturn(Collections.emptySet());

        assertFalse(this.source.checkObserverIncludesType(mockSCMHeadObserver, GitHubTagSCMHead.class));
        assertFalse(this.source.checkObserverIncludesType(mockSCMHeadObserver, PullRequestSCMHead.class));
        assertFalse(this.source.checkObserverIncludesType(mockSCMHeadObserver, BranchSCMHead.class));
    }

    @Test
    @Issue("JENKINS-65071")
    public void testCheckIncludesNull() throws Exception {
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);
        Mockito.when(mockSCMHeadObserver.getIncludes()).thenReturn(null);

        assertFalse(this.source.checkObserverIncludesType(mockSCMHeadObserver, GitHubTagSCMHead.class));
        assertFalse(this.source.checkObserverIncludesType(mockSCMHeadObserver, PullRequestSCMHead.class));
        assertFalse(this.source.checkObserverIncludesType(mockSCMHeadObserver, BranchSCMHead.class));
    }

    @Test
    @Issue("JENKINS-65071")
    public void testShouldRetrieveBranchSCMHeadType() throws Exception {
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);
        Mockito.when(mockSCMHeadObserver.getIncludes())
                .thenReturn(Collections.singleton(new BranchSCMHead("existent-branch")));

        assertTrue(this.source.shouldRetrieve(mockSCMHeadObserver, this.pushEvent, BranchSCMHead.class));
        assertFalse(this.source.shouldRetrieve(mockSCMHeadObserver, this.pushEvent, PullRequestSCMHead.class));
        assertFalse(this.source.shouldRetrieve(mockSCMHeadObserver, this.pushEvent, GitHubTagSCMHead.class));
    }

    @Test
    @Issue("JENKINS-65071")
    public void testShouldRetrievePullRequestSCMHeadType() throws Exception {
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);
        Mockito.when(mockSCMHeadObserver.getIncludes())
                .thenReturn(Collections.singleton(new PullRequestSCMHead(
                        "PR-1",
                        "rfvm",
                        "http://localhost:" + githubApi.port(),
                        "master",
                        1,
                        new BranchSCMHead("master"),
                        SCMHeadOrigin.DEFAULT,
                        ChangeRequestCheckoutStrategy.MERGE)));

        assertTrue(this.source.shouldRetrieve(mockSCMHeadObserver, this.pullRequestEvent, PullRequestSCMHead.class));
        assertFalse(this.source.shouldRetrieve(mockSCMHeadObserver, this.pullRequestEvent, BranchSCMHead.class));
        assertFalse(this.source.shouldRetrieve(mockSCMHeadObserver, this.pullRequestEvent, GitHubTagSCMHead.class));
    }

    @Test
    @Issue("JENKINS-65071")
    public void testShouldRetrieveTagSCMHeadType() throws Exception {
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);
        Mockito.when(mockSCMHeadObserver.getIncludes())
                .thenReturn(
                        Collections.singleton(new GitHubTagSCMHead("non-existent-tag", System.currentTimeMillis())));

        assertTrue(this.source.shouldRetrieve(mockSCMHeadObserver, this.pullRequestEvent, GitHubTagSCMHead.class));
        assertFalse(this.source.shouldRetrieve(mockSCMHeadObserver, this.pullRequestEvent, PullRequestSCMHead.class));
        assertFalse(this.source.shouldRetrieve(mockSCMHeadObserver, this.pullRequestEvent, BranchSCMHead.class));
    }

    @Test
    @Issue("JENKINS-65071")
    public void testShouldRetrieveNullEvent() throws Exception {
        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);
        Mockito.when(mockSCMHeadObserver.getIncludes())
                .thenReturn(
                        Collections.singleton(new GitHubTagSCMHead("non-existent-tag", System.currentTimeMillis())));

        assertTrue(this.source.shouldRetrieve(mockSCMHeadObserver, null, GitHubTagSCMHead.class));
        assertTrue(this.source.shouldRetrieve(mockSCMHeadObserver, null, PullRequestSCMHead.class));
        assertTrue(this.source.shouldRetrieve(mockSCMHeadObserver, null, BranchSCMHead.class));
    }

    @Test
    @Issue("JENKINS-67946")
    public void testUserNamesWithAndWithoutUnderscores() {
        // https://docs.github.com/en/enterprise-cloud@latest/admin/identity-and-access-management/managing-iam-for-your-enterprise/username-considerations-for-external-authentication#about-usernames-for-managed-user-accounts
        // https://github.com/github/docs/blob/bfe96c289aee3113724495a2e498c21e2ec404e4/content/admin/identity-and-access-management/using-enterprise-managed-users-for-iam/about-enterprise-managed-users.md#about--data-variablesproductprodname_emus-
        assertTrue("user_organization".matches(GitHubSCMSource.VALID_GITHUB_USER_NAME));
        assertTrue("username".matches(GitHubSCMSource.VALID_GITHUB_USER_NAME));
        assertTrue("user-name".matches(GitHubSCMSource.VALID_GITHUB_USER_NAME));
        assertTrue("user-name_organization".matches(GitHubSCMSource.VALID_GITHUB_USER_NAME));
        assertTrue("abcd".matches(GitHubSCMSource.VALID_GITHUB_USER_NAME));
        assertTrue("1234".matches(GitHubSCMSource.VALID_GITHUB_USER_NAME));
        assertTrue("user123".matches(GitHubSCMSource.VALID_GITHUB_USER_NAME));
        assertTrue("user123-org456".matches(GitHubSCMSource.VALID_GITHUB_USER_NAME));
        assertTrue("123-456".matches(GitHubSCMSource.VALID_GITHUB_USER_NAME));
        assertTrue("user123_org456".matches(GitHubSCMSource.VALID_GITHUB_USER_NAME));
        assertTrue("user123-org456-code789".matches(GitHubSCMSource.VALID_GITHUB_USER_NAME));
        assertTrue("user123-org456_code789".matches(GitHubSCMSource.VALID_GITHUB_USER_NAME));
        assertTrue("abcdefghijqlmnopkrstuvwxyz-123456789012".matches(GitHubSCMSource.VALID_GITHUB_USER_NAME));
        assertTrue("a".matches(GitHubSCMSource.VALID_GITHUB_USER_NAME));
        assertTrue("0".matches(GitHubSCMSource.VALID_GITHUB_USER_NAME));
        assertTrue("a-b-c-d-e-f-g".matches(GitHubSCMSource.VALID_GITHUB_USER_NAME));

        // Valid names should contain alphanumeric characters or single hyphens, and cannot begin or end
        // with a hyphen, and have a 39 char limit
        assertFalse("abcdefghijqlmnopkrstuvwxyz-1234567890123".matches(GitHubSCMSource.VALID_GITHUB_USER_NAME));
        assertFalse("user123@org456".matches(GitHubSCMSource.VALID_GITHUB_USER_NAME));
        assertFalse("user123.org456".matches(GitHubSCMSource.VALID_GITHUB_USER_NAME));
        assertFalse("user123--org456".matches(GitHubSCMSource.VALID_GITHUB_USER_NAME));
        assertFalse("user123-".matches(GitHubSCMSource.VALID_GITHUB_USER_NAME));
        assertFalse("-user123".matches(GitHubSCMSource.VALID_GITHUB_USER_NAME));
        assertFalse("user123__org456".matches(GitHubSCMSource.VALID_GITHUB_USER_NAME));
        assertFalse("user123_".matches(GitHubSCMSource.VALID_GITHUB_USER_NAME));
        assertFalse("_user123".matches(GitHubSCMSource.VALID_GITHUB_USER_NAME));
        assertFalse("user123-_org456".matches(GitHubSCMSource.VALID_GITHUB_USER_NAME));
        assertFalse("user123_org456-code789".matches(GitHubSCMSource.VALID_GITHUB_USER_NAME));
        assertFalse("user123_org456_code789".matches(GitHubSCMSource.VALID_GITHUB_USER_NAME));
    }
}
