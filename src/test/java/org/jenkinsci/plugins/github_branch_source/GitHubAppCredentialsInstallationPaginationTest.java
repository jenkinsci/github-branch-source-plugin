package org.jenkinsci.plugins.github_branch_source;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

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
 * count: once a GitHub App has enough installations to span multiple pages, {@code
 * GitHubAppCredentials} still pages through the entire installation list before it ever looks at
 * the credential's already-known owner. On a real GitHub App with ~1,300 installations that
 * pagination eventually takes longer than the hardcoded 30s {@code Timeout.limit} in {@code
 * generateAppInstallationToken}, and token generation fails outright.
 *
 * <p>This test does not reproduce the 30s timeout directly (that would make the test itself slow
 * and timing-dependent). Instead it proves the underlying defect deterministically: the
 * credential's matching installation is on page one, so a correct implementation never needs to
 * request page two at all.
 */
public class GitHubAppCredentialsInstallationPaginationTest extends AbstractGitHubWireMockTest {

    @Before
    public void setUpWireMock() {
        GitHubConfiguration.get().setApiRateLimitChecker(ApiRateLimitChecker.ThrottleOnOver);

        githubApi.stubFor(get(urlEqualTo("/app"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBodyFile("../AppCredentials/files/body-mapping-githubapp-app.json")));

        // Page 1 already contains the installation that matches the credential's configured
        // owner ("cloudbeers"), but - exactly like the real GitHub API once an App has more
        // installations than fit on one page - still advertises a next page via the Link header.
        githubApi.stubFor(get(urlEqualTo("/app/installations"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withHeader("Link", "<" + githubApi.baseUrl() + "/app/installations?page=2>; rel=\"next\"")
                        .withBodyFile("../AppCredentials/files/body-mapping-githubapp-installations-page1-of-2.json")));

        // A second page that only exists because the App has more installations than fit on page
        // one. The credential already knows its owner matched on page one, so nothing here
        // should ever be needed to generate its token.
        githubApi.stubFor(get(urlEqualTo("/app/installations?page=2"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBodyFile("../AppCredentials/files/body-mapping-githubapp-installations-page2-of-2.json")));

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
    public void tokenGenerationShouldNotPageThroughInstallationsItDoesNotNeed() throws Exception {
        final var credentials = GitHubApp.createCredentials("myAppCredentialsKnownOwner");
        credentials.setRepositoryAccessStrategy(new AccessSpecifiedRepositories("cloudbeers", Collections.emptyList()));
        credentials.setApiUri(githubApi.baseUrl());

        // The credential already knows its owner is "cloudbeers", and that installation is on
        // the very first page - there is no reason to ever request page 2.
        credentials.getPassword();

        githubApi.verify(
                0, RequestPatternBuilder.newRequestPattern(RequestMethod.GET, urlEqualTo("/app/installations?page=2")));
    }

    private String createTokenExpiration() {
        // This token will go stale at the soonest allowed time but will not expire for the
        // duration of the test.
        return DateTimeFormatter.ISO_INSTANT.format(
                Instant.now().plus(Duration.ofMinutes(10)).truncatedTo(ChronoUnit.SECONDS));
    }
}
