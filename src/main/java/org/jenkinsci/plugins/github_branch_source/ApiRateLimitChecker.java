package org.jenkinsci.plugins.github_branch_source;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Util;
import hudson.model.TaskListener;
import hudson.util.LogTaskListener;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.github.config.GitHubServerConfig;
import org.kohsuke.github.GHRateLimit;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.RateLimitChecker;

@SuppressFBWarnings("DMI_RANDOM_USED_ONLY_ONCE") // https://github.com/spotbugs/spotbugs/issues/1539
public enum ApiRateLimitChecker {

    /** Attempt to evenly distribute GitHub API requests. */
    ThrottleForNormalize(Messages.ApiRateLimitChecker_ThrottleForNormalize()) {
        @Override
        public LocalChecker getChecker(@NonNull TaskListener listener, String apiUrl) {
            return new LocalChecker(listener) {
                @Override
                long checkRateLimitImpl(@NonNull GHRateLimit.Record rateLimit, long count, long now)
                        throws InterruptedException {
                    long expiration = now;
                    // the buffer is how much we want to avoid using to cover unplanned over-use
                    int buffer = calculateBuffer(rateLimit.getLimit());
                    if (rateLimit.getRemaining() < buffer) {
                        // nothing we can do, we have burned into our minimum buffer, wait for reset
                        expiration = calculateExpirationWhenBufferExceeded(rateLimit, now, buffer);
                    } else {
                        // the burst is how much we want to allow for speedier response outside of the throttle
                        int burst = calculateNormalizedBurst(rateLimit.getLimit());
                        // the ideal is how much remaining we should have (after a burst)
                        long rateLimitResetMillis = rateLimit.getResetDate().getTime() - now;
                        double resetProgress = Math.max(0, rateLimitResetMillis / MILLIS_PER_HOUR);
                        int ideal = (int) ((rateLimit.getLimit() - buffer - burst) * resetProgress) + buffer;
                        if (rateLimit.getRemaining() < ideal) {
                            // work out how long until remaining == ideal + 0.1 * buffer (to give some spend)
                            double targetFraction =
                                    (rateLimit.getRemaining() - buffer * 1.1) / (rateLimit.getLimit() - buffer - burst);
                            expiration = rateLimit.getResetDate().getTime()
                                    - Math.max(0, (long) (targetFraction * MILLIS_PER_HOUR))
                                    + ENTROPY.nextInt(1000);
                            writeLog(String.format(
                                    "Jenkins-Imposed API Limiter: Current quota for Github API usage has %d remaining (%d over budget). Next quota of %d in %s. Sleeping for %s.",
                                    rateLimit.getRemaining(),
                                    ideal - rateLimit.getRemaining(),
                                    rateLimit.getLimit(),
                                    Util.getTimeSpanString(rateLimitResetMillis),
                                    // The GitHubRateLimitChecker adds a one second sleep to each notification
                                    // loop
                                    Util.getTimeSpanString(1000 + expiration - now)));
                        }
                    }
                    if (expiration != now) {
                        writeLog(
                                "Jenkins is attempting to evenly distribute GitHub API requests. "
                                        + "To configure a different rate limiting strategy, such as having Jenkins restrict GitHub API requests only when near or above the GitHub rate limit, go to \"GitHub API usage\" under \"Configure System\" in the Jenkins settings.");
                    }
                    return expiration;
                }
            };
        }
    },

    /** Restrict GitHub API requests only when near or above rate limit. */
    ThrottleOnOver(Messages.ApiRateLimitChecker_ThrottleOnOver()) {
        @Override
        public LocalChecker getChecker(@NonNull TaskListener listener, String apiUrl) {
            return new LocalChecker(listener) {
                @Override
                long checkRateLimitImpl(@NonNull GHRateLimit.Record rateLimit, long count, long now)
                        throws InterruptedException {
                    // the buffer is how much we want to avoid using to cover unplanned over-use
                    int buffer = calculateBuffer(rateLimit.getLimit());
                    // check that we have at least our minimum buffer of remaining calls
                    if (rateLimit.getRemaining() >= buffer) {
                        return now;
                    }
                    writeLog(
                            "Jenkins is restricting GitHub API requests only when near or above the rate limit. "
                                    + "To configure a different rate limiting strategy, such as having Jenkins attempt to evenly distribute GitHub API requests, go to \"GitHub API usage\" under \"Configure System\" in the Jenkins settings.");
                    return calculateExpirationWhenBufferExceeded(rateLimit, now, buffer);
                }
            };
        }
    },
    /**
     * Ignore GitHub API Rate limit. Useful for GitHub Enterprise instances that might not have a
     * limit set up.
     */
    NoThrottle(Messages.ApiRateLimitChecker_NoThrottle()) {
        @Override
        public LocalChecker getChecker(@NonNull TaskListener listener, String apiUrl) {
            if (GitHubServerConfig.GITHUB_URL.equals(apiUrl)) {
                // If the GitHub public API is being used, this will fallback to ThrottleOnOver
                LocalChecker checker = ThrottleOnOver.getChecker(listener, apiUrl);
                checker.writeLog(
                        "GitHub throttling is disabled, which is not allowed for public GitHub usage, "
                                + "so ThrottleOnOver will be used instead. To configure a different rate limiting strategy, go to \"GitHub API usage\" under \"Configure System\" in the Jenkins settings.");
                return checker;
            } else {
                return new LocalChecker(listener) {
                    @Override
                    long checkRateLimitImpl(@NonNull GHRateLimit.Record rateLimit, long count, long now)
                            throws InterruptedException {
                        return now;
                    }
                };
            }
        }
    };

