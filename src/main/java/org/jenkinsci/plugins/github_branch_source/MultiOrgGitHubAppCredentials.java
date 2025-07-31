package org.jenkinsci.plugins.github_branch_source;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.kohsuke.github.GHApp;
import org.kohsuke.github.GHAppInstallation;
import org.kohsuke.github.GHException;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.authorization.AuthorizationProvider;
import org.kohsuke.github.extras.authorization.JWTTokenProvider;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

/**
 * Enhanced GitHub App credentials that support multiple organizations.
 *
 * <p>This credential type extends the basic GitHub App credentials but adds
 * the capability to dynamically select from available organizations where
 * the app is installed, without requiring duplicate credentials.</p>
 *
 * <p>Key features:</p>
 * <ul>
 *   <li>Automatic discovery of organizations where the GitHub App is installed</li>
 *   <li>Organization-specific token generation on demand</li>
 *   <li>Token caching to minimize API calls</li>
 *   <li>Seamless integration with existing GitHub SCM Sources</li>
 * </ul>
 *
 * @since 2.15.0
 */
@SuppressFBWarnings(value = "SE_NO_SERIALVERSIONID", justification = "XStream")
public class MultiOrgGitHubAppCredentials extends GitHubAppCredentials {

    private static final Logger LOGGER = Logger.getLogger(MultiOrgGitHubAppCredentials.class.getName());

    /**
     * Cached list of available organizations for this app
     */
    private transient volatile List<String> availableOrganizations;

    /**
     * Last time the organizations were refreshed (in milliseconds)
     */
    private transient volatile long lastRefreshTime;

    /**
     * How long to cache the organizations list before refreshing (in milliseconds)
     */
    private static final long CACHE_TTL = 3600000; // 1 hour

    /**
     * Maximum number of cached tokens to prevent memory leaks
     */
    private static final int MAX_CACHED_TOKENS = 100;

    /**
     * Minimum interval between API calls to prevent rate limiting (in milliseconds)
     */
    private static final long MIN_API_CALL_INTERVAL = 100; // 100ms

    /**
     * Last time an API call was made
     */
    private transient volatile long lastApiCall;

    /**
     * Cache of tokens by organization
     */
    private transient volatile Map<String, GitHubAppCredentials.AppInstallationToken> tokensByOrg;

    /**
     * Lock object for synchronizing access to tokensByOrg
     */
    private final Object tokensByOrgLock = new Object();

    @DataBoundConstructor
    public MultiOrgGitHubAppCredentials(
            CredentialsScope scope,
            String id,
            @CheckForNull String description,
            @NonNull String appID,
            @NonNull Secret privateKey) {
        super(scope, id, description, appID, privateKey);

        // Input validation
        if (appID == null || appID.trim().isEmpty()) {
            throw new IllegalArgumentException("GitHub App ID cannot be null or empty");
        }
        if (privateKey == null
                || privateKey.getPlainText() == null
                || privateKey.getPlainText().trim().isEmpty()) {
            throw new IllegalArgumentException("GitHub App private key cannot be null or empty");
        }
    }

    /**
     * Returns a list of available organizations where this GitHub App is installed.
     * Results are cached to avoid frequent API calls.
     *
     * @return list of organization names
     */
    public List<String> getAvailableOrganizations() {
        long now = System.currentTimeMillis();
        if (availableOrganizations == null || (now - lastRefreshTime) > CACHE_TTL) {
            refreshAvailableOrganizations(true); // Use rate limiting for internal calls
        }
        return availableOrganizations != null
                ? Collections.unmodifiableList(availableOrganizations)
                : Collections.emptyList();
    }

    /**
     * Refreshes the list of available organizations where this GitHub App is installed.
     */
    public void refreshAvailableOrganizations() {
        refreshAvailableOrganizations(false); // No rate limiting for direct calls
    }

    /**
     * Internal method to refresh organizations with optional rate limiting.
     */
    private void refreshAvailableOrganizations(boolean useRateLimiting) {
        // Rate limiting protection - but allow calls when cache is expired or when not using rate limiting
        long now = System.currentTimeMillis();
        boolean cacheExpired = (now - lastRefreshTime) > CACHE_TTL;
        if (useRateLimiting && !cacheExpired && now - lastApiCall < MIN_API_CALL_INTERVAL) {
            LOGGER.log(Level.FINE, "Skipping API call due to rate limiting");
            return;
        }
        lastApiCall = now;
        try {
            // Create GitHub instance with JWT authentication
            String apiUrl = actualApiUri();
            String appId = getAppID();
            String privateKeyStr = getPrivateKey().getPlainText();

            // Create JWT token provider
            AuthorizationProvider jwtProvider = createJwtProvider(appId, privateKeyStr);

            // Build GitHub instance with JWT authentication
            GitHub gitHubApp = new GitHubBuilder()
                    .withEndpoint(apiUrl)
                    .withAuthorizationProvider(jwtProvider)
                    .build();

            GHApp app = gitHubApp.getApp();
            List<GHAppInstallation> appInstallations = app.listInstallations().asList();

            List<String> organizations = new ArrayList<>();
            for (GHAppInstallation installation : appInstallations) {
                try {
                    String login = installation.getAccount().getLogin();
                    organizations.add(login);
                } catch (RuntimeException e) {
                    LOGGER.log(Level.WARNING, "Error getting login for installation: " + e.getMessage(), e);
                }
            }

            this.availableOrganizations = organizations;
            this.lastRefreshTime = System.currentTimeMillis();

            LOGGER.log(Level.FINE, "Refreshed available organizations for GitHub App ID {0}: {1}", new Object[] {
                getAppID(), String.join(", ", availableOrganizations)
            });
        } catch (IOException | GHException e) {
            LOGGER.log(Level.WARNING, "Failed to retrieve available organizations for GitHub App ID " + getAppID(), e);
            // Initialize with empty list if not set
            if (this.availableOrganizations == null) {
                this.availableOrganizations = new ArrayList<>();
            }
        }
    }

