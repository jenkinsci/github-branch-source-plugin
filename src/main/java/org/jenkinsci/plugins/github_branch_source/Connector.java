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
import com.cloudbees.plugins.credentials.CredentialsNameProvider;
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
import hudson.model.Item;
import hudson.model.Queue;
import hudson.model.queue.Tasks;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import jenkins.model.Jenkins;
import jenkins.scm.api.SCMSourceOwner;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.github.config.GitHubServerConfig;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.RateLimitHandler;
import org.kohsuke.github.extras.OkHttpConnector;

import static org.apache.commons.lang3.StringUtils.trimToEmpty;

/**
 * Utilities that could perhaps be moved into {@code github-api}.
 */
public class Connector {
    private static final Logger LOGGER = Logger.getLogger(Connector.class.getName());

    private Connector() {
        throw new IllegalAccessError("Utility class");
    }

    public static ListBoxModel listScanCredentials(SCMSourceOwner context, String apiUri) {
        return new StandardListBoxModel()
                .includeEmptyValue()
                .includeMatchingAs(
                        context instanceof Queue.Task
                                ? Tasks.getDefaultAuthenticationOf((Queue.Task) context)
                                : ACL.SYSTEM,
                        context,
                        StandardUsernameCredentials.class,
                        githubDomainRequirements(apiUri),
                        githubScanCredentialsMatcher()
                );
    }

    public static FormValidation checkScanCredentials(SCMSourceOwner context, String apiUri, String scanCredentialsId) {
        if (context == null && !Jenkins.getActiveInstance().hasPermission(Jenkins.ADMINISTER) ||
                context != null && !context.hasPermission(Item.EXTENDED_READ)) {
            return FormValidation.ok();
        }
        if (!scanCredentialsId.isEmpty()) {
            ListBoxModel options = listScanCredentials(context, apiUri);
            boolean found = false;
            for (ListBoxModel.Option b: options) {
                if (scanCredentialsId.equals(b.value)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return FormValidation.error("Credentials not found");
            }
            if (!(context.hasPermission(Item.CONFIGURE)
                    || context.hasPermission(Item.BUILD)
                    || context.hasPermission(CredentialsProvider.USE_ITEM))) {
                return FormValidation.ok("Credentials found");
            }
            StandardCredentials credentials = Connector.lookupScanCredentials(context, apiUri, scanCredentialsId);
            if (credentials == null) {
                return FormValidation.error("Credentials not found");
            } else {
                try {
                    GitHub connector = Connector.connect(apiUri, credentials);
                    if (connector.isCredentialValid()) {
                        return FormValidation.ok();
                    } else {
                        return FormValidation.error("Invalid credentials");
                    }
                } catch (IOException e) {
                    // ignore, never thrown
                    LOGGER.log(Level.WARNING, "Exception validating credentials {0} on {1}", new Object[]{
                            CredentialsNameProvider.name(credentials), apiUri
                    });
                    return FormValidation.error("Exception validating credentials");
                }
            }
        } else {
            return FormValidation.warning("Credentials are recommended");
        }
    }

    @CheckForNull
    public static StandardCredentials lookupScanCredentials(@CheckForNull SCMSourceOwner context,
                                                                          @CheckForNull String apiUri,
                                                                          @CheckForNull String scanCredentialsId) {
        if (Util.fixEmpty(scanCredentialsId) == null) {
            return null;
        } else {
            return CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(
                    StandardUsernameCredentials.class,
                    context,
                    context instanceof Queue.Task
                            ? Tasks.getDefaultAuthenticationOf((Queue.Task) context)
                            : ACL.SYSTEM,
                    githubDomainRequirements(apiUri)
                ),
                CredentialsMatchers.allOf(CredentialsMatchers.withId(scanCredentialsId), githubScanCredentialsMatcher())
            );
        }
    }

    public static ListBoxModel listCheckoutCredentials(SCMSourceOwner context, String apiUri) {
        StandardListBoxModel result = new StandardListBoxModel();
        result.includeEmptyValue();
        result.add("- same as scan credentials -", GitHubSCMSource.DescriptorImpl.SAME);
        result.add("- anonymous -", GitHubSCMSource.DescriptorImpl.ANONYMOUS);
        return result.includeMatchingAs(
                context instanceof Queue.Task
                        ? Tasks.getDefaultAuthenticationOf((Queue.Task) context)
                        : ACL.SYSTEM,
                context,
                StandardUsernameCredentials.class,
                githubDomainRequirements(apiUri),
                GitClient.CREDENTIALS_MATCHER
        );
    }

    public static @Nonnull GitHub connect(@CheckForNull String apiUri, @CheckForNull StandardCredentials credentials) throws IOException {
        String apiUrl = Util.fixEmptyAndTrim(apiUri);
        String host;
        try {
            apiUrl = apiUrl != null ? apiUrl : GitHubServerConfig.GITHUB_URL;
            host = new URL(apiUrl).getHost();
        } catch (MalformedURLException e) {
            throw new IOException("Invalid GitHub API URL: " + apiUrl, e);
        }

        GitHubBuilder gb = new GitHubBuilder();
        gb.withEndpoint(apiUrl);
        gb.withRateLimitHandler(CUSTOMIZED);

        Cache cache = new Cache(Jenkins.getInstance().root, 10 * 1024 * 1024);
        OkHttpClient client = new OkHttpClient();
        client.setProxy(getProxy(host));
        client.setCache(cache);

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
     * @param host GitHub's hostname to build proxy to
     *
     * @return proxy to use it in connector. Should not be null as it can lead to unexpected behaviour
     */
    @Nonnull
    private static Proxy getProxy(@Nonnull String host) {
        Jenkins jenkins = GitHubWebHook.getJenkinsInstance();

        if (jenkins.proxy == null) {
            return Proxy.NO_PROXY;
        } else {
            return jenkins.proxy.createProxy(host);
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
