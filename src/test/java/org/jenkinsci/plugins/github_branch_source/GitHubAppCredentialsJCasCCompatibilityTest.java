package org.jenkinsci.plugins.github_branch_source;

import static io.jenkins.plugins.casc.misc.Util.toStringFromYamlFile;
import static io.jenkins.plugins.casc.misc.Util.toYamlString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.jvnet.hudson.test.JenkinsMatchers.hasPlainText;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.casc.CredentialsRootConfigurator;
import com.cloudbees.plugins.credentials.domains.DomainCredentials;
import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.ConfiguratorRegistry;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.casc.misc.junit.jupiter.WithJenkinsConfiguredWithCode;
import io.jenkins.plugins.casc.model.CNode;
import java.util.List;
import java.util.Objects;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.github_branch_source.app_credentials.AccessInferredOwner;
import org.jenkinsci.plugins.github_branch_source.app_credentials.AccessInferredRepository;
import org.jenkinsci.plugins.github_branch_source.app_credentials.AccessSpecifiedRepositories;
import org.jenkinsci.plugins.github_branch_source.app_credentials.DefaultPermissionsStrategy;
import org.jenkinsci.plugins.github_branch_source.app_credentials.RepositoryAccessStrategy;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.SetEnvironmentVariable;

@WithJenkinsConfiguredWithCode
@SetEnvironmentVariable(key = "GITHUB_APP_KEY", value = GitHubAppCredentialsJCasCCompatibilityTest.GITHUB_APP_KEY)
class GitHubAppCredentialsJCasCCompatibilityTest {

    protected static final String GITHUB_APP_KEY = "SomeString";

    @Test
    @ConfiguredWithCode("github-app-jcasc-minimal.yaml")
    void should_support_configuration_as_code(JenkinsConfiguredWithCodeRule j) {
        List<DomainCredentials> domainCredentials =
                SystemCredentialsProvider.getInstance().getDomainCredentials();

        assertThat(domainCredentials.size(), is(1));
        List<Credentials> credentials = domainCredentials.get(0).getCredentials();
        assertThat(credentials.size(), is(7));

        assertGitHubAppCredential(
                credentials.get(0), "github-app", "GitHub app 1111", new AccessSpecifiedRepositories(null, List.of()));
        assertGitHubAppCredential(
                credentials.get(1), "old-owner", "", new AccessSpecifiedRepositories("test", List.of()));
        assertGitHubAppCredential(
                credentials.get(2), "new-specific-empty", "", new AccessSpecifiedRepositories(null, List.of()));
        assertGitHubAppCredential(
                credentials.get(3), "new-specific-owner", "", new AccessSpecifiedRepositories("test", List.of()));
        assertGitHubAppCredential(
                credentials.get(4),
                "new-specific-repos",
                "",
                new AccessSpecifiedRepositories("test", List.of("repo1", "repo2")));
        assertGitHubAppCredential(
                credentials.get(5),
                "new-infer-owner",
                "",
                new AccessInferredOwner(),
                DefaultPermissionsStrategy.CONTENTS_READ);
        assertGitHubAppCredential(
                credentials.get(6),
                "new-infer-repo",
                "",
                new AccessInferredRepository(),
                DefaultPermissionsStrategy.CONTENTS_WRITE);
    }

    @Test
    @ConfiguredWithCode("github-app-jcasc-minimal.yaml")
    void should_support_configuration_export(JenkinsConfiguredWithCodeRule j) throws Exception {
        CNode credentials = getCredentials();

        String exported = toYamlString(credentials)
                // replace secret with a constant value
                .replaceAll("privateKey: .*", "privateKey: \"some-secret-value\"");

        String expected = toStringFromYamlFile(this, "github-app-jcasc-minimal-expected-export.yaml");

        // TODO: CasC plugin incorrectly oversimplifies the YAML export for new-specific-empty by
        // fully removing the repositoryAccessStrategy configuration because its inner
        // configuration is all empty, but that means it no longer round-trips.
        assertThat(exported, is(expected));
    }

    private CNode getCredentials() throws Exception {
        CredentialsRootConfigurator root = Jenkins.get()
                .getExtensionList(CredentialsRootConfigurator.class)
                .get(0);

        ConfiguratorRegistry registry = ConfiguratorRegistry.get();
        ConfigurationContext context = new ConfigurationContext(registry);
        return Objects.requireNonNull(root.describe(root.getTargetComponent(context), context))
                .asMapping();
    }

    private static void assertGitHubAppCredential(
            Credentials credentials, String id, String description, RepositoryAccessStrategy repoStrategy) {
        assertGitHubAppCredential(credentials, id, description, repoStrategy, DefaultPermissionsStrategy.INHERIT_ALL);
    }

    private static void assertGitHubAppCredential(
            Credentials credentials,
            String id,
            String description,
            RepositoryAccessStrategy repoStrategy,
            DefaultPermissionsStrategy permissionsStrategy) {
        assertThat(credentials, instanceOf(GitHubAppCredentials.class));
        GitHubAppCredentials appCredentials = (GitHubAppCredentials) credentials;
        assertThat(appCredentials.getAppID(), is("1111"));
        assertThat(appCredentials.getDescription(), is(description));
        assertThat(appCredentials.getId(), is(id));
        assertThat(appCredentials.getPrivateKey(), hasPlainText(GITHUB_APP_KEY));
        assertThat(appCredentials.getDefaultPermissionsStrategy(), is(permissionsStrategy));
        var actualRepoStrategy = appCredentials.getRepositoryAccessStrategy();
        assertThat(actualRepoStrategy.getClass(), is(repoStrategy.getClass()));
        if (actualRepoStrategy instanceof AccessSpecifiedRepositories actualRepos) {
            var expectedRepos = (AccessSpecifiedRepositories) repoStrategy;
            assertThat(actualRepos.getOwner(), is(expectedRepos.getOwner()));
            assertThat(actualRepos.getRepositories(), is(expectedRepos.getRepositories()));
        }
    }
}
