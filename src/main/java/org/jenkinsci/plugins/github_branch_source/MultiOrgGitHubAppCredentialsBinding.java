package org.jenkinsci.plugins.github_branch_source;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.credentialsbinding.BindingDescriptor;
import org.jenkinsci.plugins.credentialsbinding.MultiBinding;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

/**
 * Credentials binding for Multi-Organization GitHub App Credentials.
 *
 * <p>This binding allows pipeline scripts to access organization-specific GitHub tokens
 * from Multi-Organization GitHub App Credentials. It provides both a general token
 * variable and organization-specific token variables.</p>
 *
 * <p>Usage in pipeline:</p>
 * <pre>{@code
 * // Automatic mode - provides tokens for all organizations
 * withCredentials([
 *   multiOrgGitHubApp(credentialsId: 'my-multi-org-app')
 * ]) {
 *   // Available variables:
 *   // $GITHUB_ORGS - comma-separated list of organizations
 *   // $GITHUB_TOKEN_<orgname> - token for each organization
 *   sh 'git clone https://$GITHUB_TOKEN_MYORG@github.com/myorg/repo.git'
 * }
 *
 * // Manual mode - specify organization and token variable name
 * withCredentials([
 *   multiOrgGitHubApp(credentialsId: 'my-multi-org-app',
 *                     tokenVariable: 'GITHUB_TOKEN',
 *                     orgName: 'myorg')
 * ]) {
 *   // Use $GITHUB_TOKEN for the specified organization
 *   sh 'git clone https://$GITHUB_TOKEN@github.com/myorg/repo.git'
 * }
 * }</pre>
 *
 * <p>Available environment variables:
 * <br><strong>Automatic mode (no parameters):</strong>
 * <br>- {@code $GITHUB_ORGS} - Comma-separated list of available organizations
 * <br>- {@code $GITHUB_TOKEN_<orgname>} - Token for each organization
 * <br><strong>Manual mode (with parameters):</strong>
 * <br>- {@code $<tokenVariable>} - Token for the specified organization
 * <br>- {@code $GITHUB_ORGS} - Comma-separated list of available organizations
 * </p>
 *
 * @since 2.15.0
 */
public class MultiOrgGitHubAppCredentialsBinding extends MultiBinding<MultiOrgGitHubAppCredentials> {

    private static final Logger LOGGER = Logger.getLogger(MultiOrgGitHubAppCredentialsBinding.class.getName());

    /**
     * The variable name for the GitHub token (optional)
     */
    @CheckForNull
    private final String tokenVariable;

    /**
     * The organization name to get token for (optional)
     */
    @CheckForNull
    private final String orgName;

    /**
     * Constructor.
     *
     * @param tokenVariable the variable name for the GitHub token (optional)
     * @param orgName the organization name to get token for (optional)
     * @param credentialsId the credentials ID
     */
    @DataBoundConstructor
    public MultiOrgGitHubAppCredentialsBinding(
            @CheckForNull String tokenVariable, @CheckForNull String orgName, @NonNull String credentialsId) {
        super(credentialsId);

        // Input validation
        if (credentialsId == null || credentialsId.trim().isEmpty()) {
            throw new IllegalArgumentException("Credentials ID cannot be null or empty");
        }

        // Validate that if either tokenVariable or orgName is provided, both must be provided
        boolean hasTokenVar = tokenVariable != null && !tokenVariable.trim().isEmpty();
        boolean hasOrgName = orgName != null && !orgName.trim().isEmpty();

        if (hasTokenVar != hasOrgName) {
            throw new IllegalArgumentException(
                    "Both tokenVariable and orgName must be provided together for manual mode, or both must be null/empty for automatic mode");
        }

        this.tokenVariable = tokenVariable;
        this.orgName = orgName;
    }

    /**
     * Returns the variable name for the GitHub token.
     *
     * @return the token variable name or null if automatic mode
     */
    @CheckForNull
    public String getTokenVariable() {
        return tokenVariable;
    }

    /**
     * Returns the organization name to get token for.
     *
     * @return the organization name or null if automatic mode
     */
    @CheckForNull
    public String getOrgName() {
        return orgName;
    }

    /**
     * Checks if this binding is in automatic mode (no parameters specified).
     *
     * @return true if in automatic mode
     */
    public boolean isAutomaticMode() {
        return (tokenVariable == null || tokenVariable.trim().isEmpty())
                && (orgName == null || orgName.trim().isEmpty());
    }

    @Override
    protected Class<MultiOrgGitHubAppCredentials> type() {
        return MultiOrgGitHubAppCredentials.class;
    }

