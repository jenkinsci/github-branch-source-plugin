package org.jenkinsci.plugins.github_branch_source;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import org.jenkinsci.plugins.github_branch_source.app_credentials.AccessSpecifiedRepositories;
import org.junit.Before;
import org.junit.Test;

/**
 * Regression coverage for a case reported against a GitHub App with a very large installation
 * count: once a GitHub App has enough installations, {@code GitHubAppCredentials} still paged
 * through its entire installation list before it ever looked at the credential's already-known
 * (or inferable) owner. On a real GitHub App with ~1,300 installations that pagination eventually
 * took longer than the hardcoded 30s {@code Timeout.limit} in {@code
 * generateAppInstallationToken}, and token generation failed outright.
 *
 * <p>This test does not reproduce the 30s timeout directly (that would make the test itself slow
 * and timing-dependent). Instead it proves the fix directly: once the owner is known (explicitly
 * configured, or inferred from context), the fix resolves the installation with GitHub's
 * single-call per-owner lookup ({@code GET /orgs/{owner}/installation}) and never lists
 * installations at all.
 */
public class GitHubAppCredentialsInstallationPaginationTest extends AbstractGitHubWireMockTest {

    @Before
    public void setUpWireMock() {
        GitHubConfiguration.get().setApiRateLimitChecker(ApiRateLimitChecker.ThrottleOnOver);

        githubApi.stubFor(get(urlEqualTo("/app"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBodyFile("../AppCredentials/files/body-mapping-githubapp-app.json")));

        // The credential's owner ("cloudbeers") is resolved directly via GitHub's single-call,
        // non-paginated per-owner lookup. Deliberately NOT stubbing /app/installations at all:
        // if the fix regresses to listing installations again, the test fails outright rather
        // than silently falling back to a (still-correct but slow) full listing.
        githubApi.stubFor(get(urlEqualTo("/orgs/cloudbeers/installation"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBodyFile("../AppCredentials/files/body-mapping-githubapp-installation-cloudbeers.json")));

        githubApi.stubFor(post(urlEqualTo("/app/installations/654321/access_tokens"))
                .withRequestBody(equalToJson(
                        "{\"permissions\":{\"pull_requests\":\"write\",\"metadata\":\"read\",\"checks\":\"write\",\"contents\":\"read\"}}",
                        true,
                        false))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBody("{\"token\":\"super-secret-token\",\"expires_at\":\"" + createTokenExpiration()
                                + "\"}")));
    }

    @Test
    public void tokenGenerationShouldNotListInstallationsForAKnownOwner() throws Exception {
        final var credentials = GitHubApp.createCredentials("myAppCredentialsKnownOwner");
        credentials.setRepositoryAccessStrategy(new AccessSpecifiedRepositories("cloudbeers", Collections.emptyList()));
        credentials.setApiUri(githubApi.baseUrl());

        // The credential already knows its owner is "cloudbeers" - there is no reason to ever
        // list installations to find it.
        credentials.getPassword();

        githubApi.verify(
                0, RequestPatternBuilder.newRequestPattern(RequestMethod.GET, urlMatching("/app/installations.*")));
    }

    @Test
    public void tokenGenerationShouldNotListInstallationsForAnInferredOwner() throws Exception {
        // No owner configured on the credential at all (the common real-world shape for a single
        // App credential shared across many orgs) - the owner only becomes known from context,
        // the same way it would from the repository actually being built/scanned.
        final var credentials = GitHubApp.createCredentials("myAppCredentialsInferredOwner");
        credentials.setRepositoryAccessStrategy(new AccessSpecifiedRepositories(null, Collections.emptyList()));
        credentials.setApiUri(githubApi.baseUrl());

        final var usageContext = GitHubAppUsageContext.builder()
                .inferredOwner("cloudbeers")
                .trust()
                .build();
        final var contextualized = credentials.contextualize(usageContext);

        contextualized.getPassword();

        githubApi.verify(
                0, RequestPatternBuilder.newRequestPattern(RequestMethod.GET, urlMatching("/app/installations.*")));
    }

    @Test
    public void tokenGenerationShouldFallBackToUserLookupWhenOwnerIsNotAnOrganization() throws Exception {
        // The owner isn't an organization, so the org lookup 404s first; the fix then tries the
        // user lookup instead of falling back to listing installations.
        githubApi.stubFor(get(urlEqualTo("/orgs/a-user-owner/installation"))
                .willReturn(aResponse().withStatus(404)));
        githubApi.stubFor(get(urlEqualTo("/users/a-user-owner/installation"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBodyFile(
                                "../AppCredentials/files/body-mapping-githubapp-installation-a-user-owner.json")));
        githubApi.stubFor(post(urlEqualTo("/app/installations/987654/access_tokens"))
                .withRequestBody(equalToJson(
                        "{\"permissions\":{\"pull_requests\":\"write\",\"metadata\":\"read\",\"checks\":\"write\",\"contents\":\"read\"}}",
                        true,
                        false))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBody("{\"token\":\"super-secret-token\",\"expires_at\":\"" + createTokenExpiration()
                                + "\"}")));

        final var credentials = GitHubApp.createCredentials("myAppCredentialsUserOwner");
        credentials.setRepositoryAccessStrategy(
                new AccessSpecifiedRepositories("a-user-owner", Collections.emptyList()));
        credentials.setApiUri(githubApi.baseUrl());

        credentials.getPassword();

        githubApi.verify(
                0, RequestPatternBuilder.newRequestPattern(RequestMethod.GET, urlMatching("/app/installations.*")));
    }

    private String createTokenExpiration() {
        // This token will go stale at the soonest allowed time but will not expire for the
        // duration of the test.
        return DateTimeFormatter.ISO_INSTANT.format(
                Instant.now().plus(Duration.ofMinutes(10)).truncatedTo(ChronoUnit.SECONDS));
    }
}