    /**
     * Forces a refresh of the available organizations list, regardless of cache status.
     * This can be used when a GitHub App is installed to a new organization.
     */
    public void forceRefreshOrganizations() {
        this.lastRefreshTime = 0; // Invalidate cache
        this.lastApiCall = 0; // Reset rate limiting for forced refresh
        refreshAvailableOrganizations(false); // No rate limiting for forced calls
    }

    /**
     * Gets an installation token for a specific organization.
     * This ensures that the token is valid for the specified organization.
     *
     * @param orgName the organization name
     * @return the app installation token for the organization
     */
    public GitHubAppCredentials.AppInstallationToken getTokenForOrg(String orgName) {
        if (tokensByOrg == null) {
            tokensByOrg = new HashMap<>();
        }

        synchronized (tokensByOrgLock) {
            GitHubAppCredentials.AppInstallationToken token = tokensByOrg.get(orgName);

            try {
                if (token == null || token.isStale()) {
                    // Clean up expired tokens before adding new ones
                    cleanupExpiredTokens();

                    // Generate a token specifically for this organization
                    token = GitHubAppCredentials.generateAppInstallationToken(
                            null, getAppID(), getPrivateKey().getPlainText(), actualApiUri(), orgName);
                    tokensByOrg.put(orgName, token);

                    // Enforce cache size limit
                    if (tokensByOrg.size() > MAX_CACHED_TOKENS) {
                        // Remove oldest tokens (simple LRU-like cleanup)
                        String oldestOrg = tokensByOrg.keySet().iterator().next();
                        tokensByOrg.remove(oldestOrg);
                        LOGGER.log(Level.FINE, "Removed oldest cached token for org: " + oldestOrg);
                    }
                }
            } catch (RuntimeException e) {
                if (token != null && !token.isExpired()) {
                    // Requesting a new token failed. If the cached token is not expired, continue to use it.
                    LOGGER.log(
                            Level.WARNING,
                            "Failed to generate new GitHub App Installation Token for app ID "
                                    + getAppID() + " and org " + orgName
                                    + ": cached token is stale but has not expired",
                            e);
                } else {
                    throw new RuntimeException(
                            "Failed to generate GitHub App Installation Token for org " + orgName, e);
                }
            }

            return token;
        }
    }

    /**
     * Cleanup expired tokens from the cache to prevent memory leaks.
     * This method should be called from within a synchronized block on tokensByOrgLock.
     */
    private void cleanupExpiredTokens() {
        if (tokensByOrg == null) return;

        Iterator<Map.Entry<String, GitHubAppCredentials.AppInstallationToken>> iterator =
                tokensByOrg.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, GitHubAppCredentials.AppInstallationToken> entry = iterator.next();
            if (entry.getValue().isExpired()) {
                iterator.remove();
                LOGGER.log(Level.FINE, "Removed expired token for org: " + entry.getKey());
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * For MultiOrgGitHubAppCredentials, we use the token specific to the currently set owner.
     * If no owner is set, we return a token for the first available organization.
     */
    @NonNull
    @Override
    public Secret getPassword() {
        String owner = getOwner();

        // If no specific owner is set, use the first available organization
        if (owner == null || owner.isEmpty()) {
            List<String> orgs = getAvailableOrganizations();
            if (!orgs.isEmpty()) {
                owner = orgs.get(0);
            }
        }

        // If we have an owner, get a token specifically for that org
        if (owner != null && !owner.isEmpty()) {
            return getTokenForOrg(owner).getToken();
        }

        // Fall back to the parent implementation if we couldn't determine an owner
        return super.getPassword();
    }

    /**
     * Create a specific GitHubAppCredentials instance for the given organization.
     * This method allows using the MultiOrgGitHubAppCredentials to generate
     * organization-specific credentials dynamically.
     *
     * @param orgName the organization name to create credentials for
     * @return a GitHubAppCredentials instance configured for the specific organization
     */
    public GitHubAppCredentials forOrganization(@NonNull String orgName) {
        // Check if this organization is available
        if (!getAvailableOrganizations().contains(orgName)) {
            LOGGER.log(
                    Level.WARNING,
                    "Organization {0} is not in the list of available organizations for GitHub App ID {1}. "
                            + "Available organizations: {2}",
                    new Object[] {orgName, getAppID(), String.join(", ", getAvailableOrganizations())});
            // Still create the credential, but log a warning
        }

        // Create specialized credentials for this org
        GitHubAppCredentials credentials = withOwner(orgName);

        // Pre-warm the token cache for this organization
        try {
            getTokenForOrg(orgName);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to pre-warm token cache for organization {0}: {1}", new Object[] {
                orgName, e.getMessage()
            });
        }

        return credentials;
    }

