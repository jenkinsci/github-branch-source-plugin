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
import okhttp3.Cache;
import okhttp3.OkHttpClient;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.Extension;
import hudson.Util;
import hudson.model.Item;
import hudson.model.PeriodicWork;
import hudson.model.Queue;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.util.JenkinsJVM;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.github.config.GitHubServerConfig;
import org.kohsuke.github.GHAppInstallationToken;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.HttpException;
import org.kohsuke.github.RateLimitHandler;
import org.kohsuke.github.authorization.ImmutableAuthorizationProvider;
import org.kohsuke.github.extras.okhttp3.OkHttpConnector;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.WARNING;

/**
 * Utilities that could perhaps be moved into {@code github-api}.
 */
public class Connector {
    private static final Logger LOGGER = Logger.getLogger(Connector.class.getName());

    private static final Map<ConnectionId, GitHubConnection> connections = new HashMap<>();
    private static final Map<GitHub, GitHubConnection> reverseLookup = new HashMap<>();

    private static final Map<TaskListener, Map<GitHub,Void>> checked = new WeakHashMap<>();
    private static final long API_URL_REVALIDATE_MILLIS = TimeUnit.MINUTES.toMillis(5);

    private static final Random ENTROPY = new Random();
    private static final String SALT = Long.toHexString(ENTROPY.nextLong());
    private static final OkHttpClient baseClient = new OkHttpClient();


    private Connector() {
        throw new IllegalAccessError("Utility class");
    }

    /**
     * Retained for binary compatibility only.
     *
     * @param context the context.
     * @param apiUri  the api endpoint.
     * @return a {@link ListBoxModel}.
     * @deprecated use {@link #listCheckoutCredentials(Item, String)}.
     */
    @NonNull
    @Deprecated
    public static ListBoxModel listScanCredentials(@CheckForNull SCMSourceOwner context, String apiUri) {
        return listScanCredentials((Item) context, apiUri);
    }

    /**
     * Populates a {@link ListBoxModel} with the scan credentials appropriate for the supplied context against the
     * supplied API endpoint.
     *
     * @param context the context.
     * @param apiUri  the api endpoint.
     * @return a {@link ListBoxModel}.
     */
    @NonNull
    public static ListBoxModel listScanCredentials(@CheckForNull Item context, String apiUri) {
        return new StandardListBoxModel()
                .includeEmptyValue()
                .includeMatchingAs(
                        context instanceof Queue.Task
                                ? ((Queue.Task) context).getDefaultAuthentication()
                                : ACL.SYSTEM,
                        context,
                        StandardUsernameCredentials.class,
                        githubDomainRequirements(apiUri),
                        githubScanCredentialsMatcher()
                );
    }

    /**
     * Retained for binary compatibility only.
     *
     * @param context           the context.
     * @param apiUri            the api endpoint.
     * @param scanCredentialsId the credentials ID.
     * @return the {@link FormValidation} results.
     * @deprecated use {@link #checkScanCredentials(Item, String, String)}
     */
    @Deprecated
    public static FormValidation checkScanCredentials(@CheckForNull SCMSourceOwner context, String apiUri, String scanCredentialsId) {
        return checkScanCredentials((Item) context, apiUri, scanCredentialsId);
    }

