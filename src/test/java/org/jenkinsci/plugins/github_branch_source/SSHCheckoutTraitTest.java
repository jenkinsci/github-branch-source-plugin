package org.jenkinsci.plugins.github_branch_source;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeThat;

import hudson.model.Item;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.AuthorizationStrategy;
import hudson.security.SecurityRealm;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.MockFolder;

public class SSHCheckoutTraitTest {
    @ClassRule
    public static JenkinsRule j = new JenkinsRule();

    @Test
    public void given__legacyConfig__when__creatingTrait__then__convertedToModern() throws Exception {
        assertThat(new SSHCheckoutTrait(GitHubSCMSource.DescriptorImpl.ANONYMOUS).getCredentialsId(), is(nullValue()));
    }

    @Test
    public void given__sshCheckoutWithCredentials__when__decorating__then__credentialsApplied_with_repositoryUrl()
            throws Exception {
        SSHCheckoutTrait instance = new SSHCheckoutTrait("keyId");
        GitHubSCMSource source = new GitHubSCMSource("", "", "https://github.com/example/does-not-exist", true);
        source.setCredentialsId("scanId");
        GitHubSCMBuilder probe = new GitHubSCMBuilder(source, new BranchSCMHead("master"), null);
        assumeThat(probe.credentialsId(), is("scanId"));
        instance.decorateBuilder(probe);
        assertThat(probe.credentialsId(), is("keyId"));
    }

    @Test
    public void given__sshCheckoutWithCredentials__when__decorating__then__credentialsApplied() throws Exception {
        SSHCheckoutTrait instance = new SSHCheckoutTrait("keyId");
        GitHubSCMSource source = new GitHubSCMSource("example", "does-not-exist");
        source.setApiUri("https://github.test");
        source.setCredentialsId("scanId");
        GitHubSCMBuilder probe = new GitHubSCMBuilder(source, new BranchSCMHead("master"), null);
        assumeThat(probe.credentialsId(), is("scanId"));
        instance.decorateBuilder(probe);
        assertThat(probe.credentialsId(), is("keyId"));
    }

    @Test
    public void given__sshCheckoutWithAgentKey__when__decorating__then__useAgentKeyApplied_with_repositoryUrl()
            throws Exception {
        SSHCheckoutTrait instance = new SSHCheckoutTrait(null);
        GitHubSCMSource source = new GitHubSCMSource("", "", "https://github.com/example/does-not-exist", true);
        source.setCredentialsId("scanId");
        GitHubSCMBuilder probe = new GitHubSCMBuilder(source, new BranchSCMHead("master"), null);
        assumeThat(probe.credentialsId(), is("scanId"));
        instance.decorateBuilder(probe);
        assertThat(probe.credentialsId(), is(nullValue()));
    }

    @Test
    public void given__sshCheckoutWithAgentKey__when__decorating__then__useAgentKeyApplied() throws Exception {
        SSHCheckoutTrait instance = new SSHCheckoutTrait(null);
        GitHubSCMSource source = new GitHubSCMSource("example", "does-not-exist");
        source.setApiUri("https://github.test");
        source.setCredentialsId("scanId");
        GitHubSCMBuilder probe = new GitHubSCMBuilder(source, new BranchSCMHead("master"), null);
        assumeThat(probe.credentialsId(), is("scanId"));
        instance.decorateBuilder(probe);
        assertThat(probe.credentialsId(), is(nullValue()));
    }

    @Test
    public void given__descriptor__when__displayingCredentials__then__contractEnforced() throws Exception {
        final SSHCheckoutTrait.DescriptorImpl d = j.jenkins.getDescriptorByType(SSHCheckoutTrait.DescriptorImpl.class);
        final MockFolder dummy = j.createFolder("dummy");
        SecurityRealm realm = j.jenkins.getSecurityRealm();
        AuthorizationStrategy strategy = j.jenkins.getAuthorizationStrategy();
        try {
            j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
            MockAuthorizationStrategy mockStrategy = new MockAuthorizationStrategy();
            mockStrategy.grant(Jenkins.ADMINISTER).onRoot().to("admin");
            mockStrategy.grant(Item.CONFIGURE).onItems(dummy).to("bob");
            mockStrategy.grant(Item.EXTENDED_READ).onItems(dummy).to("jim");
            j.jenkins.setAuthorizationStrategy(mockStrategy);
            try (ACLContext ctx = ACL.as(User.getById("admin", true).impersonate())) {
                ListBoxModel rsp = d.doFillCredentialsIdItems(dummy, "", "does-not-exist");
                assertThat("Expecting only the provided value so that form config unchanged", rsp, hasSize(1));
                assertThat(
                        "Expecting only the provided value so that form config unchanged",
                        rsp.get(0).value,
                        is("does-not-exist"));
                rsp = d.doFillCredentialsIdItems(null, "", "does-not-exist");
                assertThat("Expecting just the empty entry", rsp, hasSize(1));
                assertThat("Expecting just the empty entry", rsp.get(0).value, is(""));
            }
            try (ACLContext ctx = ACL.as(User.getById("bob", true).impersonate())) {
                ListBoxModel rsp = d.doFillCredentialsIdItems(dummy, "", "does-not-exist");
                assertThat("Expecting just the empty entry", rsp, hasSize(1));
                assertThat("Expecting just the empty entry", rsp.get(0).value, is(""));
                rsp = d.doFillCredentialsIdItems(null, "", "does-not-exist");
                assertThat("Expecting only the provided value so that form config unchanged", rsp, hasSize(1));
                assertThat(
                        "Expecting only the provided value so that form config unchanged",
                        rsp.get(0).value,
                        is("does-not-exist"));
            }
            try (ACLContext ctx = ACL.as(User.getById("jim", true).impersonate())) {
                ListBoxModel rsp = d.doFillCredentialsIdItems(dummy, "", "does-not-exist");
                assertThat("Expecting just the empty entry", rsp, hasSize(1));
                assertThat("Expecting just the empty entry", rsp.get(0).value, is(""));
            }
            try (ACLContext ctx = ACL.as(User.getById("sue", true).impersonate())) {
                ListBoxModel rsp = d.doFillCredentialsIdItems(dummy, "", "does-not-exist");
                assertThat("Expecting only the provided value so that form config unchanged", rsp, hasSize(1));
                assertThat(
                        "Expecting only the provided value so that form config unchanged",
                        rsp.get(0).value,
                        is("does-not-exist"));
            }
        } finally {
            j.jenkins.setSecurityRealm(realm);
            j.jenkins.setAuthorizationStrategy(strategy);
            j.jenkins.remove(dummy);
        }
    }
}
