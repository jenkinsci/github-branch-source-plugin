package org.jenkinsci.plugins.github_branch_source;

import static io.jenkins.plugins.casc.misc.Util.toStringFromYamlFile;
import static io.jenkins.plugins.casc.misc.Util.toYamlString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.jvnet.hudson.test.JenkinsMatchers.hasPlainText;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.casc.CredentialsRootConfigurator;
import com.cloudbees.plugins.credentials.domains.DomainCredentials;
import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.ConfiguratorRegistry;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.EnvVarsRule;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.casc.model.CNode;
import io.jenkins.plugins.casc.model.Mapping;
import io.jenkins.plugins.casc.model.Sequence;
import java.util.List;
import java.util.Objects;
import jenkins.model.Jenkins;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class GitHubAppCredentialsJCasCCompatibilityTest {

    @ConfiguredWithCode("github-app-jcasc-minimal.yaml")
    public static JenkinsConfiguredWithCodeRule j = new JenkinsConfiguredWithCodeRule();

    private static final String GITHUB_APP_KEY = "SomeString";

    @ClassRule
    public static RuleChain chain = RuleChain.outerRule(new EnvVarsRule().set("GITHUB_APP_KEY", GITHUB_APP_KEY))
            .around(j);

    @Test
    public void should_support_configuration_as_code() {
        List<DomainCredentials> domainCredentials =
                SystemCredentialsProvider.getInstance().getDomainCredentials();

        assertThat(domainCredentials.size(), is(1));
        List<Credentials> credentials = domainCredentials.get(0).getCredentials();
        assertThat(credentials.size(), is(1));

        Credentials credential = credentials.get(0);
        assertThat(credential, instanceOf(GitHubAppCredentials.class));
        GitHubAppCredentials gitHubAppCredentials = (GitHubAppCredentials) credential;

        assertThat(gitHubAppCredentials.getAppID(), is("1111"));
        assertThat(gitHubAppCredentials.getDescription(), is("GitHub app 1111"));
        assertThat(gitHubAppCredentials.getId(), is("github-app"));
        assertThat(gitHubAppCredentials.getPrivateKey(), hasPlainText(GITHUB_APP_KEY));
    }

    @Test
    public void should_support_configuration_export() throws Exception {
        Sequence credentials = getCredentials();
        CNode githubApp = credentials.get(0).asMapping().get("gitHubApp");

        String exported = toYamlString(githubApp)
                // replace secret with a constant value
                .replaceAll("privateKey: .*", "privateKey: \"some-secret-value\"");

        String expected = toStringFromYamlFile(this, "github-app-jcasc-minimal-expected-export.yaml");

        assertThat(exported, is(expected));
    }

    private Sequence getCredentials() throws Exception {
        CredentialsRootConfigurator root = Jenkins.get()
                .getExtensionList(CredentialsRootConfigurator.class)
                .get(0);

        ConfiguratorRegistry registry = ConfiguratorRegistry.get();
        ConfigurationContext context = new ConfigurationContext(registry);
        Mapping configNode = Objects.requireNonNull(root.describe(root.getTargetComponent(context), context))
                .asMapping();
        Mapping domainCredentials = configNode
                .get("system")
                .asMapping()
                .get("domainCredentials")
                .asSequence()
                .get(0)
                .asMapping();
        return domainCredentials.get("credentials").asSequence();
    }
}
