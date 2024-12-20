package org.jenkinsci.plugins.github_branch_source;

import static org.jenkinsci.plugins.github_branch_source.GitHubSCMNavigator.DescriptorImpl.getPossibleApiUriItems;

import com.cloudbees.jenkins.GitHubRepositoryName;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import com.coravy.hudson.plugins.github.GithubProjectProperty;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.Functions;
import hudson.Util;
import hudson.model.Job;
import hudson.model.Run;
import hudson.remoting.Channel;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import java.io.IOException;
import java.io.Serializable;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import jenkins.scm.api.SCMSource;
import jenkins.security.SlaveToMasterCallable;
import jenkins.util.JenkinsJVM;
import net.sf.json.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.plugins.github_branch_source.app_credentials.AccessInferredOwner;
import org.jenkinsci.plugins.github_branch_source.app_credentials.AccessSpecifiedRepositories;
import org.jenkinsci.plugins.github_branch_source.app_credentials.AccessibleRepositories;
import org.jenkinsci.plugins.github_branch_source.app_credentials.DefaultPermissionsStrategy;
import org.jenkinsci.plugins.github_branch_source.app_credentials.MigrationAdminMonitor;
import org.jenkinsci.plugins.github_branch_source.app_credentials.RepositoryAccessStrategy;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.support.concurrent.Timeout;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.github.GHApp;
import org.kohsuke.github.GHAppCreateTokenBuilder;
import org.kohsuke.github.GHAppInstallation;
import org.kohsuke.github.GHAppInstallationToken;
import org.kohsuke.github.GHPermissionType;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.authorization.AuthorizationProvider;
import org.kohsuke.github.extras.authorization.JWTTokenProvider;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

@SuppressFBWarnings(value = "SE_NO_SERIALVERSIONID", justification = "XStream")
public class GitHubAppCredentials extends BaseStandardCredentials implements StandardUsernamePasswordCredentials {

    private static final Logger LOGGER = Logger.getLogger(GitHubAppCredentials.class.getName());

    private static final String ERROR_AUTHENTICATING_GITHUB_APP = "Couldn't authenticate with GitHub app ID %s";
    private static final String NOT_INSTALLED = ", has it been installed to your GitHub organisation / user?";

    private static final String ERROR_NOT_INSTALLED = ERROR_AUTHENTICATING_GITHUB_APP + NOT_INSTALLED;
    private static final String ERROR_NO_OWNER_MATCHING =
            "Found multiple installations for GitHub app ID %s but none match credential owner \"%s\". "
                    + "Configure the repository access strategy for the credential to use one of these owners: %s";

    /**
     * When a new {@link AppInstallationToken} is generated, wait this many seconds before continuing.
     * Has no effect when a cached token is used, only when a new token is generated.
     *
     * <p>Provided as one more possible avenue for debugging/stabilizing JENKINS-62249.
     */
    private static long AFTER_TOKEN_GENERATION_DELAY_SECONDS =
            Long.getLong(GitHubAppCredentials.class.getName() + ".AFTER_TOKEN_GENERATION_DELAY_SECONDS", 0);