    /**
     * Checks the credential ID for use as scan credentials in the supplied context against the supplied API endpoint.
     *
     * @param context           the context.
     * @param apiUri            the api endpoint.
     * @param scanCredentialsId the credentials ID.
     * @return the {@link FormValidation} results.
     */
    public static FormValidation checkScanCredentials(@CheckForNull Item context, String apiUri, String scanCredentialsId) {
        if (context == null && !Jenkins.get().hasPermission(Jenkins.ADMINISTER) ||
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
            if (context != null && !context.hasPermission(CredentialsProvider.USE_ITEM)) {
                return FormValidation.ok("Credentials found");
            }
            StandardCredentials credentials = Connector.lookupScanCredentials(context, StringUtils.defaultIfEmpty(apiUri, GitHubServerConfig.GITHUB_URL), scanCredentialsId);
            if (credentials == null) {
                return FormValidation.error("Credentials not found");
            } else {
                try {
                    GitHub connector = Connector.connect(apiUri, credentials);
                    try {
                        try {
                            boolean githubAppAuthentication = credentials instanceof GitHubAppCredentials;
                            if (githubAppAuthentication) {
                                int remaining = connector.getRateLimit().getRemaining();
                                return FormValidation.ok("GHApp verified, remaining rate limit: %d", remaining);
                            }

                            return FormValidation.ok("User %s", connector.getMyself().getLogin());
                        } catch (Exception e) {
                            return FormValidation.error("Invalid credentials: %s", e.getMessage());
                        }
                    } finally {
                        Connector.release(connector);
                    }
                } catch (IllegalArgumentException | InvalidPrivateKeyException e) {
                    String msg = "Exception validating credentials " + CredentialsNameProvider.name(credentials);
                    LOGGER.log(Level.WARNING, msg, e);
                    return FormValidation.error(e, msg);
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

    /**
     * Retained for binary compatibility only.
     *
     * @param context           the context.
     * @param apiUri            the API endpoint.
     * @param scanCredentialsId the credentials to resolve.
     * @return the {@link StandardCredentials} or {@code null}
     * @deprecated use {@link #lookupScanCredentials(Item, String, String)}
     */
    @Deprecated
    @CheckForNull
    public static StandardCredentials lookupScanCredentials(@CheckForNull SCMSourceOwner context,
                                                            @CheckForNull String apiUri,
                                                            @CheckForNull String scanCredentialsId) {
        return lookupScanCredentials((Item) context, apiUri, scanCredentialsId);
    }

    /**
     * Resolves the specified scan credentials in the specified context for use against the specified API endpoint.
     *
     * @param context           the context.
     * @param apiUri            the API endpoint.
     * @param scanCredentialsId the credentials to resolve.
     * @return the {@link StandardCredentials} or {@code null}
     */
    @CheckForNull
    public static StandardCredentials lookupScanCredentials(@CheckForNull Item context,
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
                            ? ((Queue.Task) context).getDefaultAuthentication()
                            : ACL.SYSTEM,
                    githubDomainRequirements(apiUri)
                ),
                CredentialsMatchers.allOf(CredentialsMatchers.withId(scanCredentialsId), githubScanCredentialsMatcher())
            );
        }
    }

    /**
     * Retained for binary compatibility only.
     *
     * @param context           the context.
     * @param apiUri            the API endpoint.
     * @return the {@link StandardCredentials} or {@code null}
     * @deprecated use {@link #listCheckoutCredentials(Item, String)}
     */
    @NonNull
    public static ListBoxModel listCheckoutCredentials(@CheckForNull SCMSourceOwner context, String apiUri) {
        return listCheckoutCredentials((Item) context, apiUri);
    }

    /**
     * Populates a {@link ListBoxModel} with the checkout credentials appropriate for the supplied context against the
     * supplied API endpoint.
     *
     * @param context the context.
     * @param apiUri  the api endpoint.
     * @return a {@link ListBoxModel}.
     */
    @NonNull
    public static ListBoxModel listCheckoutCredentials(@CheckForNull Item context, String apiUri) {
        StandardListBoxModel result = new StandardListBoxModel();
        result.includeEmptyValue();
        result.add("- same as scan credentials -", GitHubSCMSource.DescriptorImpl.SAME);
        result.add("- anonymous -", GitHubSCMSource.DescriptorImpl.ANONYMOUS);
        return result.includeMatchingAs(
                context instanceof Queue.Task
                        ? ((Queue.Task) context).getDefaultAuthentication()
                        : ACL.SYSTEM,
                context,
                StandardUsernameCredentials.class,
                githubDomainRequirements(apiUri),
                GitClient.CREDENTIALS_MATCHER
        );
    }

    public static @Nonnull GitHub connect(@CheckForNull String apiUri, @CheckForNull StandardCredentials credentials)
            throws IOException {
        String apiUrl = Util.fixEmptyAndTrim(apiUri);
        apiUrl = apiUrl != null ? apiUrl : GitHubServerConfig.GITHUB_URL;
        String username;
        String password = null;
        String hash;
        String authHash;
        GitHubAppCredentials gitHubAppCredentials = null;
        Jenkins jenkins = Jenkins.get();
        if (credentials == null) {
            username = null;
            password = null;
            hash = "anonymous";
            authHash = "anonymous";
        } else if (credentials instanceof GitHubAppCredentials) {
            gitHubAppCredentials = (GitHubAppCredentials) credentials;
            hash = Util.getDigestOf(gitHubAppCredentials.getAppID() + gitHubAppCredentials.getOwner() + gitHubAppCredentials.getPrivateKey().getPlainText() + SALT); // want to ensure pooling by credential
            authHash = Util.getDigestOf(gitHubAppCredentials.getAppID() + "::" + gitHubAppCredentials.getOwner() + "::" + gitHubAppCredentials.getPrivateKey().getPlainText() + "::" + jenkins.getLegacyInstanceId());
            username = gitHubAppCredentials.getUsername();
        } else if (credentials instanceof StandardUsernamePasswordCredentials) {
            StandardUsernamePasswordCredentials c = (StandardUsernamePasswordCredentials) credentials;
            username = c.getUsername();
            password = c.getPassword().getPlainText();
            hash = Util.getDigestOf(password + SALT); // want to ensure pooling by credential
            authHash = Util.getDigestOf(password + "::" + jenkins.getLegacyInstanceId());
        } else {
            // TODO OAuth support
            throw new IOException("Unsupported credential type: " + credentials.getClass().getName());
        }

        ConnectionId connectionId = new ConnectionId(apiUrl, hash);

        synchronized (connections) {
            GitHubConnection record = GitHubConnection.lookup(connectionId);
            if (record == null) {
                Cache cache = getCache(jenkins, apiUrl, authHash, username);

                GitHubBuilder gb = createGitHubBuilder(apiUrl, cache);

                if (gitHubAppCredentials != null) {
                    gb.withAuthorizationProvider(gitHubAppCredentials.getAuthorizationProvider());
                } else if (username != null && password != null) {
                    // At the time of this change this works for OAuth tokens as well.
                    // This may not continue to work in the future, as GitHub has deprecated Login/Password credentials.
                    gb.withAuthorizationProvider(ImmutableAuthorizationProvider.fromLoginAndPassword(username, password));
                }

                record = GitHubConnection
                        .connect(connectionId, gb.build(), cache, credentials instanceof GitHubAppCredentials);

            }

            return record.getGitHub();
        }
    }

    /**
     * Creates a {@link GitHubBuilder} that can be used to build a {@link GitHub} instance.
     *
     * This method creates and configures a new {@link GitHubBuilder}.
     * This should be used only when {@link #connect(String, StandardCredentials)} cannot be used,
     * such as when using {@link GitHubBuilder#withJwtToken(String)} to getting the {@link GHAppInstallationToken}.
     *
     * This method intentionally does not support caching requests or {@link GitHub} instances.
     *
     * @param apiUrl the GitHub API URL to be used for the connection
     * @return a configured GitHubBuilder instance
     * @throws IOException if I/O error occurs
     */
    static GitHubBuilder createGitHubBuilder(@Nonnull String apiUrl) throws IOException {
        return createGitHubBuilder(apiUrl, null);
    }

    @Nonnull
    private static GitHubBuilder createGitHubBuilder(@Nonnull String apiUrl, @CheckForNull Cache cache) throws IOException {
        String host;
        try {
            host = new URL(apiUrl).getHost();
        } catch (MalformedURLException e) {
            throw new IOException("Invalid GitHub API URL: " + apiUrl, e);
        }

        GitHubBuilder gb = new GitHubBuilder();
        gb.withEndpoint(apiUrl);
        gb.withRateLimitChecker(new ApiRateLimitChecker.RateLimitCheckerAdapter());
        gb.withRateLimitHandler(CUSTOMIZED);

        OkHttpClient.Builder clientBuilder = baseClient.newBuilder();
        if (JenkinsJVM.isJenkinsJVM()) {
            clientBuilder.proxy(getProxy(host));
        }
        if (cache != null) {
            clientBuilder.cache(cache);
        }
        gb.withConnector(new OkHttpConnector(clientBuilder.build()));
        return gb;
    }

    @CheckForNull
    private static Cache getCache(@Nonnull Jenkins jenkins, @Nonnull String apiUrl, @Nonnull String authHash, @CheckForNull String username) {
        Cache cache = null;
        int cacheSize = GitHubSCMSource.getCacheSize();
        if (cacheSize > 0) {
            File cacheBase = new File(jenkins.getRootDir(),
                    GitHubSCMProbe.class.getName() + ".cache");
            File cacheDir = null;
            try {
                MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
                sha256.update(apiUrl.getBytes(StandardCharsets.UTF_8));
                sha256.update("::".getBytes(StandardCharsets.UTF_8));
                if (username != null) {
                    sha256.update(username.getBytes(StandardCharsets.UTF_8));
                }
                sha256.update("::".getBytes(StandardCharsets.UTF_8));
                sha256.update(authHash.getBytes(StandardCharsets.UTF_8));
                cacheDir = new File(cacheBase, Base64.encodeBase64URLSafeString(sha256.digest()));
            } catch (NoSuchAlgorithmException e) {
                // no cache for you mr non-spec compliant JVM
            }
            if (cacheDir != null) {
                cache = new Cache(cacheDir, cacheSize * 1024L * 1024L);
            }
        }
        return cache;
    }

    public static void release(@CheckForNull GitHub hub) {
        if (hub == null) {
            return;
        }

        synchronized (connections) {
            GitHubConnection record = reverseLookup.get(hub);
            if (record != null) {
                try {
                    record.release();
                } catch (IOException e) {
                    LOGGER.log(WARNING, "There is a mismatch in connect and release calls.", e);
                }
            }
        }
    }

    private static CredentialsMatcher githubScanCredentialsMatcher() {
        // TODO OAuth credentials
        return CredentialsMatchers.anyOf(CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class));
    }

