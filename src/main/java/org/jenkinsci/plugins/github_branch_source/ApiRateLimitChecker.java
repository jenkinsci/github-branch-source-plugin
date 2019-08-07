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
                } else if (rateLimit.remaining < ideal) {
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
                GHRateLimit rateLimit = github.rateLimit();
                // the buffer is how much we want to avoid using to cover unplanned over-use
                int buffer = Math.max(15, rateLimit.limit / 20);
                // check that we have at least our minimum buffer of remaining calls
                if (rateLimit.remaining >= buffer) {
                    break;
                }
                final long expiration = System.currentTimeMillis() + ENTROPY.nextInt(65536); // approx 1 min
                listener.getLogger().println(GitHubConsoleNote.create(System.currentTimeMillis(), String.format(
                        "GitHub API Usage: Current quota has %d remaining (%d over buffer). Next quota of %d due now. Sleeping for %s.",
                        rateLimit.remaining, buffer - rateLimit.remaining, rateLimit.limit,
                        Util.getTimeSpanString(expiration - System.currentTimeMillis())
                )));
                waitUntilRateLimit(listener, github, rateLimit, expiration);
            }
        }
    };

    private static final double MILLIS_PER_HOUR = TimeUnit.HOURS.toMillis(1);
    private static final Random ENTROPY = new Random();

    private String displayName;

    ApiRateLimitChecker(String displayName) {

        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public abstract void checkApiRateLimit(@NonNull TaskListener listener, GitHub github)
            throws IOException, InterruptedException;

    private static void waitUntilRateLimit(@NonNull TaskListener listener, GitHub github, GHRateLimit rateLimit, long expiration) throws InterruptedException, IOException {
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
