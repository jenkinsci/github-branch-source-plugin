package org.jenkinsci.plugins.github_branch_source;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.util.Secret;
import java.io.IOException;
import java.util.List;
import org.kohsuke.github.GHApp;
import org.kohsuke.github.GHAppInstallation;
import org.kohsuke.github.GHAppInstallationToken;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.stapler.DataBoundConstructor;

import static org.jenkinsci.plugins.github_branch_source.JwtHelper.createJWT;

public class GitHubAppCredential extends BaseStandardCredentials implements StandardUsernamePasswordCredentials {

    private static final String ERROR_AUTHENTICATING_GITHUB_APP = "Couldn't authenticate with GitHub app ID %s";

    @NonNull
    private final String appID;

    @NonNull
    private final Secret privateKey;

    private String apiUrl;

    /**
     * Constructor.
     *
     * @param scope       the credentials scope
     * @param id          the ID or {@code null} to generate a new one.
     * @param description the description.
     * @param appID       the username.
     * @param privateKey  the password.
     */
    @DataBoundConstructor
    @SuppressWarnings("unused") // by stapler
    public GitHubAppCredential(
            CredentialsScope scope,
            String id,
            @CheckForNull String description,
            @NonNull String appID,
            @NonNull Secret privateKey
    ) {
        super(scope, id, description);
        this.appID = appID;
        this.privateKey = privateKey;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    @NonNull
    public String getAppID() {
        return appID;
    }

    @NonNull
    public Secret getPrivateKey() {
        return privateKey;
    }

    @SuppressWarnings("deprecation") // preview features are required for GitHub app integration, GitHub api adds deprecated to all preview methods
    static String generateAppInstallationToken(String appId, String appPrivateKey, String apiUrl) {
        try {
            String jwtToken = createJWT(appId, appPrivateKey);
            GitHub gitHubApp = new GitHubBuilder().withEndpoint(apiUrl).withJwtToken(jwtToken).build();

            GHApp app = gitHubApp.getApp();

            List<GHAppInstallation> appInstallations = app.listInstallations().asList();
            if (!appInstallations.isEmpty()) {
                GHAppInstallation appInstallation = appInstallations.get(0);
                GHAppInstallationToken appInstallationToken = appInstallation
                        .createToken(appInstallation.getPermissions())
                        .create();

                return appInstallationToken.getToken();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException(String.format(ERROR_AUTHENTICATING_GITHUB_APP, appId), e);
        }
        throw new IllegalArgumentException(String.format(ERROR_AUTHENTICATING_GITHUB_APP, appId));
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Secret getPassword() {
        if (Util.fixEmpty(apiUrl) == null) {
            apiUrl = "https://api.github.com";
        }

        String appInstallationToken = generateAppInstallationToken(appID, privateKey.getPlainText(), apiUrl);

        return Secret.fromString(appInstallationToken);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public String getUsername() {
        return appID;
    }

    /**
     * {@inheritDoc}
     */
    @Extension
    public static class DescriptorImpl extends BaseStandardCredentialsDescriptor {

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.GitHubAppCredential_displayName();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getIconClassName() {
            return "icon-github-logo";
        }
    }
}
