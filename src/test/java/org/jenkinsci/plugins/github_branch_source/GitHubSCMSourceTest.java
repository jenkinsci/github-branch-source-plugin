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

import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.common.SingleRootFileSource;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.Response;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Action;
import hudson.model.TaskListener;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import jenkins.scm.api.SCMFile;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.metadata.ObjectMetadataAction;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class GitHubSCMSourceTest {
    /**
     * All tests in this class only use Jenkins for the extensions
     */
    @ClassRule
    public static JenkinsRule r = new JenkinsRule();

    public static WireMockRuleFactory factory = new WireMockRuleFactory();

    @Rule
    public WireMockRule githubRaw = factory.getRule(WireMockConfiguration.options()
            .dynamicPort()
            .usingFilesUnderClasspath("raw")
    );
    @Rule
    public WireMockRule githubApi = factory.getRule(WireMockConfiguration.options()
            .dynamicPort()
            .usingFilesUnderClasspath("api")
            .extensions(
                    new ResponseTransformer() {
                        @Override
                        public Response transform(Request request, Response response, FileSource files,
                                                  Parameters parameters) {
                            if ("application/json"
                                    .equals(response.getHeaders().getContentTypeHeader().mimeTypePart())) {
                                return Response.Builder.like(response)
                                        .but()
                                        .body(response.getBodyAsString()
                                                .replace("https://api.github.com/",
                                                        "http://localhost:" + githubApi.port() + "/")
                                                .replace("https://raw.githubusercontent.com/",
                                                        "http://localhost:" + githubRaw.port() + "/")
                                        )
                                        .build();
                            }
                            return response;
                        }

                        @Override
                        public String getName() {
                            return "url-rewrite";
                        }

                    })
    );
    private GitHubSCMSource source;

    @Before
    public void prepareMockGitHub() throws Exception {
        new File("src/test/resources/api/mappings").mkdirs();
        new File("src/test/resources/api/__files").mkdirs();
        new File("src/test/resources/raw/mappings").mkdirs();
        new File("src/test/resources/raw/__files").mkdirs();
        githubApi.enableRecordMappings(new SingleRootFileSource("src/test/resources/api/mappings"),
                new SingleRootFileSource("src/test/resources/api/__files"));
        githubRaw.enableRecordMappings(new SingleRootFileSource("src/test/resources/raw/mappings"),
                new SingleRootFileSource("src/test/resources/raw/__files"));
        githubApi.stubFor(
                get(urlMatching(".*")).atPriority(10).willReturn(aResponse().proxiedFrom("https://api.github.com/")));
        githubRaw.stubFor(get(urlMatching(".*")).atPriority(10)
                .willReturn(aResponse().proxiedFrom("https://raw.githubusercontent.com/")));
        source = new GitHubSCMSource(null, "http://localhost:" + githubApi.port(), null, null, "cloudbeers", "yolo");
    }

    @Test
    public void fetchSmokes() throws Exception {
        SCMHeadObserver.Collector collector = SCMHeadObserver.collect();
        source.fetch(new SCMSourceCriteria() {
            @Override
            public boolean isHead(@NonNull Probe probe, @NonNull TaskListener listener) throws IOException {
                return probe.stat("README.md").getType() == SCMFile.Type.REGULAR_FILE;
            }
        }, collector, null, null);
        Map<String,SCMHead> byName = new HashMap<>();
        Map<String,SCMRevision> revByName = new HashMap<>();
        for (Map.Entry<SCMHead, SCMRevision> h: collector.result().entrySet())  {
            byName.put(h.getKey().getName(), h.getKey());
            revByName.put(h.getKey().getName(), h.getValue());
        }
        assertThat(byName.keySet(), containsInAnyOrder("PR-2", "master", "stephenc-patch-1"));
        assertThat(byName.get("PR-2"), instanceOf(PullRequestSCMHead.class));
        assertThat(revByName.get("PR-2"), is((SCMRevision) new PullRequestSCMRevision((PullRequestSCMHead)(byName.get("PR-2")),
                "8f1314fc3c8284d8c6d5886d473db98f2126071c",
                "c0e024f89969b976da165eecaa71e09dc60c3da1"
        )));

        assertThat(byName.get("master"), instanceOf(BranchSCMHead.class));
        assertThat(revByName.get("master"),
                hasProperty("hash", is("8f1314fc3c8284d8c6d5886d473db98f2126071c")
                ));
        assertThat(byName.get("stephenc-patch-1"), instanceOf(BranchSCMHead.class));
        assertThat(revByName.get("stephenc-patch-1"),
                hasProperty("hash", is("095e69602bb95a278505e937e41d505ac3cdd263")
                ));
    }

    @Test
    public void fetchAltConfig() throws Exception {
        source.setBuildForkPRMerge(false);
        source.setBuildForkPRHead(true);
        source.setBuildOriginPRMerge(false);
        source.setBuildOriginPRHead(false);
        SCMHeadObserver.Collector collector = SCMHeadObserver.collect();
        source.fetch(new SCMSourceCriteria() {
            @Override
            public boolean isHead(@NonNull Probe probe, @NonNull TaskListener listener) throws IOException {
                return probe.stat("README.md").getType() == SCMFile.Type.REGULAR_FILE;
            }
        }, collector, null, null);
        Map<String,SCMHead> byName = new HashMap<>();
        Map<String,SCMRevision> revByName = new HashMap<>();
        for (Map.Entry<SCMHead, SCMRevision> h: collector.result().entrySet())  {
            byName.put(h.getKey().getName(), h.getKey());
            revByName.put(h.getKey().getName(), h.getValue());
        }
        assertThat(byName.keySet(), containsInAnyOrder("PR-2", "master", "stephenc-patch-1"));
        assertThat(byName.get("PR-2"), instanceOf(PullRequestSCMHead.class));
        assertThat(revByName.get("PR-2"), is((SCMRevision) new PullRequestSCMRevision((PullRequestSCMHead)(byName.get("PR-2")),
                "8f1314fc3c8284d8c6d5886d473db98f2126071c",
                "c0e024f89969b976da165eecaa71e09dc60c3da1"
        )));

        assertThat(byName.get("master"), instanceOf(BranchSCMHead.class));
        assertThat(revByName.get("master"),
                hasProperty("hash", is("8f1314fc3c8284d8c6d5886d473db98f2126071c")
                ));
        assertThat(byName.get("stephenc-patch-1"), instanceOf(BranchSCMHead.class));
        assertThat(revByName.get("stephenc-patch-1"),
                hasProperty("hash", is("095e69602bb95a278505e937e41d505ac3cdd263")
                ));
    }

    @Test
    public void fetchActions() throws Exception {
        assertThat(source.fetchActions(null, null), Matchers.<Action>containsInAnyOrder(
                Matchers.<Action>is(
                        new ObjectMetadataAction(null, "You only live once", "http://yolo.example.com")
                ),
                Matchers.<Action>is(
                        new GitHubDefaultBranch("cloudbeers", "yolo", "master")
                ),
                instanceOf(GitHubRepoMetadataAction.class),
                Matchers.<Action>is(new GitHubLink("icon-github-repo", "https://github.com/cloudbeers/yolo"))));
    }

    @Test
    public void getTrustedRevisionReturnsRevisionIfRepoOwnerAndPullRequestBranchOwnerAreSameWithDifferentCase() throws Exception {
        PullRequestSCMRevision revision = createRevision("CloudBeers");
        assertThat(source.getTrustedRevision(revision, null), sameInstance((SCMRevision) revision));
    }

    private PullRequestSCMRevision createRevision(String sourceOwner) {
        PullRequestSCMHead head = new PullRequestSCMHead("", false, 0, null, sourceOwner, null, null);
        return new PullRequestSCMRevision(head, null, null);
    }

}
