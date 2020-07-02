package org.jenkinsci.plugins.github_branch_source;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import hudson.model.TaskListener;
import org.kohsuke.github.GHRateLimit;
import org.kohsuke.github.GitHub;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public enum ApiRateLimitChecker {
    /**
     * Attempt to evenly distribute GitHub API requests.
     */
    ThrottleForNormalize(Messages.ApiRateLimitChecker_ThrottleForNormalize()) {
        @Override
        public void checkApiRateLimit(@NonNull TaskListener listener, GitHub github) throws IOException, InterruptedException {
            boolean check = true;
            while (check) {
                check = false;
                long start = System.currentTimeMillis();
                GHRateLimit rateLimit = github.getRateLimit();
                // the buffer is how much we want to avoid using to cover unplanned over-use
                int buffer = calculateBuffer(rateLimit.limit);
                // the burst is how much we want to allow for speedier response outside of the throttle
                int burst = calculateNormalizedBurst(rateLimit.limit);
                // the ideal is how much remaining we should have (after a burst)
                long rateLimitResetMillis = rateLimit.getResetDate().getTime() - start;
                double resetProgress = Math.max(0, rateLimitResetMillis / MILLIS_PER_HOUR);
                int ideal = (int) ((rateLimit.limit - buffer - burst) * resetProgress) + buffer;
                if (rateLimit.remaining >= ideal && rateLimit.remaining < ideal + buffer) {
                    listener.getLogger().println(GitHubConsoleNote.create(start, String.format(
                            "Jenkins-Imposed API Limiter: Current quota for Github API usage has %d remaining (%d under budget). Next quota of %d in %s",
                            rateLimit.remaining, rateLimit.remaining - ideal, rateLimit.limit,
                            Util.getTimeSpanString(rateLimitResetMillis)
                    )));
                } else if (rateLimit.remaining < ideal) {
                    check = true;
                    final long expiration;
                    if (rateLimit.remaining < buffer) {
                        // nothing we can do, we have burned into our buffer, wait for reset
                        // we add a little bit of random to prevent CPU overload when the limit is due to reset but GitHub
                        // hasn't actually reset yet (clock synchronization is a hard problem)
                        if (rateLimitResetMillis < 0) {
                            expiration = System.currentTimeMillis() + ENTROPY.nextInt(EXPIRATION_WAIT_MILLIS);
                            listener.getLogger().println(GitHubConsoleNote.create(System.currentTimeMillis(), String.format(
                                    "Jenkins-Imposed API Limiter: Current quota for Github API usage has %d remaining (%d over budget). Next quota of %d due now. Sleeping for %s.",
                                    rateLimit.remaining, ideal - rateLimit.remaining, rateLimit.limit,
                                    Util.getTimeSpanString(expiration - System.currentTimeMillis())
                            )));
                        } else {
                            expiration = rateLimit.getResetDate().getTime() + ENTROPY.nextInt(EXPIRATION_WAIT_MILLIS);
                            listener.getLogger().println(GitHubConsoleNote.create(System.currentTimeMillis(), String.format(
                                    "Jenkins-Imposed API Limiter: Current quota for Github API usage has %d remaining (%d over budget). Next quota of %d in %s. Sleeping until reset.",
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
                                "Jenkins-Imposed API Limiter: Current quota for Github API usage has %d remaining (%d over budget). Next quota of %d in %s. Sleeping for %s.",
                                rateLimit.remaining, ideal - rateLimit.remaining, rateLimit.limit,
                                Util.getTimeSpanString(rateLimitResetMillis),
                                Util.getTimeSpanString(expiration - System.currentTimeMillis())
                        )));
                    }
                    listener.getLogger().println(GitHubConsoleNote.create(System.currentTimeMillis(),
                            "Jenkins is attempting to evenly distribute GitHub API requests. To configure a different rate limiting strategy, such as having Jenkins restrict GitHub API requests only when near or above the GitHub rate limit, go to \"GitHub API usage\" under \"Configure System\" in the Jenkins settings."));
                    waitUntilRateLimit(listener, github, rateLimit, expiration);
                }
            }
        }
    },

    /**
     * Restrict GitHub API requests only when near or above rate limit.
     */
    ThrottleOnOver(Messages.ApiRateLimitChecker_ThrottleOnOver()) {
        @Override
        public void checkApiRateLimit(@NonNull TaskListener listener, GitHub github) throws IOException, InterruptedException {
            boolean check = true;
            while (check) {
                GHRateLimit rateLimit = github.getRateLimit();
                // the buffer is how much we want to avoid using to cover unplanned over-use
                int buffer = calculateBuffer(rateLimit.limit);
                // check that we have at least our minimum buffer of remaining calls
                if (rateLimit.remaining >= buffer) {
                    break;
                }
                final long expiration = rateLimit.getResetDate().getTime() + ENTROPY.nextInt(EXPIRATION_WAIT_MILLIS);
                listener.getLogger().println(GitHubConsoleNote.create(System.currentTimeMillis(), String.format(
                        "Jenkins-Imposed API Limiter: Current quota for Github API usage has %d remaining (%d over buffer). Next quota of %d due in %s. Sleeping for %s.",
                        rateLimit.remaining, buffer - rateLimit.remaining, rateLimit.limit,
                        Util.getTimeSpanString(expiration - System.currentTimeMillis()),
                        Util.getTimeSpanString(NOTIFICATION_WAIT_MILLIS)

                )));
                listener.getLogger().println(GitHubConsoleNote.create(System.currentTimeMillis(),
                        "Jenkins is restricting GitHub API requests only when near or above the rate limit. To configure a different rate limiting strategy, such as having Jenkins attempt to evenly distribute GitHub API requests, go to \"GitHub API usage\" under \"Configure System\" in the Jenkins settings."));
                waitUntilRateLimit(listener, github, rateLimit, expiration);
            }
        }
    };


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

    public abstract void checkApiRateLimit(@NonNull TaskListener listener, GitHub github)
            throws IOException, InterruptedException;

    static int calculateBuffer(int limit) {
        return Math.max(15, limit / 20);
    }

    static int calculateNormalizedBurst(int rateLimit) {
        return rateLimit < 1000 ? Math.max(5, rateLimit / 10) : Math.max(200, rateLimit / 5);
    }

    private static void waitUntilRateLimit(@NonNull TaskListener listener, GitHub github, GHRateLimit rateLimit, long expiration) throws InterruptedException, IOException {
        long nextNotify = System.currentTimeMillis() + NOTIFICATION_WAIT_MILLIS;
        while (expiration > System.currentTimeMillis()) {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            long sleep = Math.min(expiration, nextNotify) - System.currentTimeMillis();
            if (sleep > 0) {
                Thread.sleep(sleep);
            }

            nextNotify += NOTIFICATION_WAIT_MILLIS;
            long now = System.currentTimeMillis();
            if (now < expiration) {
                GHRateLimit current = github.getRateLimit();
                if (current.remaining > rateLimit.remaining
                        || current.getResetDate().getTime() > rateLimit.getResetDate().getTime()) {
                    listener.getLogger().println(GitHubConsoleNote.create(now,
                            "Jenkins-Imposed API Limiter: The Github API usage quota may have been refreshed earlier than expected, rechecking..."
                    ));
                    break;
                }
                listener.getLogger().println(GitHubConsoleNote.create(now, String.format(
                        "Jenkins-Imposed API Limiter: Still sleeping, now only %s remaining.",
                        Util.getTimeSpanString(expiration - now)
                )));
            }
        }
    }
}
