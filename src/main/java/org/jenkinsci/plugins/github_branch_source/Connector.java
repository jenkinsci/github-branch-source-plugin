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
import com.squareup.okhttp.Cache;
import com.squareup.okhttp.CacheControl;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.OkUrlFactory;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.Extension;
import hudson.Util;
import hudson.model.Item;
import hudson.model.PeriodicWork;
import hudson.model.Queue;
import hudson.model.TaskListener;
import hudson.model.queue.Tasks;
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
import java.util.Random;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMSourceOwner;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.github.config.GitHubServerConfig;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.github.GHRateLimit;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.HttpConnector;
import org.kohsuke.github.RateLimitHandler;
import org.kohsuke.github.extras.OkHttpConnector;

import static java.util.logging.Level.FINE;

/**
 * Utilities that could perhaps be moved into {@code github-api}.
 */
public class Connector {
    private static final Logger LOGGER = Logger.getLogger(Connector.class.getName());

    private static final Map<GitHub,Long> lastUsed = new HashMap<>();
    private static final Map<Details, GitHub> githubs = new HashMap<>();
    private static final Map<GitHub, Integer> usage = new HashMap<>();
    private static final Map<TaskListener, Map<GitHub,Void>> checked = new WeakHashMap<>();
    private static final long API_URL_REVALIDATE_MILLIS = TimeUnit.MINUTES.toMillis(5);
    private static final Map<String,Long> apiUrlValid = new LinkedHashMap<String,Long>(){
        @Override
        protected boolean removeEldestEntry(Map.Entry<String,Long> eldest) {
            Long t = eldest.getValue();
            return t == null || t < System.currentTimeMillis() - API_URL_REVALIDATE_MILLIS;
        }
    };
    private static final double MILLIS_PER_HOUR = TimeUnit.HOURS.toMillis(1);
    private static final Random ENTROPY = new Random();
    private static final String SALT = Long.toHexString(ENTROPY.nextLong());

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
            StandardCredentials credentials = Connector.lookupScanCredentials(context, apiUri, scanCredentialsId);
            if (credentials == null) {
                return FormValidation.error("Credentials not found");
            } else {
                try {
                    GitHub connector = Connector.connect(apiUri, credentials);
                    try {
                        try {
                            return FormValidation.ok("User %s", connector.getMyself().getLogin());
                        } catch (IOException e){
                            return FormValidation.error("Invalid credentials");
                        }
                    } finally {
                        Connector.release(connector);
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

    public static void checkApiUrlValidity(@Nonnull GitHub gitHub, @CheckForNull StandardCredentials credentials) throws IOException {
        String hash;
        if (credentials == null) {
            hash = "anonymous";
        } else if (credentials instanceof StandardUsernamePasswordCredentials) {
            StandardUsernamePasswordCredentials c = (StandardUsernamePasswordCredentials) credentials;
            hash = Util.getDigestOf(c.getPassword().getPlainText() + SALT);
        } else {
            // TODO OAuth support
            throw new IOException("Unsupported credential type: " + credentials.getClass().getName());
        }
        String key = gitHub.getApiUrl() + "::" + hash;
        synchronized (apiUrlValid) {
            Long last = apiUrlValid.get(key);
            if (last != null && last > System.currentTimeMillis() - API_URL_REVALIDATE_MILLIS) {
                return;
            }
            gitHub.checkApiUrlValidity();
            apiUrlValid.put(key, System.currentTimeMillis());
        }
    }

    public static @Nonnull GitHub connect(@CheckForNull String apiUri, @CheckForNull StandardCredentials credentials) throws IOException {
        String apiUrl = Util.fixEmptyAndTrim(apiUri);
        apiUrl = apiUrl != null ? apiUrl : GitHubServerConfig.GITHUB_URL;
        String username;
        String password;
        String hash;
        String authHash;
        Jenkins jenkins = Jenkins.get();
        if (credentials == null) {
            username = null;
            password = null;
            hash = "anonymous";
            authHash = "anonymous";
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
        synchronized (githubs) {
            Details details = new Details(apiUrl, hash);
            GitHub hub = githubs.get(details);
            if (hub != null) {
                Integer count = usage.get(hub);
                usage.put(hub, count == null ? 1 : Math.max(count + 1, 1));
                return hub;
            }
            String host;
            try {
                host = new URL(apiUrl).getHost();
            } catch (MalformedURLException e) {
                throw new IOException("Invalid GitHub API URL: " + apiUrl, e);
            }

            GitHubBuilder gb = new GitHubBuilder();
            gb.withEndpoint(apiUrl);
            gb.withRateLimitHandler(CUSTOMIZED);

            OkHttpClient client = new OkHttpClient().setProxy(getProxy(host));

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
                    Cache cache = new Cache(cacheDir, cacheSize * 1024L * 1024L);
                    client.setCache(cache);
                }
            }

            if (client.getCache() != null) {
                OkHttpClient clientNoCache = new OkHttpClient().setProxy(getProxy(host));
                gb.withConnector(new ForceValidationOkHttpConnector(client, clientNoCache));
            } else {
                gb.withConnector(new OkHttpConnector(new OkUrlFactory(client)));
            }

            if (username != null) {
                gb.withPassword(username, password);
            }

            hub = gb.build();
            githubs.put(details, hub);
            usage.put(hub, 1);
            lastUsed.remove(hub);
            return hub;
        }
    }

    public static void release(@CheckForNull GitHub hub) {
        if (hub == null) {
            return;
        }
        synchronized (githubs) {
            Integer count = usage.get(hub);
            if (count == null) {
                // it was untracked, forget about it
                return;
            }
            if (count <= 1) {
                // exclusive
                usage.put(hub, 0);
                lastUsed.put(hub, System.currentTimeMillis());
            } else {
                // shared
                usage.put(hub, count - 1);
            }
        }
    }

    private static void unused(@Nonnull GitHub hub) {
        synchronized (githubs) {
            Integer count = usage.get(hub);
            if (count == null) {
                // it was untracked, forget about it
                return;
            }
            if (count <= 1) {
                // only remove if it is actually unused now
                // exclusive
                usage.remove(hub);
                // we could use multiple maps, but we expect only a handful of entries and mostly the shared path
                // so we can just walk the forward map
                for (Iterator<Map.Entry<Details, GitHub>> iterator = githubs.entrySet().iterator();
                     iterator.hasNext(); ) {
                    Map.Entry<Details, GitHub> entry = iterator.next();
                    if (hub == entry.getValue()) {
                        iterator.remove();
                        break;
                    }
                }
            }
        }
    }

    private static CredentialsMatcher githubScanCredentialsMatcher() {
        // TODO OAuth credentials
        return CredentialsMatchers.anyOf(CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class));
    }

    static List<DomainRequirement> githubDomainRequirements(String apiUri) {
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
                gitHub.getMyself();
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
        synchronized (githubs) {
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
    static void checkApiRateLimit(@NonNull TaskListener listener, GitHub github)
            throws IOException, InterruptedException {
        boolean check = true;
        while (check) {
            check = false;
            long start = System.currentTimeMillis();
            GHRateLimit rateLimit = github.rateLimit();
            long rateLimitResetMillis = rateLimit.getResetDate().getTime() - start;
            double resetProgress = rateLimitResetMillis / MILLIS_PER_HOUR;
            // the buffer is how much we want to avoid using to cover unplanned over-use
            int buffer = Math.max(15, rateLimit.limit / 20);
            // the burst is how much we want to allow for speedier response outside of the throttle
            int burst = rateLimit.limit < 1000 ? Math.max(5, rateLimit.limit / 10) : Math.max(200, rateLimit.limit / 5);
            // the ideal is how much remaining we should have (after a burst)
            int ideal = (int) ((rateLimit.limit - buffer - burst) * resetProgress) + buffer;
            if (rateLimit.remaining >= ideal && rateLimit.remaining < ideal + buffer) {
                listener.getLogger().println(GitHubConsoleNote.create(start, String.format(
                        "GitHub API Usage: Current quota has %d remaining (%d under budget). Next quota of %d in %s",
                        rateLimit.remaining, rateLimit.remaining - ideal, rateLimit.limit,
                        Util.getTimeSpanString(rateLimitResetMillis)
                )));
            } else  if (rateLimit.remaining < ideal) {
                check = true;
                final long expiration;
                if (rateLimit.remaining < buffer) {
                    // nothing we can do, we have burned into our buffer, wait for reset
                    // we add a little bit of random to prevent CPU overload when the limit is due to reset but GitHub
                    // hasn't actually reset yet (clock synchronization is a hard problem)
                    if (rateLimitResetMillis < 0) {
                        expiration = System.currentTimeMillis() + ENTROPY.nextInt(65536); // approx 1 min
                        listener.getLogger().println(GitHubConsoleNote.create(System.currentTimeMillis(), String.format(
                                "GitHub API Usage: Current quota has %d remaining (%d over budget). Next quota of %d due now. Sleeping for %s.",
                                rateLimit.remaining, ideal - rateLimit.remaining, rateLimit.limit,
                                Util.getTimeSpanString(expiration - System.currentTimeMillis())
                        )));
                    } else {
                        expiration = rateLimit.getResetDate().getTime() + ENTROPY.nextInt(65536); // approx 1 min
                        listener.getLogger().println(GitHubConsoleNote.create(System.currentTimeMillis(), String.format(
                                "GitHub API Usage: Current quota has %d remaining (%d over budget). Next quota of %d in %s. Sleeping until reset.",
                                rateLimit.remaining, ideal - rateLimit.remaining, rateLimit.limit,
                                Util.getTimeSpanString(rateLimitResetMillis)
                        )));
                    }
                } else {
                    // work out how long until remaining == ideal + 0.1 * buffer (to give some spend)
                    double targetFraction = (rateLimit.remaining - buffer * 1.1) / (rateLimit.limit - buffer - burst);
                    expiration = rateLimit.getResetDate().getTime()
                            - Math.max(0, (long) (targetFraction * MILLIS_PER_HOUR))
                            + ENTROPY.nextInt(1000);
                    listener.getLogger().println(GitHubConsoleNote.create(System.currentTimeMillis(), String.format(
                            "GitHub API Usage: Current quota has %d remaining (%d over budget). Next quota of %d in %s. Sleeping for %s.",
                            rateLimit.remaining, ideal - rateLimit.remaining, rateLimit.limit,
                            Util.getTimeSpanString(rateLimitResetMillis),
                            Util.getTimeSpanString(expiration - System.currentTimeMillis())
                    )));
                }
                long nextNotify = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(3);
                while (expiration > System.currentTimeMillis()) {
                    if (Thread.interrupted()) {
                        throw new InterruptedException();
                    }
                    long sleep = Math.min(expiration, nextNotify) - System.currentTimeMillis();
                    if (sleep > 0) {
                        Thread.sleep(sleep);
                    }
                    // A random straw poll of users concluded that 3 minutes without any visible progress in the logs
                    // is the point after which people believe that the process is dead.
                    nextNotify += TimeUnit.SECONDS.toMillis(180);
                    long now = System.currentTimeMillis();
                    if (now < expiration) {
                        GHRateLimit current = github.getRateLimit();
                        if (current.remaining > rateLimit.remaining
                                || current.getResetDate().getTime() > rateLimit.getResetDate().getTime()) {
                            listener.getLogger().println(GitHubConsoleNote.create(now,
                                    "GitHub API Usage: The quota may have been refreshed earlier than expected, rechecking..."
                            ));
                            break;
                        }
                        listener.getLogger().println(GitHubConsoleNote.create(now, String.format(
                                "GitHub API Usage: Still sleeping, now only %s remaining.",
                                Util.getTimeSpanString(expiration - now)
                        )));
                    }
                }
            }
        }
    }

    @Extension
    public static class UnusedConnectionDestroyer extends PeriodicWork {

        @Override
        public long getRecurrencePeriod() {
            return TimeUnit.SECONDS.toMillis(15);
        }

        @Override
        protected void doRun() throws Exception {
            // free any connection unused for the last 5 minutes
            long threshold = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(5);
            synchronized (githubs) {
                for (Iterator<Map.Entry<GitHub, Long>> iterator = lastUsed.entrySet().iterator();
                     iterator.hasNext(); ) {
                    Map.Entry<GitHub, Long> entry = iterator.next();
                    Long lastUse = entry.getValue();
                    if (lastUse == null || lastUse < threshold) {
                        iterator.remove();
                        unused(entry.getKey());
                    }
                }
            }
        }
    }

    private static class Details {
        private final String apiUrl;
        private final String credentialsHash;

        private Details(String apiUrl, String credentialsHash) {
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

            Details details = (Details) o;

            if (apiUrl == null ? details.apiUrl != null : !apiUrl.equals(details.apiUrl)) {
                return false;
            }
            return StringUtils.equals(credentialsHash, details.credentialsHash);
        }

        @Override
        public int hashCode() {
            return apiUrl != null ? apiUrl.hashCode() : 0;
        }

        @Override
        public String toString() {
            return "Details{" +
                    "apiUrl='" + apiUrl + '\'' +
                    ", credentialsHash=" + credentialsHash +
                    '}';
        }

    }

    /**
     * A {@link HttpConnector} that uses {@link OkHttpConnector} when caching is enabled.
     * Starts with the {@code Cache-Control} header configured to always revalidate requests
     * against the remote server using conditional GET requests.
     * Allows Jenkins to fallback to uncached query if requests fail due to flaky caching.
     */
    @Restricted(NoExternalUse.class)
    /*package*/ static class ForceValidationOkHttpConnector extends OkHttpConnector {
        private static final String FORCE_VALIDATION = new CacheControl.Builder()
                .maxAge(0, TimeUnit.SECONDS)
                .build()
                .toString();
        private static final String HEADER_NAME = "Cache-Control";
        private final OkHttpConnector uncachedConnector;

        public ForceValidationOkHttpConnector(OkHttpClient client, OkHttpClient uncachedClient) {
            super(new OkUrlFactory(client));
            this.uncachedConnector = new OkHttpConnector(new OkUrlFactory(uncachedClient));
        }

        /*package*/ OkHttpConnector getUncachedConnector() {
            return uncachedConnector;
        }

        @Override
        public HttpURLConnection connect(URL url) throws IOException {
            HttpURLConnection connection = super.connect(url);
            connection.setRequestProperty(HEADER_NAME, FORCE_VALIDATION);
            return connection;
        }
    }
}