    static List<DomainRequirement> githubDomainRequirements(String apiUri) {
        return URIRequirementBuilder.fromUri(StringUtils.defaultIfEmpty(apiUri, GitHubServerConfig.GITHUB_URL)).build();
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
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null || jenkins.proxy == null) {
            return Proxy.NO_PROXY;
        } else {
            return jenkins.proxy.createProxy(host);
        }
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

    /**
     * Alternative to {@link GitHub#isCredentialValid()} that relies on the cached user object in the {@link GitHub}
     * instance and hence reduced rate limit consumption.
     *
     * @param gitHub the instance to check.
     * @return {@code true} if the credentials are valid.
     */
    static boolean isCredentialValid(GitHub gitHub) {
        if (gitHub.isAnonymous()) {
            return true;
        } else {
            try {
                gitHub.getRateLimit();
                return true;
            } catch (IOException e) {
                if (LOGGER.isLoggable(FINE)) {
                    LOGGER.log(FINE, "Exception validating credentials on " + gitHub.getApiUrl(), e);
                }
                return false;
            }
        }
    }

    /*package*/ static void checkConnectionValidity(String apiUri, @NonNull TaskListener listener,
                                                    StandardCredentials credentials,
                                                    GitHub github)
            throws IOException {
        synchronized (checked) {
            Map<GitHub,Void> hubs = checked.get(listener);
            if (hubs != null && hubs.containsKey(github)) {
                // only check if not already in use
                return;
            }
            if (hubs == null) {
                hubs = new WeakHashMap<>();
                checked.put(listener, hubs);
            }
            hubs.put(github, null);
        }
        if (credentials != null && !isCredentialValid(github)) {
            String message = String.format("Invalid scan credentials %s to connect to %s, skipping",
                    CredentialsNameProvider.name(credentials), apiUri == null ? GitHubSCMSource.GITHUB_URL : apiUri);
            throw new AbortException(message);
        }
        if (!github.isAnonymous()) {
            assert credentials != null;
            listener.getLogger().println(GitHubConsoleNote.create(
                    System.currentTimeMillis(),
                    String.format("Connecting to %s using %s",
                    apiUri == null ? GitHubSCMSource.GITHUB_URL : apiUri,
                    CredentialsNameProvider.name(credentials))
            ));
        } else {
            listener.getLogger().println(GitHubConsoleNote.create(System.currentTimeMillis(), String.format(
                    "Connecting to %s with no credentials, anonymous access",
                    apiUri == null ? GitHubSCMSource.GITHUB_URL : apiUri
            )));
        }
    }

