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
import java.util.List;
import jenkins.security.SlaveToMasterCallable;
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

    @NonNull String actualApiUri() {
        return Util.fixEmpty(apiUri) == null ? "https://api.github.com" : apiUri;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Secret getPassword() {
        long now = System.currentTimeMillis();
        String appInstallationToken;
        if (cachedToken != null && now - tokenCacheTime < JwtHelper.VALIDITY_MS /* extra buffer */ / 2) {
            appInstallationToken = cachedToken;
        } else {
            appInstallationToken = generateAppInstallationToken(appID, privateKey.getPlainText(), actualApiUri(), owner);
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
     * Ensures that the credentials state as serialized via Remoting to an agent calls back to the controller.
     * Benefits:
     * <ul>
     * <li>The agent never needs to have access to the plaintext private key.
     * <li>We can avoid the considerable amount of class loading associated with the JWT library, Jackson data binding, Bouncy Castle, etc.
     * <li>The agent need not be able to contact GitHub.
     * </ul>
     * Drawbacks:
     * <ul>
     * <li>There is no caching, so every access requires GitHub API traffic as well as Remoting traffic.
     * </ul>
     * @see CredentialsSnapshotTaker
     */
    private Object writeReplace() {
        if (/* XStream */Channel.current() == null) {
            return this;
        }
        return new AgentSide(this);
     }

    private static final class AgentSide extends BaseStandardCredentials implements StandardUsernamePasswordCredentials {

        static final String SEP = "%%%";

        private final String data;
        private transient Channel ch;

        AgentSide(GitHubAppCredentials onMaster) {
            super(onMaster.getScope(), onMaster.getId(), onMaster.getDescription());
            data = Secret.fromString(onMaster.appID + SEP + onMaster.privateKey.getPlainText() + SEP + onMaster.actualApiUri() + SEP + onMaster.owner).getEncryptedValue();
        }

        private Object readResolve() {
            ch = Channel.currentOrFail();
            return this;
        }

        @Override
        public String getUsername() {
            try {
                return ch.call(new GetUsername(data));
            } catch (IOException | InterruptedException x) {
                throw new RuntimeException(x);
            }
        }

        @Override
        public Secret getPassword() {
            try {
                return Secret.fromString(ch.call(new GetPassword(data)));
            } catch (IOException | InterruptedException x) {
                throw new RuntimeException(x);
            }
        }

        private static final class GetUsername extends SlaveToMasterCallable<String, RuntimeException> {

            private final String data;

            GetUsername(String data) {
                this.data = data;
            }

            @Override
            public String call() throws RuntimeException {
                return Secret.fromString(data).getPlainText().split(SEP)[0];
            }

        }

        private static final class GetPassword extends SlaveToMasterCallable<String, RuntimeException> {

            private final String data;

            GetPassword(String data) {
                this.data = data;
            }

            @Override
            public String call() throws RuntimeException {
                String[] fields = Secret.fromString(data).getPlainText().split(SEP);
                return generateAppInstallationToken(fields[0], fields[1], fields[2], fields[3]);
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
