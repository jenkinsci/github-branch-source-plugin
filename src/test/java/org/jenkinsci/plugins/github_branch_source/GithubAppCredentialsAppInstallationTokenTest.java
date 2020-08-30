package org.jenkinsci.plugins.github_branch_source;

import org.junit.Test;

import java.time.Duration;
import java.time.Instant;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class GithubAppCredentialsAppInstallationTokenTest {

    @Test
    public void testAppInstallationTokenStale() throws Exception {

        GitHubAppCredentials.AppInstallationToken token;
        long now;

        now = Instant.now().getEpochSecond();
        token = new GitHubAppCredentials.AppInstallationToken("", now);
        assertThat(token.isStale(), is(false));
        assertThat(token.getTokenStaleEpochSeconds(), equalTo(now + GitHubAppCredentials.AppInstallationToken.NOT_STALE_FOR_ATLEAST_SECONDS));

        now = Instant.now().getEpochSecond();
        token = new GitHubAppCredentials.AppInstallationToken("",
            now + Duration.ofMinutes(15).getSeconds());
        assertThat(token.isStale(), is(false));
        assertThat(token.getTokenStaleEpochSeconds(), equalTo(now + GitHubAppCredentials.AppInstallationToken.NOT_STALE_FOR_ATLEAST_SECONDS));

        now = Instant.now().getEpochSecond();
        token = new GitHubAppCredentials.AppInstallationToken("",
            now + GitHubAppCredentials.AppInstallationToken.STALE_WHEN_SECONDS_UNTIL_EXPIRATION + 2);
        assertThat(token.isStale(), is(false));
        assertThat(token.getTokenStaleEpochSeconds(), equalTo(now + GitHubAppCredentials.AppInstallationToken.NOT_STALE_FOR_ATLEAST_SECONDS));

        now = Instant.now().getEpochSecond();
        token = new GitHubAppCredentials.AppInstallationToken("",
            now + GitHubAppCredentials.AppInstallationToken.STALE_WHEN_SECONDS_UNTIL_EXPIRATION + Duration
                .ofMinutes(7)
                .getSeconds());
        assertThat(token.isStale(), is(false));
        assertThat(token.getTokenStaleEpochSeconds(),
            equalTo(now + Duration.ofMinutes(7).getSeconds()));

        now = Instant.now().getEpochSecond();
        token = new GitHubAppCredentials.AppInstallationToken("",
            now + Duration.ofMinutes(90).getSeconds());
        assertThat(token.isStale(), is(false));
        assertThat(token.getTokenStaleEpochSeconds(),
            equalTo(now + GitHubAppCredentials.AppInstallationToken.STALE_AFTER_SECONDS + 1));

        long notStaleSeconds = GitHubAppCredentials.AppInstallationToken.NOT_STALE_FOR_ATLEAST_SECONDS;
        try {
            // Should revert to 1 second minimum
            GitHubAppCredentials.AppInstallationToken.NOT_STALE_FOR_ATLEAST_SECONDS = -10;

            now = Instant.now().getEpochSecond();
            token = new GitHubAppCredentials.AppInstallationToken("", now);
            assertThat(token.isStale(), is(false));
            assertThat(token.getTokenStaleEpochSeconds(), equalTo(now + 1));

            // Verify goes stale
            Thread.sleep(1000);
            assertThat(token.isStale(), is(true));
        } finally {
            GitHubAppCredentials.AppInstallationToken.NOT_STALE_FOR_ATLEAST_SECONDS = notStaleSeconds;
        }
    }
}