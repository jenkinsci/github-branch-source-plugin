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
import com.google.common.collect.Sets;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.model.Action;
import hudson.model.Item;
import hudson.model.TaskListener;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.AuthorizationStrategy;
import hudson.security.SecurityRealm;
import hudson.util.ListBoxModel;
import hudson.util.LogTaskListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMNavigatorOwner;
import jenkins.scm.api.SCMSourceObserver;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.api.metadata.ObjectMetadataAction;
import jenkins.scm.api.trait.SCMTrait;
import jenkins.scm.impl.NoOpProjectObserver;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.MockFolder;
import org.mockito.Mock;
import org.mockito.Mockito;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
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

    @Mock
    private SCMSourceOwner scmSourceOwner;

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
                get(urlMatching(".*")).atPriority(10).willReturn(aResponse().proxiedFrom("https://api.github.com/")));
        githubRaw.stubFor(get(urlMatching(".*")).atPriority(10)
                .willReturn(aResponse().proxiedFrom("https://raw.githubusercontent.com/")));
        navigator = new GitHubSCMNavigator("http://localhost:" + githubApi.port(), "cloudbeers", null, null);
    }

    @Test
    public void fetchSmokes() throws Exception {
        final Set<String> projectNames = new HashSet<>();
        final SCMSourceObserver observer = getObserver(projectNames);

        navigator.visitSources(SCMSourceObserver.filter(observer, "yolo"));

        assertThat(projectNames, Matchers.contains("yolo"));
    }

    @Test
    public void fetchReposFromTeamSlug() throws Exception {
        final Set<String> projectNames = new HashSet<>();
        final SCMSourceObserver observer = getObserver(projectNames);

        List<SCMTrait<? extends SCMTrait<?>>> traits = new ArrayList<>(navigator.getTraits());
        traits.add(new TeamSlugTrait("justice-league"));
        navigator.setTraits(traits);
        navigator.visitSources(SCMSourceObserver.filter(observer, "Hello-World", "github-branch-source-plugin"));

        assertEquals(projectNames, Sets.newHashSet("Hello-World", "github-branch-source-plugin"));
    }

    @Test
    public void fetchReposWithOrg() throws Exception {
        final Set<String> projectNames = new HashSet<>();
        final SCMSourceObserver observer = getObserver(projectNames);

        navigator.visitSources(SCMSourceObserver.filter(observer, "Hello-World", "github-branch-source-plugin"));

        assertEquals(projectNames, Sets.newHashSet("Hello-World", "github-branch-source-plugin"));
    }

    @Test
    public void appliesFilters() throws Exception {
        final Set<String> projectNames = new HashSet<>();
        final SCMSourceObserver observer = getObserver(projectNames);

        navigator.visitSources(SCMSourceObserver.filter(observer, "Hello-World", "other-repo"));

        assertEquals(projectNames, Collections.singleton("Hello-World"));
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

    @Test
    public void doFillScanCredentials() throws Exception {
        final GitHubSCMNavigator.DescriptorImpl d =
                r.jenkins.getDescriptorByType(GitHubSCMNavigator.DescriptorImpl.class);
        final MockFolder dummy = r.createFolder("dummy");
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
                assertThat("Expecting only the provided value so that form config unchanged", rsp.get(0).value,
                        is("does-not-exist"));
                rsp = d.doFillCredentialsIdItems(null, "", "does-not-exist");
                assertThat("Expecting just the empty entry", rsp, hasSize(1));
                assertThat("Expecting just the empty entry", rsp.get(0).value, is(""));
            }
            try (ACLContext ctx = ACL.as(User.getById("bob", true).impersonate())) {
                ListBoxModel rsp = d.doFillCredentialsIdItems(dummy, "", "does-not-exist");
                assertThat("Expecting just the empty entry", rsp, hasSize(1));
                assertThat("Expecting just the empty entry", rsp.get(0).value, is(""));
                rsp = d.doFillCredentialsIdItems(null, "", "does-not-exist");
                assertThat("Expecting only the provided value so that form config unchanged", rsp, hasSize(1));
                assertThat("Expecting only the provided value so that form config unchanged", rsp.get(0).value,
                        is("does-not-exist"));
            }
            try (ACLContext ctx = ACL.as(User.getById("jim", true).impersonate())) {
                ListBoxModel rsp = d.doFillCredentialsIdItems(dummy, "", "does-not-exist");
                assertThat("Expecting just the empty entry", rsp, hasSize(1));
                assertThat("Expecting just the empty entry", rsp.get(0).value, is(""));
            }
            try (ACLContext ctx = ACL.as(User.getById("sue", true).impersonate())) {
                ListBoxModel rsp = d.doFillCredentialsIdItems(dummy, "", "does-not-exist");
                assertThat("Expecting only the provided value so that form config unchanged", rsp, hasSize(1));
                assertThat("Expecting only the provided value so that form config unchanged", rsp.get(0).value, is("does-not-exist"));
            }
        } finally {
            r.jenkins.setSecurityRealm(realm);
            r.jenkins.setAuthorizationStrategy(strategy);
            r.jenkins.remove(dummy);
        }
    }

    private SCMSourceObserver getObserver(Collection<String> names){
        return new SCMSourceObserver() {
            @NonNull
            @Override
            public SCMSourceOwner getContext() {
                return scmSourceOwner;
            }

            @NonNull
            @Override
            public TaskListener getListener() {
                return new LogTaskListener(Logger.getAnonymousLogger(), Level.INFO);
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
    }

}
