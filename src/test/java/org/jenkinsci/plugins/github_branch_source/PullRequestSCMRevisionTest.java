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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.scm.api.SCMFile;
import jenkins.scm.api.SCMFileSystem;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadOrigin;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;

import org.antlr.v4.runtime.misc.NotNull;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import hudson.AbortException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

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
        repo = github.getRepository("stephenc/yolo");
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


        // validation should fail for this PR.
        Exception abort = null;
        try {
            prRevision.validateMergeHash(repo);
        } catch (Exception e) {
            abort = e;
        }
        assertThat(abort, instanceOf(AbortException.class));
        assertThat(abort.getMessage(), containsString("Not a merge head"));

    }

    @Test
    public void createMergewithNullMergeRevision() throws Exception {
        PullRequestSCMRevision prRevision = new PullRequestSCMRevision(
            prMerge, "master-revision", "pr-branch-revision");
        assertThat(prRevision.toString(), is("pr-branch-revision+master-revision (null)"));

        // equality
        assertThat(prRevision,
            is(new PullRequestSCMRevision(prMerge, "master-revision", "pr-branch-revision", null)));
        assertThat(prRevision,
            not(is(new PullRequestSCMRevision(prMerge, "master-revision", "pr-branch-revision", ""))));
        assertThat(prRevision,
            not(is(new PullRequestSCMRevision(prMerge, "master-revision", "pr-branch-revision", "pr-merge-revision"))));

        assertThat(prRevision,
            not(is(new PullRequestSCMRevision(prHead, "master-revision", "pr-branch-revision", null))));

        // validation should fail for this PR.
        Exception abort = null;
        try {
            prRevision.validateMergeHash(repo);
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
    }

    @Test
    public void createMergewithMergeRevision_validation_valid() throws Exception {
        PullRequestSCMRevision prRevision = new PullRequestSCMRevision(
            prMerge,
            "8f1314fc3c8284d8c6d5886d473db98f2126071c",
            "c0e024f89969b976da165eecaa71e09dc60c3da1",
            "38814ca33833ff5583624c29f305be9133f27a40");

        assertThat(prRevision.toString(), is("c0e024f89969b976da165eecaa71e09dc60c3da1+8f1314fc3c8284d8c6d5886d473db98f2126071c (38814ca33833ff5583624c29f305be9133f27a40)"));
        prRevision.validateMergeHash(repo);
    }

    @Test
    public void createMergewithMergeRevision_validation_invalid() throws Exception {
        PullRequestSCMRevision prRevision = new PullRequestSCMRevision(
            prMerge,
            "INVALIDc3c8284d8c6d5886d473db98f2126071c",
            "c0e024f89969b976da165eecaa71e09dc60c3da1",
            "38814ca33833ff5583624c29f305be9133f27a40");

        assertThat(prRevision.toString(), is("c0e024f89969b976da165eecaa71e09dc60c3da1+INVALIDc3c8284d8c6d5886d473db98f2126071c (38814ca33833ff5583624c29f305be9133f27a40)"));

        // validation should fail for this PR.
        Exception abort = null;
        try {
            prRevision.validateMergeHash(repo);
        } catch (Exception e) {
            abort = e;
        }
        assertThat(abort, instanceOf(AbortException.class));
        assertThat(abort.getMessage(), containsString("Head and base commits do match merge commit"));
    }

}
