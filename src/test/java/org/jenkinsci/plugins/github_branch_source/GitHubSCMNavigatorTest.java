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
import com.sun.tools.internal.jxc.ap.Const;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.model.Action;
import hudson.model.TaskListener;
import hudson.util.LogTaskListener;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.scm.api.SCMFile;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMNavigatorOwner;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.SCMSourceObserver;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.api.metadata.ObjectMetadataAction;
import jenkins.scm.impl.NoOpProjectObserver;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mockito;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.hamcrest.Matchers.instanceOf;
import static org.jenkinsci.plugins.github_branch_source.Constants.GITHUB_API_URL;
import static org.jenkinsci.plugins.github_branch_source.Constants.GITHUB_RAW_URL;
import static org.junit.Assert.assertThat;

public class GitHubSCMNavigatorTest {
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
                                                .replace(GITHUB_API_URL,
                                                        "http://localhost:" + githubApi.port() + "/")
                                                .replace(GITHUB_RAW_URL,
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
    private GitHubSCMNavigator navigator;

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
                get(urlMatching(".*")).atPriority(10).willReturn(aResponse().proxiedFrom(Constants.GITHUB_API_URL)));
        githubRaw.stubFor(get(urlMatching(".*")).atPriority(10)
                .willReturn(aResponse().proxiedFrom(GITHUB_RAW_URL)));
        navigator = new GitHubSCMNavigator("http://localhost:" + githubApi.port(), "cloudbeers", null, null);
    }

    @Test
    public void fetchSmokes() throws Exception {
        final LogTaskListener listener = new LogTaskListener(Logger.getAnonymousLogger(), Level.INFO);
        final Set<String> names = new HashSet<>();
        final SCMSourceOwner owner = Mockito.mock(SCMSourceOwner.class);
        SCMSourceObserver observer = new SCMSourceObserver() {
            @NonNull
            @Override
            public SCMSourceOwner getContext() {
                return owner;
            }

            @NonNull
            @Override
            public TaskListener getListener() {
                return listener;
            }

            @NonNull
            @Override
            public ProjectObserver observe(@NonNull String projectName) throws IllegalArgumentException {
                names.add(projectName);
                return new NoOpProjectObserver();
            }

            @Override
            public void addAttribute(@NonNull String key, @Nullable Object value)
                    throws IllegalArgumentException, ClassCastException {

            }
        };
        navigator.visitSources(SCMSourceObserver.filter(observer, "yolo"));
        assertThat(names, Matchers.contains("yolo"));
    }

    @Test
    public void fetchActions() throws Exception {
        assertThat(navigator.fetchActions(Mockito.mock(SCMNavigatorOwner.class), null, null), Matchers.<Action>containsInAnyOrder(
                Matchers.<Action>is(
                        new ObjectMetadataAction("CloudBeers, Inc.", null, "https://github.com/cloudbeers")
                ),
                Matchers.<Action>is(new GitHubOrgMetadataAction("https://avatars.githubusercontent.com/u/4181899?v=3")),
                Matchers.<Action>is(new GitHubLink("icon-github-logo", "https://github.com/cloudbeers"))));
    }
}
