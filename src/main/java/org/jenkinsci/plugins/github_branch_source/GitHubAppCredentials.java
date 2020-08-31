package org.jenkinsci.plugins.github_branch_source;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.Util;
import hudson.remoting.Channel;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import java.io.IOException;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import jenkins.security.SlaveToMasterCallable;
import jenkins.util.JenkinsJVM;
import org.jenkinsci.plugins.workflow.support.concurrent.Timeout;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.github.GHApp;
import org.kohsuke.github.GHAppInstallation;
import org.kohsuke.github.GHAppInstallationToken;
import org.kohsuke.github.GitHub;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import static org.jenkinsci.plugins.github_branch_source.GitHubSCMNavigator.DescriptorImpl.getPossibleApiUriItems;
import static org.jenkinsci.plugins.github_branch_source.JwtHelper.createJWT;

@SuppressFBWarnings(value = "SE_NO_SERIALVERSIONID", justification = "XStream")
public class GitHubAppCredentials extends BaseStandardCredentials implements StandardUsernamePasswordCredentials {

    private static final Logger LOGGER = Logger.getLogger(GitHubAppCredentials.class.getName());

    private static final String ERROR_AUTHENTICATING_GITHUB_APP = "Couldn't authenticate with GitHub app ID %s";
    private static final String NOT_INSTALLED = ", has it been installed to your GitHub organisation / user?";

    private static final String ERROR_NOT_INSTALLED = ERROR_AUTHENTICATING_GITHUB_APP + NOT_INSTALLED;

    @NonNull
    private final String appID;

    @NonNull
    private final Secret privateKey;

    private String apiUri;

    private String owner;

    private transient AppInstallationToken cachedToken;

