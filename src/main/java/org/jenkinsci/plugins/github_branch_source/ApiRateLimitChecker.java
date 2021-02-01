package org.jenkinsci.plugins.github_branch_source;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.github.config.GitHubServerConfig;
import org.kohsuke.github.GHRateLimit;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.RateLimitChecker;

import java.io.IOException;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public enum ApiRateLimitChecker {

    /**
     * Attempt to evenly distribute GitHub API requests.
     */
    ThrottleForNormalize(Messages.ApiRateLimitChecker_ThrottleForNormalize()) {
        @Override
        public RateLimitChecker getChecker(String apiUrl) {
            return new RateLimitCheckerBase() {
                @Override
                boolean checkRateLimitImpl(@NonNull GHRateLimit.Record rateLimit, long count) throws InterruptedException {
                    long expiration = getExpiration();
                    long start = System.currentTimeMillis();
                    if (expiration > Long.MIN_VALUE) {
                        waitUntilRateLimit(start, expiration, count);
                        return true;
                    }


                    // the buffer is how much we want to avoid using to cover unplanned over-use
                    int buffer = calculateBuffer(rateLimit.getLimit());
                    // the burst is how much we want to allow for speedier response outside of the throttle
                    int burst = calculateNormalizedBurst(rateLimit.getLimit());
                    // the ideal is how much remaining we should have (after a burst)
                    long rateLimitResetMillis = rateLimit.getResetDate().getTime() - start;
                    double resetProgress = Math.max(0, rateLimitResetMillis / MILLIS_PER_HOUR);
                    int ideal = (int) ((rateLimit.getLimit() - buffer - burst) * resetProgress) + buffer;
                    if (rateLimit.getRemaining() >= ideal && rateLimit.getRemaining() < ideal + buffer) {
                        writeLog(String.format(
                            "Jenkins-Imposed API Limiter: Current quota for Github API usage has %d remaining (%d under budget). Next quota of %d in %s",
                            rateLimit.getRemaining(),
                            rateLimit.getRemaining() - ideal,
                            rateLimit.getLimit(),
                            Util.getTimeSpanString(rateLimitResetMillis)
                        ));
                    } else if (rateLimit.getRemaining() < ideal) {
                        if (rateLimit.getRemaining() < buffer) {
                            // nothing we can do, we have burned into our buffer, wait for reset
                            // we add a little bit of random to prevent CPU overload when the limit is due to reset but GitHub
                            // hasn't actually reset yet (clock synchronization is a hard problem)
                            if (rateLimitResetMillis < 0) {
                                expiration = start + ENTROPY.nextInt(
                                    EXPIRATION_WAIT_MILLIS);
                                writeLog(String.format(
                                            "Jenkins-Imposed API Limiter: Current quota for Github API usage has %d remaining (%d over budget). Next quota of %d due now. Sleeping for %s.",
                                            rateLimit.getRemaining(),
                                            ideal - rateLimit.getRemaining(),
                                            rateLimit.getLimit(),
                                            Util.getTimeSpanString(expiration - start)));

                            } else {
                                expiration = rateLimit.getResetDate().getTime() + ENTROPY.nextInt(
                                    EXPIRATION_WAIT_MILLIS);
                                writeLog(String.format(
                                            "Jenkins-Imposed API Limiter: Current quota for Github API usage has %d remaining (%d over budget). Next quota of %d in %s. Sleeping until reset.",
                                            rateLimit.getRemaining(),
                                            ideal - rateLimit.getRemaining(),
                                            rateLimit.getLimit(),
                                            Util.getTimeSpanString(rateLimitResetMillis)));
                            }
                        } else {
                            // work out how long until remaining == ideal + 0.1 * buffer (to give some spend)
                            double targetFraction = (rateLimit.getRemaining() - buffer * 1.1) / (rateLimit
                                .getLimit() - buffer - burst);
                            expiration = rateLimit.getResetDate().getTime()
                                - Math.max(0, (long) (targetFraction * MILLIS_PER_HOUR))
                                + ENTROPY.nextInt(1000);
                            writeLog(String.format(
                                        "Jenkins-Imposed API Limiter: Current quota for Github API usage has %d remaining (%d over budget). Next quota of %d in %s. Sleeping for %s.",
                                        rateLimit.getRemaining(),
                                        ideal - rateLimit.getRemaining(),
                                        rateLimit.getLimit(),
                                        Util.getTimeSpanString(rateLimitResetMillis),
                                        Util.getTimeSpanString(expiration - start)));
                        }
                        writeLog("Jenkins is attempting to evenly distribute GitHub API requests. To configure a different rate limiting strategy, such as having Jenkins restrict GitHub API requests only when near or above the GitHub rate limit, go to \"GitHub API usage\" under \"Configure System\" in the Jenkins settings.");
                        setExpiration(expiration);
                        waitUntilRateLimit(start, expiration, count);
                        return true;
                    }
                    return false;
                }
            };
        }
    },

    /**å
     * Restrict GitHub API requests only when near or above rate limit.
     */
    ThrottleOnOver(Messages.ApiRateLimitChecker_ThrottleOnOver()) {
        @Override
        public RateLimitChecker getChecker(String apiUrl) {
            return new RateLimitCheckerBase() {
                @Override
                boolean checkRateLimitImpl(@NonNull GHRateLimit.Record rateLimit, long count) throws InterruptedException {
                    long expiration = getExpiration();
                    long start = System.currentTimeMillis();
                    if (expiration > Long.MIN_VALUE) {
                        waitUntilRateLimit(start, expiration, count);
                        return true;
                    }


                    // the buffer is how much we want to avoid using to cover unplanned over-use
                    int buffer = calculateBuffer(rateLimit.getLimit());
                    // check that we have at least our minimum buffer of remaining calls
                    if (rateLimit.getRemaining() >= buffer) {
                        return false;
                    }
                    expiration = rateLimit.getResetDate().getTime() + ENTROPY.nextInt(
                        EXPIRATION_WAIT_MILLIS);
                    writeLog(String.format(
                            "Jenkins-Imposed API Limiter: Current quota for Github API usage has %d remaining (%d over buffer). Next quota of %d due in %s. Sleeping for %s.",
                            rateLimit.getRemaining(),
                            buffer - rateLimit.getRemaining(),
                            rateLimit.getLimit(),
                            Util.getTimeSpanString(expiration - start),
                            Util.getTimeSpanString(NOTIFICATION_WAIT_MILLIS)
                        ));
                    writeLog("Jenkins is restricting GitHub API requests only when near or above the rate limit. To configure a different rate limiting strategy, such as having Jenkins attempt to evenly distribute GitHub API requests, go to \"GitHub API usage\" under \"Configure System\" in the Jenkins settings.");
                    setExpiration(expiration);
                    waitUntilRateLimit(start, expiration, count);
                    return true;
                }
            };
        }
    },
    /**
     * Ignore GitHub API Rate limit. Useful for GitHub Enterprise instances that might not have a limit set up.
     */
    NoThrottle(Messages.ApiRateLimitChecker_NoThrottle()) {
        @Override
        public RateLimitChecker getChecker(String apiUrl) {
            if (GitHubServerConfig.GITHUB_URL.equals(apiUrl)) {
                // If the GitHub public API is being used, this will fallback to ThrottleOnOver
                writeLog("GitHub throttling is disabled, which is not allowed for public GitHub usage, so ThrottleOnOver will be used instead. To configure a different rate limiting strategy, go to \"GitHub API usage\" under \"Configure System\" in the Jenkins settings.");
                return ThrottleOnOver.getChecker(apiUrl);
            } else {
                return  RateLimitChecker.NONE;
            }
        }
    };

    /**
     * Logger for printing output even when task listener is not set
     */
    private static final Logger LOGGER = Logger.getLogger(ApiRateLimitChecker.class.getName());

    /**
     * Thread-local task listeners.
     *
     * In Jenkins multiple threads can call into one {@link GitHub} instance with each wanting to
     * receive logging output on a different listener. {@link RateLimitChecker} does not support
     * anything like this.  So, store it here.
     */
    private static final ThreadLocal<TaskListener> taskListenerThreadLocal = new ThreadLocal<>();

    /**
     * Thread local {@link Long} expiration time.
     *
     * This saves us from recalculating the expiration after each notification loop.
     */
    private static final ThreadLocal<Long> expirationThreadLocal = ThreadLocal.withInitial(() -> Long.MIN_VALUE);

    static void setExpiration(long expiration) {
        expirationThreadLocal.set(expiration);
    }

    static long getExpiration() {
        return expirationThreadLocal.get();
    }



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

    public void checkApiRateLimit(TaskListener listener, GitHub gitHub) throws IOException, InterruptedException {
        setExpiration(Long.MIN_VALUE);
        long count = 0;
        while (checkApiRateLimitOnce(listener, gitHub, count++)) {
            // continue
        }
    }

    /**
     * For testing only
     */
    boolean checkApiRateLimitOnce(TaskListener listener, GitHub gitHub, long count) throws IOException, InterruptedException {
        taskListenerThreadLocal.set(listener);

        RateLimitChecker currentChecker = getChecker(gitHub.getApiUrl());

        if (currentChecker instanceof RateLimitCheckerBase) {
            GHRateLimit rateLimit = gitHub.getRateLimit();
            return ((RateLimitCheckerBase)currentChecker).checkRateLimit(rateLimit.getCore(), count++);
        }
        return false;
    }

    static abstract class RateLimitCheckerBase extends RateLimitChecker {
        @Override
        protected boolean checkRateLimit(GHRateLimit.Record rateLimitRecord,
                                         long count) throws InterruptedException {
            return this.checkRateLimitImpl(rateLimitRecord, count);
        }

        // internal for testing
        abstract boolean checkRateLimitImpl(@NonNull GHRateLimit.Record rateLimit, long count) throws InterruptedException;

        // Internal for testing
        void waitUntilRateLimit(long now, long expiration, long count) throws InterruptedException {
            long nextNotify = now + NOTIFICATION_WAIT_MILLIS;
            if (expiration > now) {
                if (count > 0) {
                    writeLog(String.format(
                        "Jenkins-Imposed API Limiter: Still sleeping, now only %s remaining.",
                        Util.getTimeSpanString(expiration - now)
                    ));
                }
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
                long sleep = Math.min(expiration, nextNotify) - now;
                if (sleep > 0) {
                    Thread.sleep(sleep);
                }
                now = System.currentTimeMillis();
                if (expiration <= now) {
                    setExpiration(Long.MIN_VALUE);
                }
            } else {
                setExpiration(Long.MIN_VALUE);
            }
        }
    }

    public abstract RateLimitChecker getChecker(String apiUrl);

    static int calculateBuffer(int limit) {
        return Math.max(15, limit / 20);
    }

    static int calculateNormalizedBurst(int rateLimit) {
        return rateLimit < 1000 ? Math.max(5, rateLimit / 10) : Math.max(200, rateLimit / 5);
    }

    private static void writeLog(String output) {
        TaskListener listener = taskListenerThreadLocal.get();
        if (listener != null) {
            listener.getLogger()
                .println(GitHubConsoleNote.create(System.currentTimeMillis(), output));
        } else {
            LOGGER.info(output);
        }
    }
}