    /*package*/
    static void configureLocalRateLimitChecker(@NonNull TaskListener listener, GitHub github)
            throws IOException, InterruptedException {
        ApiRateLimitChecker.configureThreadLocalChecker(listener, github);
    }

    @Extension
    public static class UnusedConnectionDestroyer extends PeriodicWork {

        @Override
        public long getRecurrencePeriod() {
            return TimeUnit.MINUTES.toMillis(5);
        }

        @Override
        protected void doRun() throws Exception {
            // Free any connection that is unused (zero refs)
            // and has not been looked up or released for the last 30 minutes
            long unusedThreshold = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(30);

            synchronized (connections) {
                GitHubConnection.removeAllUnused(unusedThreshold);
            }
        }
    }

    private static class GitHubConnection {
        @NonNull
        private final GitHub gitHub;

        @CheckForNull
        private final Cache cache;

        private final boolean cleanupCacheFolder;
        private int usageCount = 1;
        private long lastUsed = System.currentTimeMillis();
        private long lastVerified = Long.MIN_VALUE;

        private GitHubConnection(GitHub gitHub, Cache cache, boolean cleanupCacheFolder) {
            this.gitHub = gitHub;
            this.cache = cache;
            this.cleanupCacheFolder = cleanupCacheFolder;
        }

