package org.jenkinsci.plugins.github_branch_source.app_credentials;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.util.FormValidation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.github_branch_source.GitHubAppCredentials;
import org.jenkinsci.plugins.github_branch_source.GitHubAppUsageContext;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * This mode only allows the {@link GitHubAppCredentials} to generate an installation token for the
 * specified owner and list of repositories.
 *
 * <p>The context of where the credentials are used in Jenkins is irrelevant.
 *
 * <p>Specifying an empty list of repositories allows the credential to generate an installation
 * token that can access any repository available to the owner.
 */
public class AccessSpecifiedRepositories extends RepositoryAccessStrategy {

    private final @CheckForNull String owner;
    private final @NonNull List<String> repositories;

    @DataBoundConstructor
    public AccessSpecifiedRepositories(@CheckForNull String owner, @NonNull List<String> repositories) {
        this.owner = Util.fixEmptyAndTrim(owner);
        this.repositories = new ArrayList<>(repositories == null ? List.of() : repositories);
    }

    public String getOwner() {
        return owner;
    }

    public List<String> getRepositories() {
        return repositories;
    }

    @Restricted(NoExternalUse.class)
    public String getRepositoriesForJelly() {
        return String.join("\n", repositories);
    }

    @Override
    public AccessibleRepositories forContext(final GitHubAppUsageContext context) {
        return new AccessibleRepositories(owner, repositories);
    }

    @Symbol("specificRepositories")
    @Extension
    public static class DescriptorImpl extends RepositoryAccessStrategyDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.AccessSpecifiedRepositories_displayName();
        }

        // TODO: JENKINS-27901
        @Override
        public AccessSpecifiedRepositories newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            String owner = formData.getString("owner");
            String repositoryField = formData.getString("repositories");
            List<String> repositories = parseRepositories(repositoryField);
            return new AccessSpecifiedRepositories(owner, repositories);
        }

        public FormValidation doCheckRepositories(@QueryParameter String repositories) {
            if (parseRepositories(repositories).isEmpty()) {
                return FormValidation.warning(Messages.AccessSpecifiedRepositories_noRespositories());
            }
            return FormValidation.ok();
        }

        private static List<String> parseRepositories(String repositoryField) {
            repositoryField = Util.fixEmptyAndTrim(repositoryField);
            if (repositoryField == null) {
                return Collections.emptyList();
            }
            return Stream.of(repositoryField.split("\r?\n"))
                    .map(Util::fixEmptyAndTrim)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }
    }
}
