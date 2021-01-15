package org.jenkinsci.plugins.github_branch_source.authorization;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.kohsuke.github.GHAppInstallation;
import org.kohsuke.github.GHAppInstallationToken;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.authorization.AuthorizationProvider;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import static org.jenkinsci.plugins.github_branch_source.GitHubAppCredentials.ERROR_NOT_INSTALLED;

public class GitHubBranchSourceAuthorizationProvider extends GitHub.DependentAuthorizationProvider {

    @CheckForNull
    private final String owner;
    private final String applicationId;

    private String latestToken;

    @NonNull
    private Instant validUntil = Instant.MIN;

    /**
     * Provides an AuthorizationProvider that performs automatic token refresh, based on an previously authenticated
     * github client.
     *
     * @param owner
     *            The name of the organization or user where the application is installed
     * @param applicationId
     *            The application ID of the GitHub App
     * @param authorizationProvider
     *            A authorization provider that returns a JWT token that can be used to refresh the App Installation
     *            token from GitHub.
     */
    @SuppressWarnings("deprecation")
    public GitHubBranchSourceAuthorizationProvider(@CheckForNull String owner, String applicationId,
                                                   AuthorizationProvider authorizationProvider) {
        super(authorizationProvider);
        this.owner = owner;
        this.applicationId = applicationId;
    }

    @Override
    public String getEncodedAuthorization() throws IOException {
        synchronized (this) {
            if (latestToken == null || Instant.now().isAfter(this.validUntil)) {
                refreshToken();
            }
            return String.format("token %s", latestToken);
        }
    }

    @SuppressWarnings("deprecation")
    private void refreshToken() throws IOException {
        GitHub gitHub = this.gitHub();

        List<GHAppInstallation> appInstallations = gitHub.getApp()
                .listInstallations().asList();
        if (appInstallations.isEmpty()) {
            throw new IllegalArgumentException(String.format(ERROR_NOT_INSTALLED, applicationId));
        }

        GHAppInstallation appInstallation;
        if (appInstallations.size() == 1) {
            appInstallation = appInstallations.get(0);
        } else {
            appInstallation = appInstallations.stream()
                    .filter(installation -> installation.getAccount().getLogin().equals(owner))
                    .findAny()
                    .orElseThrow(() -> new IllegalArgumentException(String.format(ERROR_NOT_INSTALLED, applicationId)));
        }

        GHAppInstallationToken ghAppInstallationToken = appInstallation.createToken().create();
        this.validUntil = ghAppInstallationToken.getExpiresAt().toInstant().minus(Duration.ofMinutes(5));
        this.latestToken = Objects.requireNonNull(ghAppInstallationToken.getToken());
    }
}