        /**
         * Gets the {@link GitHub} instance for this connection
         *
         * @return the {@link GitHub} instance
         */
        public GitHub getGitHub() {
            return gitHub;
        }

        @CheckForNull
        private static GitHubConnection lookup(@NonNull ConnectionId connectionId) throws IOException {
            GitHubConnection record;
            record = connections.get(connectionId);
            if (record != null) {
                record.verifyConnection();
                record.usageCount += 1;
                record.lastUsed = System.currentTimeMillis();
            }
            return record;
        }

        @NonNull
        private static GitHubConnection connect(
                @NonNull ConnectionId connectionId,
                @NonNull GitHub gitHub,
                @CheckForNull Cache cache,
                boolean cleanupCacheFolder) throws IOException {
            GitHubConnection record = new GitHubConnection(gitHub, cache, cleanupCacheFolder);
            record.verifyConnection();
            connections.put(connectionId, record);
            reverseLookup.put(record.gitHub, record);
            return record;
        }


        private void release() throws IOException {
            if (this.usageCount <= 0) {
                throw new IOException("Tried to release a GitHubConnection that should have no references.");
            }

            this.usageCount -= 1;
            this.lastUsed = System.currentTimeMillis();
        }

        private static void removeAllUnused(long threshold) throws IOException {
            for (Iterator<Map.Entry<ConnectionId, GitHubConnection>> iterator = connections.entrySet().iterator();
                 iterator.hasNext(); ) {
                Map.Entry<ConnectionId, GitHubConnection> entry = iterator.next();
                try {
                    GitHubConnection record = Objects.requireNonNull(entry.getValue());
                    long lastUse = record.lastUsed;
                    if (record.usageCount == 0 && lastUse < threshold) {
                        iterator.remove();
                        reverseLookup.remove(record.gitHub);
                        if (record.cache != null && record.cleanupCacheFolder) {
                            record.cache.delete();
                            record.cache.close();
                        }
                    }
                } catch (IOException | NullPointerException e) {
                    LOGGER.log(WARNING, "Exception removing cache directory for unused connection: " + entry.getKey(), e);
                }
            }
        }

        public void verifyConnection() throws IOException {
            synchronized (this) {
                if (lastVerified > System.currentTimeMillis() - API_URL_REVALIDATE_MILLIS) {
                    return;
                }
                try {
                    gitHub.checkApiUrlValidity();
                } catch (HttpException e) {
                    String message = String.format("It seems %s is unreachable", gitHub.getApiUrl());
                    throw new IOException(message, e);
                }
                lastVerified = System.currentTimeMillis();
            }
        }
    }

    private static class ConnectionId {
        private final String apiUrl;
        private final String credentialsHash;

        private ConnectionId(String apiUrl, String credentialsHash) {
            this.apiUrl = apiUrl;
            this.credentialsHash = credentialsHash;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ConnectionId that = (ConnectionId) o;

            if (!Objects.equals(apiUrl, that.apiUrl)) {
                return false;
            }
            return StringUtils.equals(credentialsHash, that.credentialsHash);
        }

        @Override
        public int hashCode() {
            return apiUrl != null ? apiUrl.hashCode() : 0;
        }

        @Override
        public String toString() {
            return "ConnectionId{" +
                    "apiUrl='" + apiUrl + '\'' +
                    ", credentialsHash=" + credentialsHash +
                    '}';
        }

    }
}
