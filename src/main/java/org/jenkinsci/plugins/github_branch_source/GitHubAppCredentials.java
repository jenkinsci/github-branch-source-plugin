package org.jenkinsci.plugins.github_branch_source;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsSnapshotTaker;
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
import java.util.List;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.github.GHApp;
import org.kohsuke.github.GHAppInstallation;
import org.kohsuke.github.GHAppInstallationToken;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import static org.jenkinsci.plugins.github_branch_source.GitHubSCMNavigator.DescriptorImpl.getPossibleApiUriItems;
import static org.jenkinsci.plugins.github_branch_source.JwtHelper.createJWT;

@SuppressFBWarnings(value = "SE_NO_SERIALVERSIONID", justification = "XStream")
public class GitHubAppCredentials extends BaseStandardCredentials implements StandardUsernamePasswordCredentials {

    private static final String ERROR_AUTHENTICATING_GITHUB_APP = "Couldn't authenticate with GitHub app ID %s";
    private static final String NOT_INSTALLED = ", has it been installed to your GitHub organisation / user?";

    private static final String ERROR_NOT_INSTALLED = ERROR_AUTHENTICATING_GITHUB_APP + NOT_INSTALLED;

    @NonNull
    private final String appID;

    @NonNull
    private final Secret privateKey;

    private String apiUri;

    private String owner;

    private transient String cachedToken;
    private transient long tokenCacheTime;

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
    static String generateAppInstallationToken(String appId, String appPrivateKey, String apiUrl, String owner) {
        try {
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
                        .orElseThrow(() -> new IllegalArgumentException(String.format(ERROR_NOT_INSTALLED, appId)));
            }

            GHAppInstallationToken appInstallationToken = appInstallation
                    .createToken(appInstallation.getPermissions())
                    .create();

            return appInstallationToken.getToken();
        } catch (IOException e) {
            throw new IllegalArgumentException(String.format(ERROR_AUTHENTICATING_GITHUB_APP, appId), e);
        }

    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Secret getPassword() {
        if (Util.fixEmpty(apiUri) == null) {
            apiUri = "https://api.github.com";
        }

        long now = System.currentTimeMillis();
        String appInstallationToken;
        if (cachedToken != null && now - tokenCacheTime < JwtHelper.VALIDITY_MS /* extra buffer */ / 2) {
            appInstallationToken = cachedToken;
        } else {
            appInstallationToken = generateAppInstallationToken(appID, privateKey.getPlainText(), apiUri, owner);
            cachedToken = appInstallationToken;
            tokenCacheTime = now;
        }

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
     * Ensures that the credentials state as serialized via Remoting to an agent includes fields which are {@code transient} for purposes of XStream.
     * This provides a ~2× performance improvement over reconstructing the object without that state,
     * in the normal case that {@link #cachedToken} is valid and will remain valid for the brief time that elapses before the agent calls {@link #getPassword}:
     * <ul>
     * <li>We do not need to make API calls to GitHub to obtain a new token.
     * <li>We can avoid the considerable amount of class loading associated with the JWT library, Jackson data binding, Bouncy Castle, etc.
     * </ul>
     * @see CredentialsSnapshotTaker
     */
    private Object writeReplace() {
        if (/* XStream */Channel.current() == null) {
            return this;
        }
        return new Replacer(this);
     }

     private static final class Replacer implements Serializable {

         private final CredentialsScope scope;
         private final String id;
         private final String description;
         private final String appID;
         private final Secret privateKey;
         private final String apiUri;
         private final String owner;
         private final String cachedToken;
         private final long tokenCacheTime;

         Replacer(GitHubAppCredentials onMaster) {
             scope = onMaster.getScope();
             id = onMaster.getId();
             description = onMaster.getDescription();
             appID = onMaster.appID;
             privateKey = onMaster.privateKey;
             apiUri = onMaster.apiUri;
             owner = onMaster.owner;
             cachedToken = onMaster.cachedToken;
             tokenCacheTime = onMaster.tokenCacheTime;
         }

         private Object readResolve() {
             GitHubAppCredentials clone = new GitHubAppCredentials(scope, id, description, appID, privateKey);
             clone.apiUri = apiUri;
             clone.owner = owner;
             clone.cachedToken = cachedToken;
             clone.tokenCacheTime = tokenCacheTime;
             return clone;
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
                return FormValidation.ok("Success, Remaining rate limit: " + connect.getRateLimit().getRemaining());
            } catch (Exception e) {
                return FormValidation.error(e, String.format(ERROR_AUTHENTICATING_GITHUB_APP, appID));
            }
        }
    }
}