    /** Logger for printing output even when task listener is not set */
    private static final Logger LOGGER = Logger.getLogger(ApiRateLimitChecker.class.getName());

    /**
     * Thread-local rate limit checkers.
     *
     * <p>In Jenkins multiple threads can call into one {@link GitHub} instance with each wanting to
     * receive logging output on a different listener. {@link RateLimitChecker} does not support
     * anything like this. We use thread-local checker instances to track rate limit checking state
     * for each thread.
     */
    private static final ThreadLocal<LocalChecker> localRateLimitChecker = new ThreadLocal<>();

    private static final double MILLIS_PER_HOUR = TimeUnit.HOURS.toMillis(1);
    private static Random ENTROPY = new Random();
    private static int EXPIRATION_WAIT_MILLIS = 65536; // approx 1 min
    // A random straw poll of users concluded that 3 minutes without any visible progress in the logs
    // is the point after which people believe that the process is dead.
    private static long NOTIFICATION_WAIT_MILLIS = TimeUnit.MINUTES.toMillis(3);

    static void setEntropy(Random random) {
        ENTROPY = random;
    }

    static void setExpirationWaitMillis(int expirationWaitMillis) {
        EXPIRATION_WAIT_MILLIS = expirationWaitMillis;
    }

    static void setNotificationWaitMillis(int notificationWaitMillis) {
        NOTIFICATION_WAIT_MILLIS = notificationWaitMillis;
    }

    private String displayName;

