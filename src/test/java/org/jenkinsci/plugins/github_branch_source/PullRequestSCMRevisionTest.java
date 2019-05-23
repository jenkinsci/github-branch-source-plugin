/*
 * The MIT License
 *
 * Copyright (c) 2019 CloudBees, Inc.
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
import java.io.File;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadOrigin;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import hudson.AbortException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class PullRequestSCMRevisionTest {
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
    private GitHub github;
    private GHRepository repo;


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
        github = Connector.connect("http://localhost:" + githubApi.port(), null);
        repo = github.getRepository("cloudbeers/yolo");
    }

    public static SCMHead master = new BranchSCMHead("master");
    public static PullRequestSCMHead prHead = new PullRequestSCMHead("", "stephenc", "yolo", "master", 1, (BranchSCMHead) master,
        SCMHeadOrigin.DEFAULT, ChangeRequestCheckoutStrategy.HEAD);
    public static PullRequestSCMHead prMerge = new PullRequestSCMHead("", "stephenc", "yolo", "master", 1, (BranchSCMHead) master,
        SCMHeadOrigin.DEFAULT, ChangeRequestCheckoutStrategy.MERGE);

    @Test
    public void createHeadwithNullMergeRevision() throws Exception {
        PullRequestSCMRevision prRevision = new PullRequestSCMRevision(
            prHead, "master-revision", "pr-branch-revision");
        assertThat(prRevision.toString(), is("pr-branch-revision"));

        // equality
        assertThat(prRevision,
            is(new PullRequestSCMRevision(prHead, "master-revision", "pr-branch-revision", null)));
        assertThat(prRevision,
            not(is(new PullRequestSCMRevision(prHead, "master-revision", "pr-branch-revision", ""))));
        assertThat(prRevision,
            not(is(new PullRequestSCMRevision(prHead, "master-revision", "pr-branch-revision", "pr-merge-revision"))));

        assertThat(prRevision,
            not(is(new PullRequestSCMRevision(prMerge, "master-revision", "pr-branch-revision", null))));

        try {
            prRevision.validateMergeHash();
        } catch (AbortException e) {
            fail("Validation should succeed, but: " + e.getMessage());
        }
    }

    @Test
    public void createHeadwithMergeRevision() throws Exception {
        PullRequestSCMRevision prRevision = new PullRequestSCMRevision(
            prHead, "master-revision", "pr-branch-revision", "pr-merge-revision");
        assertThat(prRevision.toString(), is("pr-branch-revision"));

        // equality
        assertThat(prRevision,
            not(is(new PullRequestSCMRevision(prHead, "master-revision", "pr-branch-revision", null))));
        assertThat(prRevision,
            not(is(new PullRequestSCMRevision(prHead, "master-revision", "pr-branch-revision", ""))));
        assertThat(prRevision,
            is(new PullRequestSCMRevision(prHead, "master-revision", "pr-branch-revision", "pr-merge-revision")));

        assertThat(prRevision,
            not(is(new PullRequestSCMRevision(prMerge, "master-revision", "pr-branch-revision", "pr-merge-revision"))));

        try {
            prRevision.validateMergeHash();
        } catch (AbortException e) {
            fail("Validation should succeed, but: " + e.getMessage());
        }
    }

    @Test
    public void createMergewithNullMergeRevision() throws Exception {
        PullRequestSCMRevision prRevision = new PullRequestSCMRevision(
            prMerge, "master-revision", "pr-branch-revision");
        assertThat(prRevision.toString(), is("pr-branch-revision+master-revision (UNKNOWN_MERGE_STATE)"));

        // equality
        assertThat(prRevision,
            is(new PullRequestSCMRevision(prMerge, "master-revision", "pr-branch-revision", null)));
        assertThat(prRevision,
            not(is(new PullRequestSCMRevision(prMerge, "master-revision", "pr-branch-revision", ""))));
        assertThat(prRevision,
            not(is(new PullRequestSCMRevision(prMerge, "master-revision", "pr-branch-revision", "pr-merge-revision"))));

        assertThat(prRevision,
            not(is(new PullRequestSCMRevision(prHead, "master-revision", "pr-branch-revision", null))));

        try {
            prRevision.validateMergeHash();
        } catch (AbortException e) {
            fail("Validation should succeed, but: " + e.getMessage());
        }
    }

    @Test
    public void createMergewithNotMergeableRevision() throws Exception {
        PullRequestSCMRevision prRevision = new PullRequestSCMRevision(
            prMerge, "master-revision", "pr-branch-revision", PullRequestSCMRevision.NOT_MERGEABLE_HASH);
        assertThat(prRevision.toString(), is("pr-branch-revision+master-revision (NOT_MERGEABLE)"));

        // equality
        assertThat(prRevision,
            not(is(new PullRequestSCMRevision(prMerge, "master-revision", "pr-branch-revision", null))));
        assertThat(prRevision,
            not(is(new PullRequestSCMRevision(prMerge, "master-revision", "pr-branch-revision", ""))));
        assertThat(prRevision,
            not(is(new PullRequestSCMRevision(prMerge, "master-revision", "pr-branch-revision", "pr-merge-revision"))));

        assertThat(prRevision,
            not(is(new PullRequestSCMRevision(prHead, "master-revision", "pr-branch-revision", null))));

        // validation should fail for this PR.
        Exception abort = null;
        try {
            prRevision.validateMergeHash();
        } catch (Exception e) {
            abort = e;
        }
        assertThat(abort, instanceOf(AbortException.class));
        assertThat(abort.getMessage(), containsString("Not mergeable"));
    }

    @Test
    public void createMergewithMergeRevision() throws Exception {
        PullRequestSCMRevision prRevision = new PullRequestSCMRevision(
            prMerge, "master-revision", "pr-branch-revision", "pr-merge-revision");
        assertThat(prRevision.toString(), is("pr-branch-revision+master-revision (pr-merge-revision)"));

        // equality
        assertThat(prRevision,
            not(is(new PullRequestSCMRevision(prMerge, "master-revision", "pr-branch-revision", null))));
        assertThat(prRevision,
            not(is(new PullRequestSCMRevision(prMerge, "master-revision", "pr-branch-revision", ""))));
        assertThat(prRevision,
            is(new PullRequestSCMRevision(prMerge, "master-revision", "pr-branch-revision", "pr-merge-revision")));

        assertThat(prRevision,
            not(is(new PullRequestSCMRevision(prHead, "master-revision", "pr-branch-revision", "pr-merge-revision"))));

        try {
            prRevision.validateMergeHash();
        } catch (AbortException e) {
            fail("Validation should succeed, but: " + e.getMessage());
        }
    }
}