    @Override
    public MultiEnvironment bind(@NonNull Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener)
            throws IOException, InterruptedException {

        MultiOrgGitHubAppCredentials credentials = getCredentials(build);
        if (credentials == null) {
            throw new IOException("Could not find Multi-Org GitHub App credentials: " + getCredentialsId());
        }

        // Get available organizations
        List<String> organizations = credentials.getAvailableOrganizations();
        if (organizations.isEmpty()) {
            listener.getLogger()
                    .println("Warning: No organizations found for GitHub App. "
                            + "Make sure the app is installed to at least one organization.");
        }

        Map<String, String> secretValues = new HashMap<>();
        Map<String, String> publicValues = new HashMap<>();

        try {
            if (isAutomaticMode()) {
                // Automatic mode: provide tokens for all organizations with standard naming
                listener.getLogger()
                        .printf(
                                "Binding Multi-Org GitHub App credentials in automatic mode for %d organizations%n",
                                organizations.size());

                for (String org : organizations) {
                    try {
                        String orgToken =
                                credentials.forOrganization(org).getPassword().getPlainText();
                        String tokenVarName = "GITHUB_TOKEN_" + sanitizeOrgName(org);
                        secretValues.put(tokenVarName, orgToken);

                        listener.getLogger().printf("Set %s for organization: %s%n", tokenVarName, org);
                    } catch (RuntimeException e) {
                        listener.getLogger()
                                .printf("Warning: Failed to get token for organization %s: %s%n", org, e.getMessage());
                        LOGGER.log(Level.WARNING, "Failed to get token for organization " + org, e);
                    }
                }

                // Set list of available organizations (not sensitive)
                String orgsListVar = "GITHUB_ORGS";
                publicValues.put(orgsListVar, String.join(",", organizations));
                listener.getLogger()
                        .printf("Set %s with organizations: %s%n", orgsListVar, String.join(", ", organizations));

            } else {
                // Manual mode: use specified organization and variable name
                listener.getLogger().printf("Binding Multi-Org GitHub App credentials in manual mode%n");

                // Validate that both tokenVariable and orgName are provided in manual mode
                if (tokenVariable == null || tokenVariable.trim().isEmpty()) {
                    throw new IOException("Token variable name is required when orgName is specified");
                }
                if (orgName == null || orgName.trim().isEmpty()) {
                    throw new IOException("Organization name is required when token variable is specified");
                }

                // Verify the organization exists
                if (!organizations.contains(orgName)) {
                    throw new IOException("Organization '" + orgName + "' not found in available organizations: "
                            + String.join(", ", organizations));
                }

                // Set token for the specified organization
                try {
                    String orgToken =
                            credentials.forOrganization(orgName).getPassword().getPlainText();
                    secretValues.put(tokenVariable, orgToken);

                    listener.getLogger().printf("Set %s for organization: %s%n", tokenVariable, orgName);
                } catch (RuntimeException e) {
                    throw new IOException("Failed to get token for organization " + orgName + ": " + e.getMessage(), e);
                }

                // Set list of available organizations (not sensitive)
                String orgsListVar = "GITHUB_ORGS";
                publicValues.put(orgsListVar, String.join(",", organizations));
                listener.getLogger()
                        .printf("Set %s with organizations: %s%n", orgsListVar, String.join(", ", organizations));
            }

            return new MultiEnvironment(secretValues, publicValues);

        } catch (IOException e) {
            throw e; // Re-throw IOException as-is
        } catch (RuntimeException e) {
            throw new IOException("Failed to bind Multi-Org GitHub App credentials", e);
        }
    }

    @Override
    public Set<String> variables(@NonNull Run<?, ?> build) {
        try {
            MultiOrgGitHubAppCredentials credentials = getCredentials(build);

            Set<String> vars = new HashSet<>();
            vars.add("GITHUB_ORGS"); // Always add this

            if (isAutomaticMode()) {
                // In automatic mode, add variables for all organizations
                List<String> organizations = credentials.getAvailableOrganizations();
                for (String org : organizations) {
                    vars.add("GITHUB_TOKEN_" + sanitizeOrgName(org));
                }
            } else {
                // In manual mode, add only the specified token variable
                if (tokenVariable != null && !tokenVariable.trim().isEmpty()) {
                    vars.add(tokenVariable);
                }
            }

            return vars;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to get credential variables", e);
            Set<String> fallback = new HashSet<>();
            fallback.add("GITHUB_ORGS");
            if (tokenVariable != null && !tokenVariable.trim().isEmpty()) {
                fallback.add(tokenVariable);
            }
            return fallback;
        }
    }

    /**
     * Sanitizes organization name for use as environment variable suffix.
     * Replaces non-alphanumeric characters with underscores and converts to uppercase.
     *
     * @param orgName the organization name
     * @return sanitized name suitable for environment variable
     */
    private String sanitizeOrgName(String orgName) {
        return orgName.replaceAll("[^a-zA-Z0-9]", "_").toUpperCase();
    }

    /**
     * The descriptor for {@link MultiOrgGitHubAppCredentialsBinding}.
     */
    @Symbol("multiOrgGitHubApp")
    @Extension
    public static class DescriptorImpl extends BindingDescriptor<MultiOrgGitHubAppCredentials> {

