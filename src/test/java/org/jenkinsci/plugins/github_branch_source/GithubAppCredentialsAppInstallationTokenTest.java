package org.jenkinsci.plugins.github_branch_source;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import hudson.util.Secret;
import java.time.Duration;
import java.time.Instant;
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
        equalTo(now + GitHubAppCredentials.AppInstallationToken.NOT_STALE_MINIMUM_SECONDS));

    now = Instant.now().getEpochSecond();
    token =
        new GitHubAppCredentials.AppInstallationToken(
            secret, now + Duration.ofMinutes(15).getSeconds());
    assertThat(token.isStale(), is(false));
    assertThat(
        token.getTokenStaleEpochSeconds(),
        equalTo(now + GitHubAppCredentials.AppInstallationToken.NOT_STALE_MINIMUM_SECONDS));

    now = Instant.now().getEpochSecond();
    token =
        new GitHubAppCredentials.AppInstallationToken(
            secret,
            now + GitHubAppCredentials.AppInstallationToken.STALE_BEFORE_EXPIRATION_SECONDS + 2);
    assertThat(token.isStale(), is(false));
    assertThat(
        token.getTokenStaleEpochSeconds(),
        equalTo(now + GitHubAppCredentials.AppInstallationToken.NOT_STALE_MINIMUM_SECONDS));

    now = Instant.now().getEpochSecond();
    token =
        new GitHubAppCredentials.AppInstallationToken(
            secret,
            now
                + GitHubAppCredentials.AppInstallationToken.STALE_BEFORE_EXPIRATION_SECONDS
                + Duration.ofMinutes(7).getSeconds());
    assertThat(token.isStale(), is(false));
    assertThat(
        token.getTokenStaleEpochSeconds(), equalTo(now + Duration.ofMinutes(7).getSeconds()));

    now = Instant.now().getEpochSecond();
    token =
        new GitHubAppCredentials.AppInstallationToken(
            secret, now + Duration.ofMinutes(90).getSeconds());
    assertThat(token.isStale(), is(false));
    assertThat(
        token.getTokenStaleEpochSeconds(),
        equalTo(now + GitHubAppCredentials.AppInstallationToken.STALE_AFTER_SECONDS + 1));

    long notStaleSeconds = GitHubAppCredentials.AppInstallationToken.NOT_STALE_MINIMUM_SECONDS;
    try {
      // Should revert to 1 second minimum
      GitHubAppCredentials.AppInstallationToken.NOT_STALE_MINIMUM_SECONDS = -10;

      now = Instant.now().getEpochSecond();
      token = new GitHubAppCredentials.AppInstallationToken(secret, now);
      assertThat(token.isStale(), is(false));
      assertThat(token.getTokenStaleEpochSeconds(), equalTo(now + 1));

      // Verify goes stale
      Thread.sleep(1000);
      assertThat(token.isStale(), is(true));
    } finally {
      GitHubAppCredentials.AppInstallationToken.NOT_STALE_MINIMUM_SECONDS = notStaleSeconds;
    }
  }
}
