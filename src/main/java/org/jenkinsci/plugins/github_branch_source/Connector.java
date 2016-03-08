/*
 * The MIT License
 *
 * Copyright 2015 CloudBees, Inc.
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

import com.cloudbees.jenkins.GitHubWebHook;
import com.cloudbees.plugins.credentials.CredentialsMatcher;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.google.common.hash.Hashing;
import com.squareup.okhttp.Cache;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.OkUrlFactory;
import hudson.Util;
import hudson.security.ACL;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.util.List;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import jenkins.model.Jenkins;
import jenkins.scm.api.SCMSourceOwner;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.github.config.GitHubServerConfig;
import org.jenkinsci.plugins.github.internal.GitHubClientCacheOps;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.RateLimitHandler;
import org.kohsuke.github.extras.OkHttpConnector;

import static org.apache.commons.lang3.StringUtils.*;
import static org.jenkinsci.plugins.github.config.GitHubServerConfig.GITHUB_URL;
import static org.jenkinsci.plugins.github.internal.GitHubClientCacheOps.toCacheDir;

/**
 * Utilities that could perhaps be moved into {@code github-api}.
 */
public class Connector {

    public static @CheckForNull StandardCredentials lookupScanCredentials(@CheckForNull SCMSourceOwner context, @CheckForNull String apiUri, @CheckForNull String scanCredentialsId) {
        if (Util.fixEmpty(scanCredentialsId) == null) {
            return null;
        } else {
            return CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(
                    StandardUsernameCredentials.class,
                    context,
                    ACL.SYSTEM,
                    githubDomainRequirements(apiUri)
                ),
                CredentialsMatchers.allOf(CredentialsMatchers.withId(scanCredentialsId), githubScanCredentialsMatcher())
            );
        }
    }

    public static @Nonnull GitHub connect(@CheckForNull String apiUri, @CheckForNull StandardCredentials credentials) throws IOException {
        GitHubServerConfig config = new GitHubServerConfig(credentials!=null ? credentials.getId() : null);
        String apiUrl = Util.fixEmptyAndTrim(apiUri);
        if (apiUrl !=null) {
            config.setCustomApiUrl(true);
            config.setApiUrl(apiUrl);
        }

// Can't do this until github plugin support username/password
//        GitHub gh = GitHubServerConfig.loginToGithub().apply(config);

        GitHubBuilder gb = new GitHubBuilder();

        if (apiUrl !=null) {
            gb.withEndpoint(apiUrl);
        }

        gb.withRateLimitHandler(CUSTOMIZED);
        OkHttpClient client = new OkHttpClient().setProxy(getProxy(defaultIfBlank(apiUrl, GITHUB_URL)));
        client.setCache(GitHubClientCacheOps.toCacheDir().apply(config));
        if (config.getClientCacheSize() > 0) {
            Cache cache = toCacheDir().apply(config);
            client.setCache(cache);
        }

        gb.withConnector(new OkHttpConnector(new OkUrlFactory(client)));

        if (credentials == null) {
            // nothing further to configure
        } else if (credentials instanceof StandardUsernamePasswordCredentials) {
            StandardUsernamePasswordCredentials c = (StandardUsernamePasswordCredentials) credentials;
            gb.withPassword(c.getUsername(), c.getPassword().getPlainText());
        } else {
            // TODO OAuth support
            throw new IOException("Unsupported credential type: " + credentials.getClass().getName());
        }

        return gb.build();
    }

    public static void fillScanCredentialsIdItems(StandardListBoxModel result, @CheckForNull SCMSourceOwner context, @CheckForNull String apiUri) {
        result.withMatching(githubScanCredentialsMatcher(), CredentialsProvider.lookupCredentials(StandardUsernameCredentials.class, context, ACL.SYSTEM, githubDomainRequirements(apiUri)));
    }

    public static void fillCheckoutCredentialsIdItems(StandardListBoxModel result, @CheckForNull SCMSourceOwner context, @CheckForNull String apiUri) {
        result.withMatching(GitClient.CREDENTIALS_MATCHER, CredentialsProvider.lookupCredentials(StandardUsernameCredentials.class, context, ACL.SYSTEM, githubDomainRequirements(apiUri)));
    }

    private static CredentialsMatcher githubScanCredentialsMatcher() {
        // TODO OAuth credentials
        return CredentialsMatchers.anyOf(CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class));
    }

    private static List<DomainRequirement> githubDomainRequirements(String apiUri) {
        return URIRequirementBuilder.fromUri(StringUtils.defaultIfEmpty(apiUri, "https://github.com")).build();
    }

    /**
     * Uses proxy if configured on pluginManager/advanced page
     *
     * @param apiUrl GitHub's url to build proxy to
     *
     * @return proxy to use it in connector. Should not be null as it can lead to unexpected behaviour
     */
    @Nonnull
    private static Proxy getProxy(String apiUrl) {
        Jenkins jenkins = GitHubWebHook.getJenkinsInstance();

        if (jenkins.proxy == null) {
            return Proxy.NO_PROXY;
        } else {
            return jenkins.proxy.createProxy(apiUrl);
        }
    }

    /**
     * @param config url and creds id to be hashed
     *
     * @return unique id for folder name to create cache inside of base cache dir
     */
    private static String hashed(GitHubServerConfig config) {
        return Hashing.murmur3_32().newHasher()
                .putString(trimToEmpty(config.getApiUrl()))
                .putString(trimToEmpty(config.getCredentialsId())).hash().toString();
    }


    private Connector() {}

    /**
     * Fail immediately and throw a customized exception.
     */
    public static final RateLimitHandler CUSTOMIZED = new RateLimitHandler() {

        @Override
        public void onError(IOException e, HttpURLConnection uc) throws IOException {
            try {
                long limit = Long.parseLong(uc.getHeaderField("X-RateLimit-Limit"));
                long remaining = Long.parseLong(uc.getHeaderField("X-RateLimit-Remaining"));
                long reset = Long.parseLong(uc.getHeaderField("X-RateLimit-Reset"));

                throw new RateLimitExceededException("GitHub API rate limit exceeded", limit, remaining, reset);
            } catch (NumberFormatException nfe) {
                // Something wrong happened
                throw new IOException(nfe);
            }
        }

    };

}