        @Override
        public String getDisplayName() {
            return Messages.MultiOrgGitHubAppCredentialsBinding_displayName();
        }

        @Override
        protected Class<MultiOrgGitHubAppCredentials> type() {
            return MultiOrgGitHubAppCredentials.class;
        }

        /**
         * Form validation for the token variable field.
         *
         * @param value the token variable name
         * @param orgName the organization name (for context)
         * @return form validation result
         */
        @POST
        public FormValidation doCheckTokenVariable(@QueryParameter String value, @QueryParameter String orgName) {
            boolean hasOrgName = orgName != null && !orgName.trim().isEmpty();
            boolean hasTokenVariable = value != null && !value.trim().isEmpty();

            // If orgName is specified, tokenVariable is required
            if (hasOrgName && !hasTokenVariable) {
                return FormValidation.error("Token variable name is required when organization name is specified");
            }

            // If tokenVariable is specified, orgName is required
            if (hasTokenVariable && !hasOrgName) {
                return FormValidation.error("Organization name is required when token variable is specified");
            }

            // If tokenVariable is provided, validate format
            if (hasTokenVariable && !value.matches("[A-Z_][A-Z0-9_]*")) {
                return FormValidation.warning(
                        "Token variable name should follow environment variable naming conventions (uppercase, underscores)");
            }

            return FormValidation.ok();
        }

        /**
         * Form validation for the organization name field.
         *
         * @param value the organization name
         * @param tokenVariable the token variable name (for context)
         * @return form validation result
         */
        @POST
        public FormValidation doCheckOrgName(@QueryParameter String value, @QueryParameter String tokenVariable) {
            boolean hasTokenVariable =
                    tokenVariable != null && !tokenVariable.trim().isEmpty();
            boolean hasOrgName = value != null && !value.trim().isEmpty();

            // If tokenVariable is specified, orgName is required
            if (hasTokenVariable && !hasOrgName) {
                return FormValidation.error("Organization name is required when token variable is specified");
            }

            // If orgName is specified, tokenVariable is required
            if (hasOrgName && !hasTokenVariable) {
                return FormValidation.error("Token variable name is required when organization name is specified");
            }

            return FormValidation.ok();
        }

        /**
         * Fills the credentials dropdown with Multi-Org GitHub App credentials.
         *
         * @param context the context
         * @param credentialsId the current credentials ID
         * @return list box model with available credentials
         */
        @POST
        public ListBoxModel doFillCredentialsIdItems(
                @AncestorInPath Item context, @QueryParameter String credentialsId) {
            StandardListBoxModel result = new StandardListBoxModel();

            if (context == null && !Jenkins.get().hasPermission(Jenkins.ADMINISTER)
                    || context != null && !context.hasPermission(Item.EXTENDED_READ)) {
                return result.includeCurrentValue(credentialsId);
            }

            return result.includeEmptyValue()
                    .includeAs(ACL.SYSTEM, context, MultiOrgGitHubAppCredentials.class)
                    .includeCurrentValue(credentialsId);
        }

        /**
         * Tests the Multi-Org GitHub App credentials and shows available organizations.
         *
         * @param context the context
         * @param credentialsId the credentials ID to test
         * @return form validation result with organization information
         */
        @POST
        public FormValidation doTestCredentials(@AncestorInPath Item context, @QueryParameter String credentialsId) {
            if (context == null && !Jenkins.get().hasPermission(Jenkins.ADMINISTER)
                    || context != null && !context.hasPermission(Item.EXTENDED_READ)) {
                return FormValidation.ok();
            }

            if (credentialsId == null || credentialsId.trim().isEmpty()) {
                return FormValidation.error("Please select credentials");
            }

            try {
                MultiOrgGitHubAppCredentials credentials = findCredentialById(credentialsId);

                if (credentials == null) {
                    return FormValidation.error("Credentials not found");
                }

                List<String> organizations = credentials.getAvailableOrganizations();
                if (organizations.isEmpty()) {
                    return FormValidation.warning(
                            "No organizations found. Make sure the GitHub App is installed to at least one organization.");
                }

                String message = String.format(
                        "Success! Found %d organization(s): %s",
                        organizations.size(), String.join(", ", organizations));
                return FormValidation.ok(message);

            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to test Multi-Org GitHub App credentials: " + credentialsId, e);
                return FormValidation.error("Failed to test credentials: " + e.getMessage());
            }
        }

        /**
         * Helper method to find Multi-Org GitHub App credentials by ID.
         */
        private MultiOrgGitHubAppCredentials findCredentialById(String id) {
            if (id == null || id.isEmpty()) {
                return null;
            }

            return CredentialsMatchers.firstOrNull(
                    CredentialsProvider.lookupCredentials(
                            MultiOrgGitHubAppCredentials.class, Jenkins.get(), ACL.SYSTEM, Collections.emptyList()),
                    CredentialsMatchers.withId(id));
        }
    }
}
