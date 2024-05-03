package org.jenkinsci.plugins.github_branch_source;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import java.util.List;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlInput;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class GitHubConfigurationTest {

    @Rule
    public final JenkinsRule j = new JenkinsRule();

    private GitHubConfiguration globalConfig;

    @Before
    public void setUp() {
        globalConfig = GitHubConfiguration.all().getInstance(GitHubConfiguration.class);
    }

    @Test
    public void configRoundTripTestNoChanges() throws Exception {
        globalConfig.addEndpoint(new Endpoint("https://www.example.com/api/v3", "example.com"));
        List<Endpoint> endpoints = globalConfig.getEndpoints();
        assertThat(endpoints, is(hasSize(1)));
        Endpoint endpoint = endpoints.get(0);
        assertThat(endpoint.getApiUri(), is("https://www.example.com/api/v3"));
        assertThat(endpoint.getName(), is("example.com"));

        // Submit the global configuration page with no changes
        j.configRoundtrip();

        endpoints = globalConfig.getEndpoints();
        assertThat(endpoints, is(hasSize(1)));
        endpoint = endpoints.get(0);
        assertThat(endpoint.getApiUri(), is("https://www.example.com/api/v3"));
        assertThat(endpoint.getName(), is("example.com"));
    }

    @Test
    public void configDeleteEndpoint() throws Exception {
        globalConfig.addEndpoint(new Endpoint("https://www.example.com/api/v3", "example.com"));
        List<Endpoint> endpoints = globalConfig.getEndpoints();
        assertThat(endpoints, is(hasSize(1)));

        j.configRoundtrip();

        // delete the endpoint in the UI
        HtmlForm config = j.createWebClient().goTo("configure").getFormByName("config");
        HtmlInput input = config.getInputByName("_.apiUri");
        input.getParentNode().removeChild(input);
        input = config.getInputByName("_.name");
        input.getParentNode().removeChild(input);
        j.submit(config);

        // there should be no endpoints now
        endpoints = globalConfig.getEndpoints();
        assertThat(endpoints, is(hasSize(0)));
    }
}
