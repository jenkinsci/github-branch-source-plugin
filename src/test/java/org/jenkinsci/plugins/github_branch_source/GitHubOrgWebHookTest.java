/*
 * The MIT License
 *
 * Copyright 2019 CloudBees, Inc.
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

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.github.GitHub;

@WithJenkins
class GitHubOrgWebHookTest {

    @RegisterExtension
    private static final WireMockExtension WIRE_MOCK_EXTENSION = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private JenkinsRule r;

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        r = rule;
    }

    @Issue("JENKINS-58942")
    @Test
    void registerCustom() throws Exception {
        System.setProperty("jenkins.hook.url", "https://mycorp/hook-proxy/");
        // Return 404 for /rate_limit
        WIRE_MOCK_EXTENSION.stubFor(
                get(urlEqualTo("/api/rate_limit")).willReturn(aResponse().withStatus(404)));

        // validate api url
        WIRE_MOCK_EXTENSION.stubFor(get(urlEqualTo("/api/"))
                .willReturn(aResponse().withBody("{\"rate_limit_url\": \"https://localhost/placeholder/\"}")));

        WIRE_MOCK_EXTENSION.stubFor(
                get(urlEqualTo("/api/users/myorg")).willReturn(aResponse().withBody("{\"login\":\"myorg\"}")));
        WIRE_MOCK_EXTENSION.stubFor(get(urlEqualTo("/api/orgs/myorg"))
                .willReturn(aResponse().withBody("{\"login\":\"myorg\",\"html_url\":\"https://github.com/myorg\"}")));
        WIRE_MOCK_EXTENSION.stubFor(
                get(urlEqualTo("/api/orgs/myorg/hooks")).willReturn(aResponse().withBody("[]")));
        WIRE_MOCK_EXTENSION.stubFor(post(urlEqualTo("/api/orgs/myorg/hooks"))
                .withRequestBody(matchingJsonPath("$.config.url", equalTo("https://mycorp/hook-proxy/github-webhook/")))
                .willReturn(aResponse().withBody("{}")));
        GitHub hub = Connector.connect("http://localhost:" + WIRE_MOCK_EXTENSION.getPort() + "/api/", null);
        try {
            GitHubOrgWebHook.register(hub, "myorg");
        } finally {
            Connector.release(hub);
        }
    }
}
