package org.jenkinsci.plugins.github_branch_source;

import org.junit.Test;

import java.time.Duration;
import java.time.Instant;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class GitHubAppCredentialsTest {

    @Test
    public void testAppInstallationTokenStale() throws Exception {

        GitHubAppCredentials.AppInstallationToken token;
        long now;

        now = Instant.now().getEpochSecond();
        token = new GitHubAppCredentials.AppInstallationToken("", now);
        assertThat(token.isStale(), is(false));
        assertThat(token.getTokenStaleEpochSeconds(), equalTo(now + 1));

        Thread.sleep(1000);
        assertThat(token.isStale(), is(true));

        now = Instant.now().getEpochSecond();
        token = new GitHubAppCredentials.AppInstallationToken("",
            now + Duration.ofMinutes(15).getSeconds());
        assertThat(token.isStale(), is(false));
        assertThat(token.getTokenStaleEpochSeconds(), equalTo(now + 1));

        now = Instant.now().getEpochSecond();
        token = new GitHubAppCredentials.AppInstallationToken("",
            now + GitHubAppCredentials.AppInstallationToken.MINIMUM_SECONDS_UNTIL_EXPIRATION + 2);
        assertThat(token.isStale(), is(false));
        assertThat(token.getTokenStaleEpochSeconds(), equalTo(now + 2));

        now = Instant.now().getEpochSecond();
        token = new GitHubAppCredentials.AppInstallationToken("",
            now + GitHubAppCredentials.AppInstallationToken.MINIMUM_SECONDS_UNTIL_EXPIRATION + Duration.ofMinutes(7).getSeconds());
        assertThat(token.isStale(), is(false));
        assertThat(token.getTokenStaleEpochSeconds(), equalTo(now + Duration.ofMinutes(7).getSeconds()));

        now = Instant.now().getEpochSecond();
        token = new GitHubAppCredentials.AppInstallationToken("",
            now + Duration.ofMinutes(90).getSeconds());
        assertThat(token.isStale(), is(false));
        assertThat(token.getTokenStaleEpochSeconds(), equalTo(now + GitHubAppCredentials.AppInstallationToken.MAXIMUM_AGE_SECONDS + 1));
    }
}