    /**
     * Creates a JWT token provider for GitHub App authentication.
     *
     * @param appId the GitHub App ID
     * @param appPrivateKey the private key for the GitHub App
     * @return an AuthorizationProvider for JWT authentication
     */
    private static AuthorizationProvider createJwtProvider(String appId, String appPrivateKey) {
        try {
            return new JWTTokenProvider(appId, appPrivateKey);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException(
                    "Couldn't parse private key for GitHub app, make sure it's PKCS#8 format", e);
        }
    }

    /**
     * The descriptor for {@link MultiOrgGitHubAppCredentials}.
     */
    @Extension
    public static class DescriptorImpl extends GitHubAppCredentials.DescriptorImpl {

        /** {@inheritDoc} */
        @Override
        public String getDisplayName() {
            return Messages.MultiOrgGitHubAppCredentials_displayName();
        }

        /**
         * Returns the available GitHub organizations for the credential.
         *
         * @param credentialId the credential ID
         * @return list box model with available organizations
         */
        @POST
        public ListBoxModel doFillOrganizationItems(@AncestorInPath Item context, @QueryParameter String credentialId) {
            StandardListBoxModel result = new StandardListBoxModel();

            if (credentialId.isEmpty()) {
                return result.includeEmptyValue();
            }

            if (context == null && !Jenkins.get().hasPermission(Jenkins.ADMINISTER)
                    || context != null && !context.hasPermission(Item.EXTENDED_READ)) {
                return result.includeEmptyValue();
            }

            MultiOrgGitHubAppCredentials credentials = findCredentialById(credentialId);
            if (credentials != null) {
                List<String> organizations = credentials.getAvailableOrganizations();
                for (String org : organizations) {
                    result.add(org);
                }
            }

            return result;
        }

        @POST
        public FormValidation doTestMultiOrgConnection(
                @QueryParameter("appID") final String appID,
                @QueryParameter("privateKey") final String privateKey,
                @QueryParameter("apiUri") final String apiUri) {

            // Validate required parameters
            if (appID == null || appID.trim().isEmpty()) {
                return FormValidation.error("App ID is required");
            }
            if (privateKey == null || privateKey.trim().isEmpty()) {
                return FormValidation.error("Private key is required");
            }

            try {
                MultiOrgGitHubAppCredentials gitHubAppCredential = new MultiOrgGitHubAppCredentials(
                        CredentialsScope.GLOBAL, "test-id-not-being-saved", null, appID, Secret.fromString(privateKey));
                gitHubAppCredential.setApiUri(apiUri);

                // Force refresh to get the latest organizations
                gitHubAppCredential.forceRefreshOrganizations();
                List<String> organizations = gitHubAppCredential.getAvailableOrganizations();

                if (organizations.isEmpty()) {
                    return FormValidation.warning("GitHub App is not installed to any organizations. "
                            + "Please install the GitHub App in at least one organization.");
                }

                // Test connection with the first available organization
                String testOrg = organizations.get(0);
                GitHub connect = Connector.connect(apiUri, gitHubAppCredential.forOrganization(testOrg));
                try {
                    int remainingRate = connect.getRateLimit().getRemaining();
                    String orgList = String.join(", ", organizations);
                    String message =
                            "Success! Available organizations: " + orgList + ". Remaining rate limit: " + remainingRate;
                    return FormValidation.ok(message);
                } finally {
                    Connector.release(connect);
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Test connection failed for GitHub App ID " + appID, e);
                return FormValidation.error(
                        e, "Failed to authenticate with GitHub App ID " + appID + ": " + e.getMessage());
            }
        }

        private MultiOrgGitHubAppCredentials findCredentialById(String id) {
            if (id == null || id.isEmpty()) {
                return null;
            }

            // Use CredentialsMatchers to find the credential by ID
            return CredentialsMatchers.firstOrNull(
                    CredentialsProvider.lookupCredentials(
                            MultiOrgGitHubAppCredentials.class, Jenkins.get(), ACL.SYSTEM, Collections.emptyList()),
                    CredentialsMatchers.withId(id));
        }
    }
}
