package org.jenkinsci.plugins.github_branch_source;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import hudson.util.Secret;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.hamcrest.number.BigDecimalCloseTo;
import org.junit.Test;

public class GithubAppCredentialsAppInstallationTokenTest {

    @Test
    public void testAppInstallationTokenStale() throws Exception {

        GitHubAppCredentials.AppInstallationToken token;
        long now;

        now = Instant.now().getEpochSecond();
        Secret secret = Secret.fromString("secret-token");
        token = new GitHubAppCredentials.AppInstallationToken(secret, now);
        assertThat(token.isStale(), is(false));
        assertThat(
                token.getTokenStaleEpochSeconds(),
                closeTo(now + GitHubAppCredentials.AppInstallationToken.NOT_STALE_MINIMUM_SECONDS, 3));

        now = Instant.now().getEpochSecond();
        token = new GitHubAppCredentials.AppInstallationToken(
                secret, now + Duration.ofMinutes(15).getSeconds());
        assertThat(token.isStale(), is(false));
        assertThat(
                token.getTokenStaleEpochSeconds(),
                closeTo(now + GitHubAppCredentials.AppInstallationToken.NOT_STALE_MINIMUM_SECONDS, 3));

        now = Instant.now().getEpochSecond();
        token = new GitHubAppCredentials.AppInstallationToken(
                secret, now + GitHubAppCredentials.AppInstallationToken.STALE_BEFORE_EXPIRATION_SECONDS + 2);
        assertThat(token.isStale(), is(false));
        assertThat(
                token.getTokenStaleEpochSeconds(),
                closeTo(now + GitHubAppCredentials.AppInstallationToken.NOT_STALE_MINIMUM_SECONDS, 3));

        now = Instant.now().getEpochSecond();
        token = new GitHubAppCredentials.AppInstallationToken(
                secret,
                now
                        + GitHubAppCredentials.AppInstallationToken.STALE_BEFORE_EXPIRATION_SECONDS
                        + Duration.ofMinutes(7).getSeconds());
        assertThat(token.isStale(), is(false));
        assertThat(
                token.getTokenStaleEpochSeconds(),
                closeTo(now + Duration.ofMinutes(7).getSeconds(), 3));

        now = Instant.now().getEpochSecond();
        token = new GitHubAppCredentials.AppInstallationToken(
                secret, now + Duration.ofMinutes(90).getSeconds());
        assertThat(token.isStale(), is(false));
        assertThat(
                token.getTokenStaleEpochSeconds(),
                closeTo(now + GitHubAppCredentials.AppInstallationToken.STALE_AFTER_SECONDS + 1, 3));

        // TODO use FlagRule
        long notStaleSeconds = GitHubAppCredentials.AppInstallationToken.NOT_STALE_MINIMUM_SECONDS;
        try {
            // Should revert to 1 second minimum
            GitHubAppCredentials.AppInstallationToken.NOT_STALE_MINIMUM_SECONDS = -10;

            now = Instant.now().getEpochSecond();
            token = new GitHubAppCredentials.AppInstallationToken(secret, now);
            assertThat(token.isStale(), is(false));
            assertThat(token.getTokenStaleEpochSeconds(), closeTo(now + 1, 3));

            // Verify goes stale
            Thread.sleep(1000);
            assertThat(token.isStale(), is(true));
        } finally {
            GitHubAppCredentials.AppInstallationToken.NOT_STALE_MINIMUM_SECONDS = notStaleSeconds;
        }
    }

    private static Matcher<Long> closeTo(long operand, long error) {
        BigDecimalCloseTo delegate = new BigDecimalCloseTo(new BigDecimal(operand), new BigDecimal(error));
        return new TypeSafeMatcher<Long>(Long.class) {
            @Override
            protected boolean matchesSafely(Long item) {
                return delegate.matches(new BigDecimal(item));
            }

            @Override
            protected void describeMismatchSafely(Long item, Description mismatchDescription) {
                delegate.describeMismatchSafely(new BigDecimal(item), mismatchDescription);
            }

            @Override
            public void describeTo(Description description) {
                delegate.describeTo(description);
            }
        };
    }
}