    ApiRateLimitChecker(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static void configureThreadLocalChecker(@NonNull TaskListener listener, @NonNull GitHub gitHub) {
        configureThreadLocalChecker(listener, gitHub.getApiUrl());
    }

    private static void configureThreadLocalChecker(TaskListener listener, String apiUrl) {
        LocalChecker checker =
                GitHubConfiguration.get().getApiRateLimitChecker().getChecker(listener, apiUrl);
        localRateLimitChecker.set(checker);
    }

    /**
     * Verify a GitHub connection
     *
     * <p>WARNING: this call is not protected by rate limit checking. It is possible to exceed the
     * rate limit by calling this method.
     *
     * <p>This method should only be called from {@link Connector}. This works without any locking
     * because the checker is local to this thread.
     *
     * @param gitHub the GitHub connection to check for validity
     */
    static void verifyConnection(GitHub gitHub) throws IOException {
        Objects.requireNonNull(gitHub);
        LocalChecker checker = getLocalChecker();
        try {
            TaskListener listener = checker != null ? checker.listener : new LogTaskListener(LOGGER, Level.INFO);

            // Pass empty apiUrl to force no rate limit checking
            localRateLimitChecker.set(NoThrottle.getChecker(listener, ""));

            gitHub.checkApiUrlValidity();
        } finally {
            localRateLimitChecker.set(checker);
        }
    }

    /** For test purposes only. */
    static LocalChecker getLocalChecker() {
        return localRateLimitChecker.get();
    }

    /** For test purposes only. */
    static void resetLocalChecker() {
        localRateLimitChecker.set(null);
    }

    /**
     * This method is the old code path for rate limit checks
     *
     * <p>It has been slowly refactored until it almost matches the behavior of the
     * GitHubRateLimitChecker.
     *
     * @deprecated rate limit checking is done automatically. Use {@link
     *     #configureThreadLocalChecker(TaskListener, GitHub)} instead.
     */
    @Deprecated
    public void checkApiRateLimit(TaskListener listener, GitHub gitHub) throws IOException, InterruptedException {
        configureThreadLocalChecker(listener, gitHub);
    }

    static final class RateLimitCheckerAdapter extends RateLimitChecker {
        @Override
        protected boolean checkRateLimit(GHRateLimit.Record rateLimitRecord, long count) throws InterruptedException {
            LocalChecker checker = getLocalChecker();
            if (checker == null) {
                // If a checker was not configured for this thread, try our best by attempting to get the
                // URL from the first configured GitHub endpoint, else default to the public endpoint.
                // NOTE: Defaulting to the public GitHub endpoint is insufficient for those using GitHub
                // enterprise as it forces rate limit checking in those cases.
                String apiUrl = GitHubServerConfig.GITHUB_URL;
                List<Endpoint> endpoints = GitHubConfiguration.get().getEndpoints();
                if (endpoints.size() > 0
                        && !StringUtils.isBlank(endpoints.get(0).getApiUri())) {
                    apiUrl = endpoints.get(0).getApiUri();
                }
                configureThreadLocalChecker(new LogTaskListener(LOGGER, Level.INFO), apiUrl);
                checker = getLocalChecker();
                checker.writeLog("LocalChecker for rate limit was not set for this thread. "
                        + "Configured using system settings with API URL '"
                        + apiUrl
                        + "'.");
            }
            return checker.checkRateLimit(rateLimitRecord, count);
        }
    }

    abstract static class LocalChecker {
        @NonNull
        private final TaskListener listener;

        private long expiration;

        LocalChecker(@NonNull TaskListener listener) {
            this.listener = Objects.requireNonNull(listener);
            resetExpiration();
        }

        protected boolean checkRateLimit(GHRateLimit.Record rateLimitRecord, long count) throws InterruptedException {
            if (count == 0) {
                resetExpiration();
            }
            long now = System.currentTimeMillis();
            if (waitUntilRateLimit(now, expiration, count)) {
                return true;
            }
            long newExpiration = this.checkRateLimitImpl(rateLimitRecord, count, now);
            if (newExpiration > expiration) {
                count = 0;
            }
            return waitUntilRateLimit(now, newExpiration, count);
        }

        // internal for testing
        abstract long checkRateLimitImpl(@NonNull GHRateLimit.Record rateLimit, long count, long now)
                throws InterruptedException;

        void resetExpiration() {
            expiration = Long.MIN_VALUE;
        }

        long calculateExpirationWhenBufferExceeded(GHRateLimit.Record rateLimit, long now, int buffer) {
            long expiration;
            long rateLimitResetMillis = rateLimit.getResetDate().getTime() - now;
            // we add a little bit of random to prevent CPU overload when the limit is due to reset but
            // GitHub
            // hasn't actually reset yet (clock synchronization is a hard problem)
            if (rateLimitResetMillis < 0) {
                expiration = now + ENTROPY.nextInt(EXPIRATION_WAIT_MILLIS);
                writeLog(String.format(
                        "Jenkins-Imposed API Limiter: Current quota for Github API usage has %d remaining (%d over budget). Next quota of %d due now. Sleeping for %s.",
                        rateLimit.getRemaining(),
                        buffer - rateLimit.getRemaining(),
                        rateLimit.getLimit(),
                        // The GitHubRateLimitChecker adds a one second sleep to each notification loop
                        Util.getTimeSpanString(1000 + expiration - now)));
            } else {
                expiration = rateLimit.getResetDate().getTime() + ENTROPY.nextInt(EXPIRATION_WAIT_MILLIS);
                writeLog(String.format(
                        "Jenkins-Imposed API Limiter: Current quota for Github API usage has %d remaining (%d over budget). Next quota of %d in %s. Sleeping until reset.",
                        rateLimit.getRemaining(),
                        buffer - rateLimit.getRemaining(),
                        rateLimit.getLimit(),
                        Util.getTimeSpanString(rateLimitResetMillis)));
            }
            return expiration;
        }

        // Internal for testing
        boolean waitUntilRateLimit(long now, long expiration, long count) throws InterruptedException {
            boolean waiting = expiration > now;
            if (waiting) {
                long nextNotify = now + NOTIFICATION_WAIT_MILLIS;
                this.expiration = expiration;
                if (count > 0) {
                    writeLog(String.format(
                            "Jenkins-Imposed API Limiter: Still sleeping, now only %s remaining.",
                            Util.getTimeSpanString(expiration - now)));
                }
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
                long sleep = Math.min(expiration, nextNotify) - now;
                if (sleep > 0) {
                    Thread.sleep(sleep);
                }
            } else {
                resetExpiration();
            }
            return waiting;
        }

        void writeLog(String output) {
            listener.getLogger().println(GitHubConsoleNote.create(System.currentTimeMillis(), output));
        }
    }

    public abstract LocalChecker getChecker(@NonNull TaskListener listener, String apiUrl);

    static int calculateBuffer(int limit) {
        return Math.max(15, limit / 20);
    }

    static int calculateNormalizedBurst(int rateLimit) {
        return rateLimit < 1000 ? Math.max(5, rateLimit / 10) : Math.max(200, rateLimit / 5);
    }
}