    /**
     * Controls whether {@link GithubProjectProperty} is considered by {@link #forRun} for Pipeline builds.
     * <p>{@link RepositoryAccessStrategy} is intended to prevent users with the ability to edit a Jenkinsfile in a
     * single repository from being able to use GitHub app credentials available to that Pipeline to access other
     * repositories. The existence of the {@code properties} step means that job properties may not be trusted for
     * Pipeline repository inference.
     */
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "Non-final for modification from script console")
    public static boolean ALLOW_UNSAFE_REPOSITORY_INFERENCE =
            Boolean.getBoolean(GitHubAppCredentials.class.getName() + ".ALLOW_UNSAFE_REPOSITORY_INFERENCE");

    @NonNull
    private final String appID;

    @NonNull
    private final Secret privateKey;

    private String apiUri;

    @SuppressFBWarnings(value = "IS2_INCONSISTENT_SYNC", justification = "#withOwner locking only for #byOwner")
    @Deprecated
    private String owner;

    private RepositoryAccessStrategy repositoryAccessStrategy;
    private DefaultPermissionsStrategy defaultPermissionsStrategy;

    @NonNull
    private transient GitHubAppUsageContext context = new GitHubAppUsageContext();

    private transient AppInstallationToken cachedToken;

    /**
     * Caches temporary instances of these credentials created for use in distinct contexts.
     *
     * @see #contextualize
     * @see GitHubAppUsageContext
     */
    private transient Map<GitHubAppUsageContext, GitHubAppCredentials> cachedCredentials = new ConcurrentHashMap<>();

    @DataBoundConstructor
    @SuppressWarnings("unused") // by stapler
    public GitHubAppCredentials(
            CredentialsScope scope,
            String id,
            @CheckForNull String description,
            @NonNull String appID,
            @NonNull Secret privateKey) {
        super(scope, id, description);
        this.appID = appID;
        this.privateKey = privateKey;
        this.repositoryAccessStrategy = new AccessInferredOwner();
        this.defaultPermissionsStrategy = DefaultPermissionsStrategy.INHERIT_ALL;
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

    /** @deprecated Use {@link #getRepositoryAccessStrategy}. */
    @Deprecated
    @CheckForNull
    public String getOwner() {
        return null;
    }

    // This method is not deprecated or restricted only to preserve compatibility with existing CasC YAML files.
    /** Do not call this method, use {@link #setRepositoryAccessStrategy} instead. */
    public void setOwner(String owner) {
        owner = Util.fixEmptyAndTrim(owner);
        if (owner != null) {
            this.repositoryAccessStrategy = new AccessSpecifiedRepositories(owner, List.of());
        } else {
            this.repositoryAccessStrategy = new AccessInferredOwner();
        }
        // We only expect this to be called by CasC and by a few plugins which implement variants of this class based on
        // external credential providers, so we still count it as a migration.
        MigrationAdminMonitor.addMigratedCredentialId(getId());
    }

    @NonNull
    public RepositoryAccessStrategy getRepositoryAccessStrategy() {
        return repositoryAccessStrategy;
    }

    @DataBoundSetter
    public void setRepositoryAccessStrategy(@NonNull RepositoryAccessStrategy strategy) {
        this.repositoryAccessStrategy = strategy;
    }

    @NonNull
    public DefaultPermissionsStrategy getDefaultPermissionsStrategy() {
        return defaultPermissionsStrategy;
    }

    @DataBoundSetter
    public void setDefaultPermissionsStrategy(@NonNull DefaultPermissionsStrategy strategy) {
        this.defaultPermissionsStrategy = strategy;
    }

    AccessibleRepositories getAccessibleRepositories() {
        return repositoryAccessStrategy.forContext(context);
    }

    Map<String, GHPermissionType> getPermissions() {
        if (context.getPermissions() != null) {
            return context.getPermissions();
        }
        return defaultPermissionsStrategy.getPermissions();
    }

    @SuppressWarnings("deprecation")
    AuthorizationProvider getAuthorizationProvider() {
        return new CredentialsTokenProvider(this);
    }

    private static AuthorizationProvider createJwtProvider(String appId, String appPrivateKey) {
        try {
            return new JWTTokenProvider(appId, appPrivateKey);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException(
                    "Couldn't parse private key for GitHub app, make sure it's PKCS#8 format", e);
        }
    }

    private abstract static class TokenProvider extends GitHub.DependentAuthorizationProvider {

        protected TokenProvider(String appID, String privateKey) {
            super(createJwtProvider(appID, privateKey));
        }

        /**
         * Create and return the specialized GitHub instance to be used for refreshing
         * AppInstallationToken
         *
         * <p>The {@link GitHub.DependentAuthorizationProvider} provides a specialized GitHub instance
         * that uses JWT for authorization and does not check rate limit since it doesn't apply for the
         * App endpoints when using JWT.
         */
        static GitHub createTokenRefreshGitHub(String appId, String appPrivateKey, String apiUrl) throws IOException {
            TokenProvider provider = new TokenProvider(appId, appPrivateKey) {
                @Override
                public String getEncodedAuthorization() throws IOException {
                    // Will never be called
                    return null;
                }
            };
            Connector.createGitHubBuilder(apiUrl)
                    .withAuthorizationProvider(provider)
                    .build();

            return provider.gitHub();
        }
    }

    private static class CredentialsTokenProvider extends TokenProvider {
        private final GitHubAppCredentials credentials;

        CredentialsTokenProvider(GitHubAppCredentials credentials) {
            super(credentials.getAppID(), credentials.getPrivateKey().getPlainText());
            this.credentials = credentials;
        }

        public String getEncodedAuthorization() throws IOException {
            Secret token = credentials.getToken(gitHub()).getToken();
            return String.format("token %s", token.getPlainText());
        }
    }

    @SuppressWarnings("deprecation") // preview features are required for GitHub app integration, GitHub api adds
    // deprecated to all preview methods
    static AppInstallationToken generateAppInstallationToken(
            GitHub gitHubApp,
            String appId,
            String appPrivateKey,
            String apiUrl,
            String owner,
            List<String> repositories,
            Map<String, GHPermissionType> permissions) {
        JenkinsJVM.checkJenkinsJVM();
        // We expect this to be fast but if anything hangs in here we do not want to block indefinitely

        try (Timeout ignored = Timeout.limit(30, TimeUnit.SECONDS)) {
            if (gitHubApp == null) {
                gitHubApp = TokenProvider.createTokenRefreshGitHub(appId, appPrivateKey, apiUrl);
            }

            GHApp app;
            try {
                app = gitHubApp.getApp();
            } catch (IOException e) {
                throw new IllegalArgumentException(String.format(ERROR_AUTHENTICATING_GITHUB_APP, appId), e);
            }

            List<GHAppInstallation> appInstallations = app.listInstallations().asList();
            if (appInstallations.isEmpty()) {
                throw new IllegalArgumentException(String.format(ERROR_NOT_INSTALLED, appId));
            }
            GHAppInstallation appInstallation;
            if (StringUtils.isEmpty(owner) && appInstallations.size() == 1) {
                // This case is only used when AccessSpecifiedRepositories.getOwner is empty.
                appInstallation = appInstallations.get(0);
            } else {
                final String ownerOrEmpty = owner != null ? owner : "";
                Optional<GHAppInstallation> appInstallationOptional = appInstallations.stream()
                        .filter(installation -> installation
                                .getAccount()
                                .getLogin()
                                .toLowerCase(Locale.ROOT)
                                .equals(ownerOrEmpty.toLowerCase(Locale.ROOT)))
                        .findAny();
                if (appInstallationOptional.isEmpty()) {
                    String logins = appInstallations.stream()
                            .map(installation -> installation.getAccount().getLogin())
                            .collect(Collectors.joining(", "));
                    throw new IllegalArgumentException(
                            String.format(ERROR_NO_OWNER_MATCHING, appId, ownerOrEmpty, logins));
                }
                appInstallation = appInstallationOptional.get();
            }

            GHAppCreateTokenBuilder builder = appInstallation.createToken();
            if (!repositories.isEmpty()) {
                builder.repositories(repositories);
            }
            if (!permissions.isEmpty()) {
                builder.permissions(permissions);
            } else {
                builder.permissions(appInstallation.getPermissions());
            }
            GHAppInstallationToken appInstallationToken = builder.create();

            long expiration = getExpirationSeconds(appInstallationToken);
            AppInstallationToken token =
                    new AppInstallationToken(Secret.fromString(appInstallationToken.getToken()), expiration);
            LOGGER.log(
                    Level.FINEST,
                    "Generated App Installation Token for app ID {0} limited to {1}/{2} with permissions {3} at {4}",
                    new Object[] {appId, owner, repositories, permissions, Instant.now()});

            if (AFTER_TOKEN_GENERATION_DELAY_SECONDS > 0) {
                // Delay can be up to 10 seconds.
                long tokenDelay = Math.min(10, AFTER_TOKEN_GENERATION_DELAY_SECONDS);
                LOGGER.log(Level.FINER, "Waiting {0} seconds after token generation", tokenDelay);
                Thread.sleep(Duration.ofSeconds(tokenDelay).toMillis());
            }

            return token;
        } catch (IOException | InterruptedException e) {
            throw new IllegalArgumentException(
                    "Failed to generate GitHub App installation token for app ID " + appId, e);
        }
    }

    private static long getExpirationSeconds(GHAppInstallationToken appInstallationToken) {
        try {
            return appInstallationToken.getExpiresAt().toInstant().getEpochSecond();
        } catch (Exception e) {
            // if we fail to calculate the expiration, guess at a reasonable value.
            LOGGER.log(Level.WARNING, "Unable to get GitHub App installation token expiration", e);
            return Instant.now().getEpochSecond() + AppInstallationToken.NOT_STALE_MINIMUM_SECONDS;
        }
    }

    @NonNull
    String actualApiUri() {
        return Util.fixEmpty(getApiUri()) == null ? "https://api.github.com" : getApiUri();
    }

    private static class InferredAccessibleRepositoriesException extends IllegalStateException {

        public InferredAccessibleRepositoriesException(final GitHubAppCredentials credentials) {
            super("Cannot generate App Installation Token for app ID "
                    + credentials.getAppID()
                    + " because the accessible repositories could not be inferred. This is due to the repository access configuration for the credentials with ID: "
                    + credentials.getId());
        }
    }

    private AppInstallationToken getToken(GitHub gitHub) {
        synchronized (this) {
            try {
                if (cachedToken == null || cachedToken.isStale()) {
                    LOGGER.log(Level.FINE, "Generating App Installation Token for app ID {0}", getAppID());
                    final var accessibleRepositories = getAccessibleRepositories();
                    if (accessibleRepositories == null) {
                        throw new InferredAccessibleRepositoriesException(this);
                    }
                    cachedToken = generateAppInstallationToken(
                            gitHub,
                            getAppID(),
                            getPrivateKey().getPlainText(),
                            actualApiUri(),
                            accessibleRepositories.getOwner(),
                            accessibleRepositories.getRepositories(),
                            getPermissions());
                    LOGGER.log(Level.FINER, "Retrieved GitHub App Installation Token for app ID {0}", getAppID());
                }
            } catch (Exception e) {
                if (cachedToken != null && !cachedToken.isExpired()) {
                    // Requesting a new token failed. If the cached token is not expired, continue to use it.
                    // This minimizes failures due to occasional network instability,
                    // while only slightly increasing the chance that tokens will expire while in use.
                    LOGGER.log(
                            Level.WARNING,
                            "Failed to generate new GitHub App Installation Token for app ID "
                                    + getAppID()
                                    + ": cached token is stale but has not expired",
                            e);
                } else {
                    throw e;
                }
            }
            LOGGER.log(Level.FINEST, "Returned GitHub App Installation Token for app ID {0}", getAppID());

            return cachedToken;
        }
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public Secret getPassword() {
        return this.getToken(null).getToken();
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public String getUsername() {
        return getAppID();
    }

    @Override
    public boolean isUsernameSecret() {
        return false;
    }

    @NonNull
    public GitHubAppCredentials contextualize(final GitHubAppUsageContext context) {
        return cachedCredentials.computeIfAbsent(context, this::clone);
    }

    @NonNull
    private GitHubAppCredentials clone(final GitHubAppUsageContext context) {
        final var clone = new GitHubAppCredentials(getScope(), getId(), getDescription(), getAppID(), getPrivateKey());
        clone.apiUri = getApiUri();
        clone.repositoryAccessStrategy = getRepositoryAccessStrategy();
        clone.defaultPermissionsStrategy = getDefaultPermissionsStrategy();
        clone.context = context;
        return clone;
    }

    @NonNull
    @Override
    public Credentials forRun(Run<?, ?> context) {
        Job<?, ?> job = context.getParent();
        SCMSource src = SCMSource.SourceByItem.findSource(job);
        if (src instanceof GitHubSCMSource) {
            GitHubSCMSource source = (GitHubSCMSource) src;
            final var usageContext = GitHubAppUsageContext.builder()
                    .inferredOwner(source.getRepoOwner())
                    .inferredRepository(source.getRepository())
                    .permissions(defaultPermissionsStrategy.getPermissions())
                    .build();
            return contextualize(usageContext);
        }

        GitHubRepositoryName ghrn = GitHubRepositoryName.create(job.getProperty(GithubProjectProperty.class));
        if (ghrn != null) {
            if (ALLOW_UNSAFE_REPOSITORY_INFERENCE || !(context instanceof FlowExecutionOwner.Executable)) {
                final var usageContext = GitHubAppUsageContext.builder()
                        .inferredOwner(ghrn.userName)
                        .inferredRepository(ghrn.repositoryName)
                        .permissions(defaultPermissionsStrategy.getPermissions())
                        .build();
                return contextualize(usageContext);
            }
        }
        return this;
    }

    private AppInstallationToken getCachedToken() {
        synchronized (this) {
            return cachedToken;
        }
    }

    static class AppInstallationToken implements Serializable {
        /**
         * {@link #getPassword()} checks that the token is still valid before returning it. The token
         * generally will not expire for at least this amount of time after it is returned.
         *
         * <p>Using a larger value will result in longer time-to-live for the token, but also more
         * network calls related to getting new tokens. Setting a smaller value will result in less
         * token generation but runs the the risk of the token expiring while it is still being used.
         *
         * <p>The time-to-live for the token may be less than this if the initial expiration for the
         * token when it is returned from GitHub is less than this or if the token is kept and due to
         * failures while retrieving a new token. Non-final for testing/debugging purposes.
         */
        static long STALE_BEFORE_EXPIRATION_SECONDS = Long.getLong(
                GitHubAppCredentials.class.getName() + ".STALE_BEFORE_EXPIRATION_SECONDS",
                Duration.ofMinutes(45).getSeconds());

        /**
         * Any token older than this is considered stale.
         *
         * <p>This is a back stop to ensure that, in case of unforeseen error, expired tokens are not
         * accidentally retained past their expiration.
         */
        static final long STALE_AFTER_SECONDS = Duration.ofMinutes(30).getSeconds();

        /**
         * When a token is retrieved it cannot got stale for at least this many seconds.
         *
         * <p>Prevents continuous refreshing of credentials. Non-final for testing purposes. This value
         * takes precedence over {@link #STALE_BEFORE_EXPIRATION_SECONDS}. If {@link
         * #STALE_AFTER_SECONDS} is less than this value, {@link #STALE_AFTER_SECONDS} takes precedence
         * over this value. Minimum value of 1.
         */
        static long NOT_STALE_MINIMUM_SECONDS = Long.getLong(
                GitHubAppCredentials.class.getName() + ".NOT_STALE_MINIMUM_SECONDS",
                Duration.ofMinutes(1).getSeconds());

        private final Secret token;
        private final long expirationEpochSeconds;
        private final long staleEpochSeconds;

        /**
         * Create a AppInstallationToken instance.
         *
         * <p>Tokens will always become stale after {@link #STALE_AFTER_SECONDS} seconds. Tokens will
         * not become stale for at least {@link #NOT_STALE_MINIMUM_SECONDS}, as long as that does not
         * exceed {@link #STALE_AFTER_SECONDS}. Within the bounds of {@link #NOT_STALE_MINIMUM_SECONDS}
         * and {@link #STALE_AFTER_SECONDS}, tokens will become stale {@link
         * #STALE_BEFORE_EXPIRATION_SECONDS} seconds before they expire.
         *
         * @param token the token string
         * @param expirationEpochSeconds the time in epoch seconds that this token will expire
         */
        public AppInstallationToken(Secret token, long expirationEpochSeconds) {
            long now = Instant.now().getEpochSecond();
            long secondsUntilExpiration = expirationEpochSeconds - now;

            long minimumAllowedAge = Math.max(1, NOT_STALE_MINIMUM_SECONDS);
            long maximumAllowedAge = Math.max(1, 1 + STALE_AFTER_SECONDS);

            // Tokens go stale a while before they will expire
            long secondsUntilStale = secondsUntilExpiration - STALE_BEFORE_EXPIRATION_SECONDS;

            // Tokens are never stale as soon as they are made
            if (secondsUntilStale < minimumAllowedAge) {
                secondsUntilStale = minimumAllowedAge;
            }

            // Tokens have a maximum age at which they go stale
            if (secondsUntilStale > maximumAllowedAge) {
                secondsUntilStale = maximumAllowedAge;
            }

            LOGGER.log(Level.FINER, "Token will become stale after " + secondsUntilStale + " seconds");

            this.token = token;
            this.expirationEpochSeconds = expirationEpochSeconds;
            this.staleEpochSeconds = now + secondsUntilStale;
        }

        public Secret getToken() {
            return token;
        }

        /**
         * Whether a token is stale and should be replaced with a new token.
         *
         * <p>{@link #getPassword()} checks that the token is not "stale" before returning it. If a
         * token is "stale" if it has expired, exceeded {@link #STALE_AFTER_SECONDS}, or will expire in
         * less than {@link #STALE_BEFORE_EXPIRATION_SECONDS}.
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

    private Object readResolve() {
        cachedCredentials = new ConcurrentHashMap<>();
        if (repositoryAccessStrategy == null || defaultPermissionsStrategy == null) {
            if (owner != null) {
                // In this case, the migration should result in identical behavior.
                repositoryAccessStrategy = new AccessSpecifiedRepositories(owner, Collections.emptyList());
            } else {
                // There is a choice here: We can either preserve compatibility for users who have
                // the app installed in multiple orgs and only use the credentials in contexts
                // where owner inference is supported by using AccessInferredOwner, _or_ we can
                // preserve compatibility for users who have the app installed in a single org and
                // use it in contexts where inference is not supported by using
                // AccessSpecifiedRepositories with a null owner.
                // None of the new strategies support these two use cases simultaneously.
                repositoryAccessStrategy = new AccessInferredOwner();
            }
            defaultPermissionsStrategy = DefaultPermissionsStrategy.INHERIT_ALL;
            MigrationAdminMonitor.addMigratedCredentialId(getId());
        }
        owner = null;
        context = new GitHubAppUsageContext();
        return this;
    }

    /**
     * Ensures that the credentials state as serialized via Remoting to an agent calls back to the
     * controller. Benefits:
     *
     * <ul>
     *   <li>The token is cached locally and used until it is stale.
     *   <li>The agent never needs to have access to the plaintext private key.
     *   <li>We avoid the considerable amount of class loading associated with the JWT library,
     *       Jackson data binding, Bouncy Castle, etc.
     *   <li>The agent need not be able to contact GitHub.
     * </ul>
     */
    protected Object writeReplace() {
        if (
        /* XStream */ Channel.current() == null) {
            return this;
        }
        return new DelegatingGitHubAppCredentials(this);
    }

    private static final class DelegatingGitHubAppCredentials extends BaseStandardCredentials
            implements StandardUsernamePasswordCredentials {

        private final String appID;
        /**
         * An encrypted form of all data needed to refresh the token. Used to prevent {@link GetToken}
         * from being abused by compromised build agents.
         */
        private final String tokenRefreshData;

        private AppInstallationToken cachedToken;

        private transient Channel ch;

        DelegatingGitHubAppCredentials(GitHubAppCredentials onMaster) {
            super(onMaster.getScope(), onMaster.getId(), onMaster.getDescription());
            JenkinsJVM.checkJenkinsJVM();
            appID = onMaster.getAppID();
            JSONObject j = new JSONObject();
            j.put("appID", appID);
            j.put("privateKey", onMaster.getPrivateKey().getPlainText());
            j.put("apiUri", onMaster.actualApiUri());
            final var accessibleRepositories = onMaster.getAccessibleRepositories();
            if (accessibleRepositories == null) {
                throw new InferredAccessibleRepositoriesException(onMaster);
            }
            j.put("owner", accessibleRepositories.getOwner());
            j.put("repositories", accessibleRepositories.getRepositories());
            j.put("permissions", onMaster.getPermissions());
            tokenRefreshData = Secret.fromString(j.toString()).getEncryptedValue();

            // Check token is valid before sending it to the agent.
            // Ensuring the cached token is not stale before sending it to agents keeps agents from having
            // to
            // immediately refresh the token.
            // This is intentionally only a best-effort attempt.
            // If this fails, the agent will fallback to making the request (which may or may not fail).
            try {
                LOGGER.log(
                        Level.FINEST,
                        "Checking App Installation Token for app ID {0} before sending to agent",
                        onMaster.getAppID());
                onMaster.getPassword();
            } catch (Exception e) {
                LOGGER.log(
                        Level.WARNING,
                        "Failed to update stale GitHub App installation token for app ID "
                                + onMaster.getAppID()
                                + " before sending to agent",
                        e);
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
                synchronized (this) {
                    try {
                        if (cachedToken == null || cachedToken.isStale()) {
                            LOGGER.log(Level.FINE, "Generating App Installation Token for app ID {0} on agent", appID);
                            cachedToken = ch.call(new GetToken(tokenRefreshData));
                            LOGGER.log(
                                    Level.FINER,
                                    "Retrieved GitHub App Installation Token for app ID {0} on agent",
                                    appID);
                            LOGGER.log(
                                    Level.FINEST,
                                    () -> "Generated App Installation Token at "
                                            + Instant.now().toEpochMilli()
                                            + " on agent");
                        }
                    } catch (Exception e) {
                        if (cachedToken != null && !cachedToken.isExpired()) {
                            // Requesting a new token failed. If the cached token is not expired, continue to use
                            // it.
                            // This minimizes failures due to occasional network instability,
                            // while only slightly increasing the chance that tokens will expire while in use.
                            LOGGER.log(
                                    Level.WARNING,
                                    "Failed to generate new GitHub App Installation Token for app ID "
                                            + appID
                                            + " on agent: cached token is stale but has not expired");
                            // Logging the exception here caused a security exception when trying to read the
                            // agent logs during testing
                            // Added the exception to a secondary log message that can be viewed if it is needed
                            LOGGER.log(Level.FINER, () -> Functions.printThrowable(e));
                        } else {
                            throw e;
                        }
                    }
                    LOGGER.log(Level.FINEST, "Returned GitHub App Installation Token for app ID {0} on agent", appID);

                    return cachedToken.getToken();
                }

            } catch (IOException | InterruptedException x) {
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
                JSONObject fields =
                        JSONObject.fromObject(Secret.fromString(data).getPlainText());
                LOGGER.log(
                        Level.FINE, "Generating App Installation Token for app ID {0} for agent", fields.get("appID"));
                AppInstallationToken token = generateAppInstallationToken(
                        null,
                        (String) fields.get("appID"),
                        (String) fields.get("privateKey"),
                        (String) fields.get("apiUri"),
                        (String) fields.get("owner"),
                        (List<String>) fields.get("repositories"),
                        (Map<String, GHPermissionType>) fields.get("permissions"));
                LOGGER.log(
                        Level.FINER,
                        "Retrieved GitHub App Installation Token for app ID {0} for agent",
                        fields.get("appID"));
                return token;
            }
        }
    }

    /** {@inheritDoc} */
    @Extension
    public static class DescriptorImpl extends BaseStandardCredentialsDescriptor {

        /** {@inheritDoc} */
        @Override
        public String getDisplayName() {
            return Messages.GitHubAppCredentials_displayName();
        }

        /** {@inheritDoc} */
        @Override
        public String getIconClassName() {
            return "symbol-logo-github plugin-ionicons-api";
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
                @QueryParameter("testConnectionOwner") final String owner) {

            GitHubAppCredentials gitHubAppCredential = new GitHubAppCredentials(
                    CredentialsScope.GLOBAL, "test-id-not-being-saved", null, appID, Secret.fromString(privateKey));
            gitHubAppCredential.setApiUri(apiUri);
            try {
                final String inferredOwner;
                // If no owner is specified, check if the app has multiple installations.
                if (owner == null || owner.isEmpty()) {
                    GitHub gitHubApp = TokenProvider.createTokenRefreshGitHub(
                            appID, privateKey, gitHubAppCredential.actualApiUri());
                    List<GHAppInstallation> appInstallations =
                            gitHubApp.getApp().listInstallations().toList();
                    if (appInstallations.size() > 1) {
                        // Just pick the owner of the first installation, so we have a valid
                        // owner to create an access token for testing the connection.
                        inferredOwner = appInstallations.get(0).getAccount().getLogin();
                    } else {
                        inferredOwner = StringUtils.EMPTY;
                    }
                } else {
                    inferredOwner = owner;
                }

                final var usageContext = GitHubAppUsageContext.builder()
                        .inferredOwner(inferredOwner)
                        .trust()
                        .build();
                final var contextualized = gitHubAppCredential.contextualize(usageContext);
                GitHub connect = Connector.connect(apiUri, contextualized);
                try {
                    return FormValidation.ok("Success, Remaining rate limit: "
                            + connect.getRateLimit().getRemaining());
                } finally {
                    Connector.release(connect);
                }
            } catch (Exception e) {
                return FormValidation.error(e, String.format(ERROR_AUTHENTICATING_GITHUB_APP, appID));
            }
        }
    }
}