    @DataBoundConstructor
    @SuppressWarnings("unused") // by stapler
    public GitHubAppCredentials(
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

    public String getApiUri() {
        return apiUri;
    }

    @DataBoundSetter
    public void setApiUri(String apiUri) {
        this.apiUri = apiUri;
    }

    @NonNull
    public String getAppID() {
        return appID;
    }

    @NonNull
    public Secret getPrivateKey() {
        return privateKey;
    }

    /**
     * Owner of this installation, i.e. a user or organisation,
     * used to differeniate app installations when the app is installed to multiple organisations / users.
     *
     * If this is null then call listInstallations and if there's only one in the list then use that installation.
     *
     * @return the owner of the organisation or null.
     */
    @CheckForNull
    public String getOwner() {
        return owner;
    }

    @DataBoundSetter
    public void setOwner(String owner) {
        this.owner = Util.fixEmpty(owner);
    }

    @SuppressWarnings("deprecation") // preview features are required for GitHub app integration, GitHub api adds deprecated to all preview methods
    static AppInstallationToken generateAppInstallationToken(String appId, String appPrivateKey, String apiUrl, String owner) {
        // We expect this to be fast but if anything hangs in here we do not want to block indefinitely
        try (Timeout timeout = Timeout.limit(30, TimeUnit.SECONDS)) {
            String jwtToken = createJWT(appId, appPrivateKey);
            GitHub gitHubApp = Connector
                .createGitHubBuilder(apiUrl)
                .withJwtToken(jwtToken)
                .build();

            GHApp app = gitHubApp.getApp();

            List<GHAppInstallation> appInstallations = app.listInstallations().asList();
            if (appInstallations.isEmpty()) {
                throw new IllegalArgumentException(String.format(ERROR_NOT_INSTALLED, appId));
            }
            GHAppInstallation appInstallation;
            if (appInstallations.size() == 1) {
                appInstallation = appInstallations.get(0);
            } else {
                appInstallation = appInstallations.stream()
                    .filter(installation -> installation.getAccount().getLogin().equals(owner))
                    .findAny()
                    .orElseThrow(() -> new IllegalArgumentException(String.format(
                        ERROR_NOT_INSTALLED,
                        appId)));
            }

            GHAppInstallationToken appInstallationToken = appInstallation
                .createToken(appInstallation.getPermissions())
                .create();

            long expiration = getExpirationSeconds(appInstallationToken);
            LOGGER.log(Level.FINEST,
                "Token raw expiration epoch seconds: {0,number,#}",
                expiration);

            AppInstallationToken token = new AppInstallationToken(appInstallationToken.getToken(),
                expiration);
            LOGGER.log(Level.FINE,
                "Generated App Installation Token for app ID {0}",
                appId);

            return token;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to retrieve GitHub App installation token for app ID " + appId, e);
            throw new IllegalArgumentException(String.format(ERROR_AUTHENTICATING_GITHUB_APP, appId), e);
        }
    }

    private static long getExpirationSeconds(GHAppInstallationToken appInstallationToken) {
        try {
            return appInstallationToken.getExpiresAt()
                .toInstant()
                .getEpochSecond();
        } catch (Exception e) {
            // if we fail to calculate the expiration, guess at a reasonable value.
            LOGGER.log(Level.WARNING,
                "Unable to get GitHub App installation token expiration",
                e);
            return Instant.now().getEpochSecond() + AppInstallationToken.NOT_STALE_MINIMUM_SECONDS;
        }
    }

    @NonNull String actualApiUri() {
        return Util.fixEmpty(apiUri) == null ? "https://api.github.com" : apiUri;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Secret getPassword() {
        String appInstallationToken;
        synchronized (this) {
            try {
                if (cachedToken == null || cachedToken.isStale()) {
                    LOGGER.log(Level.FINE, "Generating App Installation Token for app ID {0}", appID);
                    cachedToken = generateAppInstallationToken(appID,
                        privateKey.getPlainText(),
                        actualApiUri(),
                        owner);
                    LOGGER.log(Level.FINER, "Retrieved GitHub App Installation Token for app ID {0}", appID);
                }
            } catch (Exception e) {
                if (cachedToken != null && !cachedToken.isExpired()) {
                    // Requesting a new token failed. If the cached token is not expired, continue to use it.
                    // This minimizes failures due to occasional network instability,
                    // while only slightly increasing the chance that tokens will expire while in use.
                    LOGGER.log(Level.WARNING,
                        "Keeping cached GitHub App Installation Token for app ID {0}: token is stale but has not expired", appID);
                } else {
                    throw e;
                }
            }
            appInstallationToken = cachedToken.getToken();
        }

        LOGGER.log(Level.FINEST, "Returned GitHub App Installation Token for app ID {0}", appID);

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

    private AppInstallationToken getCachedToken() {
        synchronized (this) {
            return cachedToken;
        }
    }

    static class AppInstallationToken implements Serializable {
        /**
         * {@link #getPassword()} checks that the token is still valid before returning it.
         * The token generally will not expire for at least this amount of time after it is returned.
         *
         * Using a larger value will result in longer time-to-live for the token, but also more network
         * calls related to getting new tokens.  Setting a smaller value will result in less token generation
         * but runs the the risk of the token expiring while it is still being used.
         *
         * The time-to-live for the token may be less than this if the initial expiration for the token when
         * it is returned from GitHub is less than this or if the token is kept and due to failures
         * while retrieving a new token.
         */
        static final long STALE_BEFORE_EXPIRATION_SECONDS = Duration.ofMinutes(45).getSeconds();

        /**
         * Any token older than this is considered stale.
         *
         * This is a back stop to ensure that, in case of unforeseen error,
         * expired tokens are not accidentally retained past their expiration.
         */
        static final long STALE_AFTER_SECONDS = Duration.ofMinutes(30).getSeconds();

        /**
         * When a token is retrieved it cannot got stale for at least this many seconds.
         *
         * Prevents continuous refreshing of credentials.
         * Non-final for testing purposes.
         * This value takes precedence over {@link #STALE_BEFORE_EXPIRATION_SECONDS}.
         * {@link #STALE_BEFORE_EXPIRATION_SECONDS} takes precedence over this value.
         * Minimum value of 1.
         */
        static long NOT_STALE_MINIMUM_SECONDS = Duration.ofMinutes(1).getSeconds();

        private final String token;
        private final long expirationEpochSeconds;
        private final long staleEpochSeconds;

        /**
         * Create a AppInstallationToken instance.
         *
         * Tokens will always become stale after {@link #STALE_AFTER_SECONDS} seconds.
         * Tokens will not become stale for at least {@link #NOT_STALE_MINIMUM_SECONDS},
         * as long as that does not exceed {@link #STALE_AFTER_SECONDS}.
         * Within the bounds of {@link #NOT_STALE_MINIMUM_SECONDS} and {@link #STALE_AFTER_SECONDS},
         * tokens will become stale {@link #STALE_BEFORE_EXPIRATION_SECONDS} seconds before they expire.
         *
         * @param token the token string
         * @param expirationEpochSeconds the time in epoch seconds that this token will expire
         */
        public AppInstallationToken(String token, long expirationEpochSeconds) {
            long now = Instant.now().getEpochSecond();
            long minimumAllowedAge = Math.max(1, NOT_STALE_MINIMUM_SECONDS);
            long maximumAllowedAge = Math.max(1, 1 + STALE_AFTER_SECONDS);

            // Tokens go stale a while before they will expire
            long secondsUntilStale = (expirationEpochSeconds - now) - STALE_BEFORE_EXPIRATION_SECONDS;

            // Tokens are never stale as soon as they are made
            if (secondsUntilStale < minimumAllowedAge) {
                secondsUntilStale = minimumAllowedAge;
            }

            // Tokens have a maximum age at which they go stale
            if (secondsUntilStale > maximumAllowedAge) {
                secondsUntilStale = maximumAllowedAge;
            }

            LOGGER.log(Level.FINER, "Token will become stale after {0,number,#} seconds", secondsUntilStale);

            this.token = token;
            this.expirationEpochSeconds = expirationEpochSeconds;
            this.staleEpochSeconds = now + secondsUntilStale;
        }

        public String getToken() {
            return token;
        }

        /**
         * Whether a token is stale and should be replaced with a new token.
         *
         * {@link #getPassword()} checks that the token is not "stale" before returning it.
         * If a token is "stale" if it has expired, exceeded {@link #STALE_AFTER_SECONDS}, or
         * will expire in less than {@link #STALE_BEFORE_EXPIRATION_SECONDS}.
         *
         * @return {@code true} if token should be refreshed, otherwise {@code false}.
         */
        public boolean isStale() {
            return Instant.now().getEpochSecond() >= staleEpochSeconds;
        }

        public boolean isExpired() {
            return Instant.now().getEpochSecond() >= expirationEpochSeconds;
        }

        long getTokenStaleEpochSeconds() {
            return staleEpochSeconds;
        }
    }

    /**
     * Ensures that the credentials state as serialized via Remoting to an agent calls back to the controller.
     * Benefits:
     * <ul>
     * <li>The token is cached locally and used until it is stale.
     * <li>The agent never needs to have access to the plaintext private key.
     * <li>We avoid the considerable amount of class loading associated with the JWT library, Jackson data binding, Bouncy Castle, etc.
     * <li>The agent need not be able to contact GitHub.
     * </ul>
     */
    private Object writeReplace() {
        if (/* XStream */Channel.current() == null) {
            return this;
        }
        return new DelegatingGitHubAppCredentials(this);
    }

    private static final class DelegatingGitHubAppCredentials extends BaseStandardCredentials implements StandardUsernamePasswordCredentials {

        private static final String SEP = "%%%";

        private final String appID;
        /** 
         * An encrypted form of all data needed to refresh the token.
         * Used to prevent {@link GetToken} from being abused by compromised build agents.
         */
        private final String tokenRefreshData;
        private AppInstallationToken cachedToken;

        private transient Channel ch;

        DelegatingGitHubAppCredentials(GitHubAppCredentials onMaster) {
            super(onMaster.getScope(), onMaster.getId(), onMaster.getDescription());
            JenkinsJVM.checkJenkinsJVM();
            appID = onMaster.appID;
            tokenRefreshData = Secret.fromString(onMaster.appID + SEP + onMaster.privateKey.getPlainText() + SEP + onMaster.actualApiUri() + SEP + onMaster.owner).getEncryptedValue();

            // Check token is valid before sending it to the agent.
            // Ensuring the cached token is not stale before sending it to agents keeps agents from having to
            // immediately refresh the token.
            // This is intentionally only a best-effort attempt.
            // If this fails, the agent will fallback to making the request (which may or may not fail).
            try {
                LOGGER.log(Level.FINEST, "Checking App Installation Token for app ID {0} before sending to agent", onMaster.appID);
                onMaster.getPassword();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to refresh stale GitHub App installation token before sending to agent for app ID " + onMaster.getAppID(), e);
            }

            cachedToken = onMaster.getCachedToken();
        }

        private Object readResolve() {
            JenkinsJVM.checkNotJenkinsJVM();
            synchronized (this) {
                ch = Channel.currentOrFail();
            }
            return this;
        }

        @NonNull
        @Override
        public String getUsername() {
            return appID;
        }

        @Override
        public Secret getPassword() {
            JenkinsJVM.checkNotJenkinsJVM();
            try {
                String appInstallationToken;
                synchronized (this) {
                    try {
                        if (cachedToken == null || cachedToken.isStale()) {
                            cachedToken = ch.call(new GetToken(tokenRefreshData));
                            LOGGER.log(Level.INFO,
                                "Retrieved GitHub App Installation Token for app ID {0} on agent",
                                appID);
                        }
                    } catch (Exception e) {
                        if (cachedToken != null && !cachedToken.isExpired()) {
                            // Requesting a new token failed. If the cached token is not expired, continue to use it.
                            // This minimizes failures due to occasional network instability,
                            // while only slightly increasing the chance that tokens will expire while in use.
                            LOGGER.log(Level.WARNING,
                                "Keeping cached GitHub App Installation Token for app ID {0} on agent: token is stale but has not expired",
                                appID);
                        } else {
                            throw e;
                        }
                    }
                    appInstallationToken = cachedToken.getToken();
                }

                LOGGER.log(Level.FINEST, "Returned GitHub App Installation Token for app ID {0} on agent", appID);

                return Secret.fromString(appInstallationToken);
            } catch (IOException | InterruptedException x) {
                LOGGER.log(Level.WARNING, "Failed to get GitHub App Installation token on agent: " + getId(), x);
                throw new RuntimeException(x);
            }
        }

        private static final class GetToken extends SlaveToMasterCallable<AppInstallationToken, RuntimeException> {

            private final String data;

            GetToken(String data) {
                this.data = data;
            }

            @Override
            public AppInstallationToken call() throws RuntimeException {
                JenkinsJVM.checkJenkinsJVM();
                String[] fields = Secret.fromString(data).getPlainText().split(SEP);
                LOGGER.log(Level.FINE, "Generating App Installation Token for app ID {0} for agent", fields[0]);
                AppInstallationToken token = generateAppInstallationToken(fields[0],
                    fields[1],
                    fields[2],
                    fields[3]);
                LOGGER.log(Level.FINER,
                    "Retrieved GitHub App Installation Token for app ID {0} for agent",
                    fields[0]);
                return token;
            }
        }
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
            return Messages.GitHubAppCredentials_displayName();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getIconClassName() {
            return "icon-github-logo";
        }

        @SuppressWarnings("unused") // jelly
        public boolean isApiUriSelectable() {
            return !GitHubConfiguration.get().getEndpoints().isEmpty();
        }

        /**
         * Returns the available GitHub endpoint items.
         *
         * @return the available GitHub endpoint items.
         */
        @SuppressWarnings("unused") // stapler
        @Restricted(NoExternalUse.class) // stapler
        public ListBoxModel doFillApiUriItems() {
            return getPossibleApiUriItems();
        }

        public FormValidation doCheckAppID(@QueryParameter String appID) {
            if (!appID.isEmpty()) {
                try {
                    Integer.parseInt(appID);
                } catch (NumberFormatException x) {
                    return FormValidation.warning("An app ID is likely to be a number, distinct from the app name");
                }
            }
            return FormValidation.ok();
        }

        @POST
        @SuppressWarnings("unused") // stapler
        @Restricted(NoExternalUse.class) // stapler
        public FormValidation doTestConnection(
                @QueryParameter("appID") final String appID,
                @QueryParameter("privateKey") final String privateKey,
                @QueryParameter("apiUri") final String apiUri,
                @QueryParameter("owner") final String owner

        ) {
            GitHubAppCredentials gitHubAppCredential = new GitHubAppCredentials(
                    CredentialsScope.GLOBAL, "test-id-not-being-saved", null,
                    appID, Secret.fromString(privateKey)
            );
            gitHubAppCredential.setApiUri(apiUri);
            gitHubAppCredential.setOwner(owner);

            try {
                GitHub connect = Connector.connect(apiUri, gitHubAppCredential);
                try {
                    return FormValidation.ok("Success, Remaining rate limit: " + connect.getRateLimit().getRemaining());
                } finally {
                    Connector.release(connect);
                }
            } catch (Exception e) {
                return FormValidation.error(e, String.format(ERROR_AUTHENTICATING_GITHUB_APP, appID));
            }
        }
    }
}
