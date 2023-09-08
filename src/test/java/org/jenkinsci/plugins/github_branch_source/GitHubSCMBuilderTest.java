package org.jenkinsci.plugins.github_branch_source;

import static java.util.logging.Level.FINEST;
import static java.util.logging.Logger.getAnonymousLogger;
import static jenkins.plugins.git.AbstractGitSCMSource.SCMRevisionImpl;
import static jenkins.plugins.git.AbstractGitSCMSource.SpecificRevisionBuildChooser;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;

import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.Revision;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.browser.GithubWeb;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.BuildChooserSetting;
import hudson.util.LogTaskListener;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.plugins.git.GitSCMSourceDefaults;
import jenkins.plugins.git.MergeWithGitSCMExtension;
import jenkins.scm.api.SCMHeadOrigin;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import org.eclipse.jgit.transport.RemoteConfig;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mockito;

@RunWith(Parameterized.class)
public class GitHubSCMBuilderTest {
    @ClassRule
    public static JenkinsRule j = new JenkinsRule();

    private GitHubSCMSource source;
    private WorkflowMultiBranchProject owner;
    private boolean configuredByUrl;

    @Parameterized.Parameters
    public static Collection<Object[]> generateParams() {
        return Arrays.asList(new Object[] {true}, new Object[] {false});
    }

    public GitHubSCMBuilderTest(boolean configuredByUrl) {
        this.configuredByUrl = configuredByUrl;
    }

    public void createGitHubSCMSourceForTest(boolean configuredByUrl, String repoUrlToConfigure) throws Exception {
        if (configuredByUrl) {
            // Throw an exception if we don't supply a URL
            if (repoUrlToConfigure.isEmpty()) {
                throw new Exception("Must supply a URL when testing single-URL configured jobs");
            }
            source = new GitHubSCMSource("", "", repoUrlToConfigure, true);
        } else {
            source = new GitHubSCMSource("tester", "test-repo", null, false);
        }
        source.setOwner(owner);
    }

    @Before
    public void setUp() throws IOException {
        owner = j.createProject(WorkflowMultiBranchProject.class);
        Credentials userPasswordCredential = new UsernamePasswordCredentialsImpl(
                CredentialsScope.GLOBAL, "user-pass", null, "git-user", "git-secret");
        Credentials sshPrivateKeyCredential = new BasicSSHUserPrivateKey(
                CredentialsScope.GLOBAL,
                "user-key",
                "git",
                new BasicSSHUserPrivateKey.UsersPrivateKeySource(),
                null,
                null);
        SystemCredentialsProvider.getInstance()
                .setDomainCredentialsMap(Collections.singletonMap(
                        Domain.global(), Arrays.asList(userPasswordCredential, sshPrivateKeyCredential)));
    }

    @After
    public void tearDown() throws IOException, InterruptedException {
        SystemCredentialsProvider.getInstance().setDomainCredentialsMap(Collections.emptyMap());
        owner.delete();
    }

    @Test
    public void given__cloud_branch_rev_anon__when__build__then__scmBuilt() throws Exception {
        createGitHubSCMSourceForTest(false, null);
        BranchSCMHead head = new BranchSCMHead("test-branch");
        SCMRevisionImpl revision = new SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe");
        source.setCredentialsId(null);
        GitHubSCMBuilder instance = new GitHubSCMBuilder(source, head, revision);
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.head(), is(head));
        assertThat(instance.revision(), is(revision));
        assertThat(instance.refSpecs(), contains("+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"));
        assertThat(
                "expecting guess value until withGitHubRemote called",
                instance.remote(),
                is("https://github.com/tester/test-repo.git"));
        assertThat(instance.browser(), instanceOf(GithubWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://github.com/tester/test-repo"));

        instance.withGitHubRemote();
        assertThat(instance.remote(), is("https://github.com/tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(GithubWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://github.com/tester/test-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("https://github.com/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is(nullValue()));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://github.com/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(
                actual.getExtensions(),
                containsInAnyOrder(instanceOf(BuildChooserSetting.class), instanceOf(GitSCMSourceDefaults.class)));
        BuildChooserSetting chooser = getExtension(actual, BuildChooserSetting.class);
        assertThat(chooser.getBuildChooser(), instanceOf(SpecificRevisionBuildChooser.class));
        SpecificRevisionBuildChooser revChooser = (SpecificRevisionBuildChooser) chooser.getBuildChooser();
        Collection<Revision> revisions = revChooser.getCandidateRevisions(
                false,
                "test-branch",
                mock(GitClient.class),
                new LogTaskListener(getAnonymousLogger(), FINEST),
                null,
                null);
        assertThat(revisions, hasSize(1));
        assertThat(revisions.iterator().next().getSha1String(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
    }

    @Test
    public void given__cloud_branch_rev_userpass__when__build__then__scmBuilt() throws Exception {
        createGitHubSCMSourceForTest(false, null);
        BranchSCMHead head = new BranchSCMHead("test-branch");
        SCMRevisionImpl revision = new SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe");
        source.setCredentialsId("user-pass");
        GitHubSCMBuilder instance = new GitHubSCMBuilder(source, head, revision);
        assertThat(instance.credentialsId(), is("user-pass"));
        assertThat(instance.head(), is(head));
        assertThat(instance.revision(), is(revision));
        assertThat(instance.refSpecs(), contains("+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"));
        assertThat(
                "expecting guess value until withGitHubRemote called",
                instance.remote(),
                is("https://github.com/tester/test-repo.git"));
        assertThat(instance.browser(), instanceOf(GithubWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://github.com/tester/test-repo"));

        instance.withGitHubRemote();
        assertThat(instance.remote(), is("https://github.com/tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(GithubWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://github.com/tester/test-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("https://github.com/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-pass"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://github.com/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(
                actual.getExtensions(),
                containsInAnyOrder(instanceOf(BuildChooserSetting.class), instanceOf(GitSCMSourceDefaults.class)));
        BuildChooserSetting chooser = getExtension(actual, BuildChooserSetting.class);
        assertThat(chooser.getBuildChooser(), instanceOf(SpecificRevisionBuildChooser.class));
        SpecificRevisionBuildChooser revChooser = (SpecificRevisionBuildChooser) chooser.getBuildChooser();
        Collection<Revision> revisions = revChooser.getCandidateRevisions(
                false,
                "test-branch",
                mock(GitClient.class),
                new LogTaskListener(getAnonymousLogger(), FINEST),
                null,
                null);
        assertThat(revisions, hasSize(1));
        assertThat(revisions.iterator().next().getSha1String(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
    }

    @Test
    public void given__cloud_branch_rev_userkey__when__build__then__scmBuilt() throws Exception {
        createGitHubSCMSourceForTest(false, null);
        BranchSCMHead head = new BranchSCMHead("test-branch");
        SCMRevisionImpl revision = new SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe");
        source.setCredentialsId("user-key");
        GitHubSCMBuilder instance = new GitHubSCMBuilder(source, head, revision);
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is(head));
        assertThat(instance.revision(), is(revision));
        assertThat(instance.refSpecs(), contains("+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"));
        assertThat(
                "expecting guess value until withGitHubRemote called",
                instance.remote(),
                is("https://github.com/tester/test-repo.git"));
        assertThat(instance.browser(), instanceOf(GithubWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://github.com/tester/test-repo"));

        instance.withGitHubRemote();
        assertThat(instance.remote(), is("git@github.com:tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(GithubWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://github.com/tester/test-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("git@github.com:tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("git@github.com:tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(
                actual.getExtensions(),
                containsInAnyOrder(instanceOf(BuildChooserSetting.class), instanceOf(GitSCMSourceDefaults.class)));
        BuildChooserSetting chooser = getExtension(actual, BuildChooserSetting.class);
        assertThat(chooser.getBuildChooser(), instanceOf(SpecificRevisionBuildChooser.class));
        SpecificRevisionBuildChooser revChooser = (SpecificRevisionBuildChooser) chooser.getBuildChooser();
        Collection<Revision> revisions = revChooser.getCandidateRevisions(
                false,
                "test-branch",
                mock(GitClient.class),
                new LogTaskListener(getAnonymousLogger(), FINEST),
                null,
                null);
        assertThat(revisions, hasSize(1));
        assertThat(revisions.iterator().next().getSha1String(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
    }

    @Test
    public void given__cloud_branch_rev_anon_sshtrait_anon__when__build__then__scmBuilt() throws Exception {
        createGitHubSCMSourceForTest(false, null);
        BranchSCMHead head = new BranchSCMHead("test-branch");
        SCMRevisionImpl revision = new SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe");
        source.setCredentialsId(null);
        GitHubSCMBuilder instance = new GitHubSCMBuilder(source, head, revision);
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.head(), is(head));
        assertThat(instance.revision(), is(revision));
        assertThat(instance.refSpecs(), contains("+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"));
        assertThat(
                "expecting guess value until withGitHubRemote called",
                instance.remote(),
                is("https://github.com/tester/test-repo.git"));
        assertThat(instance.browser(), instanceOf(GithubWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://github.com/tester/test-repo"));

        instance.withGitHubRemote();
        assertThat(instance.remote(), is("https://github.com/tester/test-repo.git"));

        SSHCheckoutTrait sshTrait = new SSHCheckoutTrait(null);
        sshTrait.decorateBuilder(instance);

        GitSCM actual = instance.build();
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.remote(), is("git@github.com:tester/test-repo.git"));

        assertThat(actual.getBrowser(), instanceOf(GithubWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://github.com/tester/test-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("git@github.com:tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is(nullValue()));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("git@github.com:tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(
                actual.getExtensions(),
                containsInAnyOrder(instanceOf(BuildChooserSetting.class), instanceOf(GitSCMSourceDefaults.class)));
        BuildChooserSetting chooser = getExtension(actual, BuildChooserSetting.class);
        assertThat(chooser.getBuildChooser(), instanceOf(SpecificRevisionBuildChooser.class));
        SpecificRevisionBuildChooser revChooser = (SpecificRevisionBuildChooser) chooser.getBuildChooser();
        Collection<Revision> revisions = revChooser.getCandidateRevisions(
                false,
                "test-branch",
                mock(GitClient.class),
                new LogTaskListener(getAnonymousLogger(), FINEST),
                null,
                null);
        assertThat(revisions, hasSize(1));
        assertThat(revisions.iterator().next().getSha1String(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
    }

    @Test
    public void given__cloud_branch_rev_userpass_sshtrait_anon__when__build__then__scmBuilt() throws Exception {
        createGitHubSCMSourceForTest(false, null);
        BranchSCMHead head = new BranchSCMHead("test-branch");
        SCMRevisionImpl revision = new SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe");
        source.setCredentialsId("user-pass");
        GitHubSCMBuilder instance = new GitHubSCMBuilder(source, head, revision);
        assertThat(instance.credentialsId(), is("user-pass"));
        assertThat(instance.head(), is(head));
        assertThat(instance.revision(), is(revision));
        assertThat(instance.refSpecs(), contains("+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"));
        assertThat(
                "expecting guess value until withGitHubRemote called",
                instance.remote(),
                is("https://github.com/tester/test-repo.git"));
        assertThat(instance.browser(), instanceOf(GithubWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://github.com/tester/test-repo"));

        instance.withGitHubRemote();
        assertThat(instance.remote(), is("https://github.com/tester/test-repo.git"));

        SSHCheckoutTrait sshTrait = new SSHCheckoutTrait(null);
        sshTrait.decorateBuilder(instance);

        GitSCM actual = instance.build();
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.remote(), is("git@github.com:tester/test-repo.git"));

        assertThat(actual.getBrowser(), instanceOf(GithubWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://github.com/tester/test-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("git@github.com:tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is(nullValue()));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("git@github.com:tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(
                actual.getExtensions(),
                containsInAnyOrder(instanceOf(BuildChooserSetting.class), instanceOf(GitSCMSourceDefaults.class)));
        BuildChooserSetting chooser = getExtension(actual, BuildChooserSetting.class);
        assertThat(chooser.getBuildChooser(), instanceOf(SpecificRevisionBuildChooser.class));
        SpecificRevisionBuildChooser revChooser = (SpecificRevisionBuildChooser) chooser.getBuildChooser();
        Collection<Revision> revisions = revChooser.getCandidateRevisions(
                false,
                "test-branch",
                mock(GitClient.class),
                new LogTaskListener(getAnonymousLogger(), FINEST),
                null,
                null);
        assertThat(revisions, hasSize(1));
        assertThat(revisions.iterator().next().getSha1String(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
    }

    @Test
    public void given__cloud_branch_rev_userkey_sshtrait_anon__when__build__then__scmBuilt() throws Exception {
        createGitHubSCMSourceForTest(false, null);
        BranchSCMHead head = new BranchSCMHead("test-branch");
        SCMRevisionImpl revision = new SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe");
        source.setCredentialsId("user-key");
        GitHubSCMBuilder instance = new GitHubSCMBuilder(source, head, revision);
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is(head));
        assertThat(instance.revision(), is(revision));
        assertThat(instance.refSpecs(), contains("+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"));
        assertThat(
                "expecting guess value until withGitHubRemote called",
                instance.remote(),
                is("https://github.com/tester/test-repo.git"));
        assertThat(instance.browser(), instanceOf(GithubWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://github.com/tester/test-repo"));

        instance.withGitHubRemote();
        assertThat(instance.remote(), is("git@github.com:tester/test-repo.git"));

        SSHCheckoutTrait sshTrait = new SSHCheckoutTrait(null);
        sshTrait.decorateBuilder(instance);

        GitSCM actual = instance.build();
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.remote(), is("git@github.com:tester/test-repo.git"));

        assertThat(actual.getBrowser(), instanceOf(GithubWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://github.com/tester/test-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("git@github.com:tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is(nullValue()));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("git@github.com:tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(
                actual.getExtensions(),
                containsInAnyOrder(instanceOf(BuildChooserSetting.class), instanceOf(GitSCMSourceDefaults.class)));
        BuildChooserSetting chooser = getExtension(actual, BuildChooserSetting.class);
        assertThat(chooser.getBuildChooser(), instanceOf(SpecificRevisionBuildChooser.class));
        SpecificRevisionBuildChooser revChooser = (SpecificRevisionBuildChooser) chooser.getBuildChooser();
        Collection<Revision> revisions = revChooser.getCandidateRevisions(
                false,
                "test-branch",
                mock(GitClient.class),
                new LogTaskListener(getAnonymousLogger(), FINEST),
                null,
                null);
        assertThat(revisions, hasSize(1));
        assertThat(revisions.iterator().next().getSha1String(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
    }

    @Test
    public void given__cloud_branch_rev_anon_sshtrait_userkey__when__build__then__scmBuilt() throws Exception {
        createGitHubSCMSourceForTest(false, null);
        BranchSCMHead head = new BranchSCMHead("test-branch");
        SCMRevisionImpl revision = new SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe");
        source.setCredentialsId(null);
        GitHubSCMBuilder instance = new GitHubSCMBuilder(source, head, revision);
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.head(), is(head));
        assertThat(instance.revision(), is(revision));
        assertThat(instance.refSpecs(), contains("+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"));
        assertThat(
                "expecting guess value until withGitHubRemote called",
                instance.remote(),
                is("https://github.com/tester/test-repo.git"));
        assertThat(instance.browser(), instanceOf(GithubWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://github.com/tester/test-repo"));

        instance.withGitHubRemote();
        assertThat(instance.remote(), is("https://github.com/tester/test-repo.git"));

        SSHCheckoutTrait sshTrait = new SSHCheckoutTrait("user-key");
        sshTrait.decorateBuilder(instance);

        GitSCM actual = instance.build();
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.remote(), is("git@github.com:tester/test-repo.git"));

        assertThat(actual.getBrowser(), instanceOf(GithubWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://github.com/tester/test-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("git@github.com:tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("git@github.com:tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(
                actual.getExtensions(),
                containsInAnyOrder(instanceOf(BuildChooserSetting.class), instanceOf(GitSCMSourceDefaults.class)));
        BuildChooserSetting chooser = getExtension(actual, BuildChooserSetting.class);
        assertThat(chooser.getBuildChooser(), instanceOf(SpecificRevisionBuildChooser.class));
        SpecificRevisionBuildChooser revChooser = (SpecificRevisionBuildChooser) chooser.getBuildChooser();
        Collection<Revision> revisions = revChooser.getCandidateRevisions(
                false,
                "test-branch",
                mock(GitClient.class),
                new LogTaskListener(getAnonymousLogger(), FINEST),
                null,
                null);
        assertThat(revisions, hasSize(1));
        assertThat(revisions.iterator().next().getSha1String(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
    }

    @Test
    public void given__cloud_branch_rev_userpass_sshtrait_userkey__when__build__then__scmBuilt() throws Exception {
        createGitHubSCMSourceForTest(false, null);
        BranchSCMHead head = new BranchSCMHead("test-branch");
        SCMRevisionImpl revision = new SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe");
        source.setCredentialsId("user-pass");
        GitHubSCMBuilder instance = new GitHubSCMBuilder(source, head, revision);
        assertThat(instance.credentialsId(), is("user-pass"));
        assertThat(instance.head(), is(head));
        assertThat(instance.revision(), is(revision));
        assertThat(instance.refSpecs(), contains("+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"));
        assertThat(
                "expecting guess value until withGitHubRemote called",
                instance.remote(),
                is("https://github.com/tester/test-repo.git"));
        assertThat(instance.browser(), instanceOf(GithubWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://github.com/tester/test-repo"));

        instance.withGitHubRemote();
        assertThat(instance.remote(), is("https://github.com/tester/test-repo.git"));

        SSHCheckoutTrait sshTrait = new SSHCheckoutTrait("user-key");
        sshTrait.decorateBuilder(instance);

        GitSCM actual = instance.build();
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.remote(), is("git@github.com:tester/test-repo.git"));

        assertThat(actual.getBrowser(), instanceOf(GithubWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://github.com/tester/test-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("git@github.com:tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("git@github.com:tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(
                actual.getExtensions(),
                containsInAnyOrder(instanceOf(BuildChooserSetting.class), instanceOf(GitSCMSourceDefaults.class)));
        BuildChooserSetting chooser = getExtension(actual, BuildChooserSetting.class);
        assertThat(chooser.getBuildChooser(), instanceOf(SpecificRevisionBuildChooser.class));
        SpecificRevisionBuildChooser revChooser = (SpecificRevisionBuildChooser) chooser.getBuildChooser();
        Collection<Revision> revisions = revChooser.getCandidateRevisions(
                false,
                "test-branch",
                mock(GitClient.class),
                new LogTaskListener(getAnonymousLogger(), FINEST),
                null,
                null);
        assertThat(revisions, hasSize(1));
        assertThat(revisions.iterator().next().getSha1String(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
    }

    @Test
    public void given__cloud_branch_rev_userkey_sshtrait_userkey__when__build__then__scmBuilt() throws Exception {
        createGitHubSCMSourceForTest(false, null);
        BranchSCMHead head = new BranchSCMHead("test-branch");
        SCMRevisionImpl revision = new SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe");
        source.setCredentialsId("user-key");
        GitHubSCMBuilder instance = new GitHubSCMBuilder(source, head, revision);
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is(head));
        assertThat(instance.revision(), is(revision));
        assertThat(instance.refSpecs(), contains("+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"));
        assertThat(
                "expecting guess value until withGitHubRemote called",
                instance.remote(),
                is("https://github.com/tester/test-repo.git"));
        assertThat(instance.browser(), instanceOf(GithubWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://github.com/tester/test-repo"));

        instance.withGitHubRemote();
        assertThat(instance.remote(), is("git@github.com:tester/test-repo.git"));

        SSHCheckoutTrait sshTrait = new SSHCheckoutTrait("user-key");
        sshTrait.decorateBuilder(instance);

        GitSCM actual = instance.build();
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.remote(), is("git@github.com:tester/test-repo.git"));

        assertThat(actual.getBrowser(), instanceOf(GithubWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://github.com/tester/test-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("git@github.com:tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("git@github.com:tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(
                actual.getExtensions(),
                containsInAnyOrder(instanceOf(BuildChooserSetting.class), instanceOf(GitSCMSourceDefaults.class)));
        BuildChooserSetting chooser = getExtension(actual, BuildChooserSetting.class);
        assertThat(chooser.getBuildChooser(), instanceOf(SpecificRevisionBuildChooser.class));
        SpecificRevisionBuildChooser revChooser = (SpecificRevisionBuildChooser) chooser.getBuildChooser();
        Collection<Revision> revisions = revChooser.getCandidateRevisions(
                false,
                "test-branch",
                mock(GitClient.class),
                new LogTaskListener(getAnonymousLogger(), FINEST),
                null,
                null);
        assertThat(revisions, hasSize(1));
        assertThat(revisions.iterator().next().getSha1String(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
    }

    @Test
    public void given__cloud_branch_norev_anon__when__build__then__scmBuilt() throws Exception {
        createGitHubSCMSourceForTest(false, null);
        BranchSCMHead head = new BranchSCMHead("test-branch");
        source.setCredentialsId(null);
        GitHubSCMBuilder instance = new GitHubSCMBuilder(source, head, null);
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.head(), is(head));
        assertThat(instance.revision(), is(nullValue()));
        assertThat(instance.refSpecs(), contains("+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"));
        assertThat(
                "expecting guess value until withGitHubRemote called",
                instance.remote(),
                is("https://github.com/tester/test-repo.git"));
        assertThat(instance.browser(), instanceOf(GithubWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://github.com/tester/test-repo"));

        instance.withGitHubRemote();
        assertThat(instance.remote(), is("https://github.com/tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(GithubWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://github.com/tester/test-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("https://github.com/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is(nullValue()));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://github.com/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), contains(instanceOf(GitSCMSourceDefaults.class)));
    }

    @Test
    public void given__cloud_branch_norev_userpass__when__build__then__scmBuilt() throws Exception {
        createGitHubSCMSourceForTest(false, null);
        BranchSCMHead head = new BranchSCMHead("test-branch");
        source.setCredentialsId("user-pass");
        GitHubSCMBuilder instance = new GitHubSCMBuilder(source, head, null);
        assertThat(instance.credentialsId(), is("user-pass"));
        assertThat(instance.head(), is(head));
        assertThat(instance.revision(), is(nullValue()));
        assertThat(instance.refSpecs(), contains("+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"));
        assertThat(
                "expecting guess value until withGitHubRemote called",
                instance.remote(),
                is("https://github.com/tester/test-repo.git"));
        assertThat(instance.browser(), instanceOf(GithubWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://github.com/tester/test-repo"));

        instance.withGitHubRemote();
        assertThat(instance.remote(), is("https://github.com/tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(GithubWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://github.com/tester/test-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("https://github.com/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-pass"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://github.com/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), contains(instanceOf(GitSCMSourceDefaults.class)));
    }

    @Test
    public void given__cloud_branch_norev_userkey__when__build__then__scmBuilt() throws Exception {
        createGitHubSCMSourceForTest(false, null);
        BranchSCMHead head = new BranchSCMHead("test-branch");
        source.setCredentialsId("user-key");
        GitHubSCMBuilder instance = new GitHubSCMBuilder(source, head, null);
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is(head));
        assertThat(instance.revision(), is(nullValue()));
        assertThat(instance.refSpecs(), contains("+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"));
        assertThat(
                "expecting guess value until withGitHubRemote called",
                instance.remote(),
                is("https://github.com/tester/test-repo.git"));
        assertThat(instance.browser(), instanceOf(GithubWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://github.com/tester/test-repo"));

        instance.withGitHubRemote();
        assertThat(instance.remote(), is("git@github.com:tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(GithubWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://github.com/tester/test-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("git@github.com:tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("git@github.com:tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), contains(instanceOf(GitSCMSourceDefaults.class)));
    }

    @Test
    public void given__server_branch_rev_anon__when__build__then__scmBuilt() throws Exception {
        createGitHubSCMSourceForTest(true, "https://github.test/tester/test-repo.git");
        BranchSCMHead head = new BranchSCMHead("test-branch");
        AbstractGitSCMSource.SCMRevisionImpl revision =
                new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe");
        source.setCredentialsId(null);
        GitHubSCMBuilder instance = new GitHubSCMBuilder(source, head, revision);
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.head(), is(head));
        assertThat(instance.revision(), is(revision));
        assertThat(instance.refSpecs(), contains("+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"));
        assertThat(
                "expecting guess value until withGitHubRemote called",
                instance.remote(),
                is("https://github.test/tester/test-repo.git"));
        assertThat(instance.browser(), instanceOf(GithubWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://github.test/tester/test-repo"));

        instance.withGitHubRemote();
        assertThat(instance.remote(), is("https://github.test/tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(GithubWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://github.test/tester/test-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("https://github.test/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is(nullValue()));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://github.test/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(
                actual.getExtensions(),
                containsInAnyOrder(instanceOf(GitSCMSourceDefaults.class), instanceOf(BuildChooserSetting.class)));
        BuildChooserSetting chooser = getExtension(actual, BuildChooserSetting.class);
        assertThat(chooser.getBuildChooser(), instanceOf(AbstractGitSCMSource.SpecificRevisionBuildChooser.class));
        AbstractGitSCMSource.SpecificRevisionBuildChooser revChooser =
                (AbstractGitSCMSource.SpecificRevisionBuildChooser) chooser.getBuildChooser();
        Collection<Revision> revisions = revChooser.getCandidateRevisions(
                false,
                "test-branch",
                Mockito.mock(GitClient.class),
                new LogTaskListener(Logger.getAnonymousLogger(), Level.FINEST),
                null,
                null);
        assertThat(revisions, hasSize(1));
        assertThat(revisions.iterator().next().getSha1String(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
    }

    @Test
    public void given__server_branch_rev_userpass__when__build__then__scmBuilt() throws Exception {
        createGitHubSCMSourceForTest(true, "https://github.test/tester/test-repo.git");
        BranchSCMHead head = new BranchSCMHead("test-branch");
        AbstractGitSCMSource.SCMRevisionImpl revision =
                new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe");
        source.setCredentialsId("user-pass");
        GitHubSCMBuilder instance = new GitHubSCMBuilder(source, head, revision);
        assertThat(instance.credentialsId(), is("user-pass"));
        assertThat(instance.head(), is(head));
        assertThat(instance.revision(), is(revision));
        assertThat(instance.refSpecs(), contains("+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"));
        assertThat(
                "expecting guess value until withGitHubRemote called",
                instance.remote(),
                is("https://github.test/tester/test-repo.git"));
        assertThat(instance.browser(), instanceOf(GithubWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://github.test/tester/test-repo"));

        instance.withGitHubRemote();
        assertThat(instance.remote(), is("https://github.test/tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(GithubWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://github.test/tester/test-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("https://github.test/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-pass"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://github.test/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(
                actual.getExtensions(),
                containsInAnyOrder(instanceOf(GitSCMSourceDefaults.class), instanceOf(BuildChooserSetting.class)));
        BuildChooserSetting chooser = getExtension(actual, BuildChooserSetting.class);
        assertThat(chooser.getBuildChooser(), instanceOf(AbstractGitSCMSource.SpecificRevisionBuildChooser.class));
        AbstractGitSCMSource.SpecificRevisionBuildChooser revChooser =
                (AbstractGitSCMSource.SpecificRevisionBuildChooser) chooser.getBuildChooser();
        Collection<Revision> revisions = revChooser.getCandidateRevisions(
                false,
                "test-branch",
                Mockito.mock(GitClient.class),
                new LogTaskListener(Logger.getAnonymousLogger(), Level.FINEST),
                null,
                null);
        assertThat(revisions, hasSize(1));
        assertThat(revisions.iterator().next().getSha1String(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
    }

    @Test
    public void given__server_branch_rev_userkey__when__build__then__scmBuilt() throws Exception {
        createGitHubSCMSourceForTest(true, "https://github.test/tester/test-repo.git");
        BranchSCMHead head = new BranchSCMHead("test-branch");
        AbstractGitSCMSource.SCMRevisionImpl revision =
                new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe");
        source.setCredentialsId("user-key");
        GitHubSCMBuilder instance = new GitHubSCMBuilder(source, head, revision);
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is(head));
        assertThat(instance.revision(), is(revision));
        assertThat(instance.refSpecs(), contains("+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"));
        assertThat(
                "expecting guess value until withGitHubRemote called",
                instance.remote(),
                is("https://github.test/tester/test-repo.git"));
        assertThat(instance.browser(), instanceOf(GithubWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://github.test/tester/test-repo"));

        instance.withGitHubRemote();
        assertThat(instance.remote(), is("git@github.test:tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(GithubWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://github.test/tester/test-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("git@github.test:tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("git@github.test:tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(
                actual.getExtensions(),
                containsInAnyOrder(instanceOf(GitSCMSourceDefaults.class), instanceOf(BuildChooserSetting.class)));
        BuildChooserSetting chooser = getExtension(actual, BuildChooserSetting.class);
        assertThat(chooser.getBuildChooser(), instanceOf(AbstractGitSCMSource.SpecificRevisionBuildChooser.class));
        AbstractGitSCMSource.SpecificRevisionBuildChooser revChooser =
                (AbstractGitSCMSource.SpecificRevisionBuildChooser) chooser.getBuildChooser();
        Collection<Revision> revisions = revChooser.getCandidateRevisions(
                false,
                "test-branch",
                Mockito.mock(GitClient.class),
                new LogTaskListener(Logger.getAnonymousLogger(), Level.FINEST),
                null,
                null);
        assertThat(revisions, hasSize(1));
        assertThat(revisions.iterator().next().getSha1String(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
    }

    @Test
    public void given__server_branch_norev_anon__when__build__then__scmBuilt() throws Exception {
        createGitHubSCMSourceForTest(true, "https://github.test/tester/test-repo.git");
        BranchSCMHead head = new BranchSCMHead("test-branch");
        source.setCredentialsId(null);
        GitHubSCMBuilder instance = new GitHubSCMBuilder(source, head, null);
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.head(), is(head));
        assertThat(instance.revision(), is(nullValue()));
        assertThat(instance.refSpecs(), contains("+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"));
        assertThat(
                "expecting guess value until withGitHubRemote called",
                instance.remote(),
                is("https://github.test/tester/test-repo.git"));
        assertThat(instance.browser(), instanceOf(GithubWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://github.test/tester/test-repo"));

        instance.withGitHubRemote();
        assertThat(instance.remote(), is("https://github.test/tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(GithubWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://github.test/tester/test-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("https://github.test/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is(nullValue()));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://github.test/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), contains(instanceOf(GitSCMSourceDefaults.class)));
    }

    @Test
    public void given__server_branch_norev_userpass__when__build__then__scmBuilt() throws Exception {
        createGitHubSCMSourceForTest(true, "https://github.test/tester/test-repo.git");
        BranchSCMHead head = new BranchSCMHead("test-branch");
        source.setCredentialsId("user-pass");
        GitHubSCMBuilder instance = new GitHubSCMBuilder(source, head, null);
        assertThat(instance.credentialsId(), is("user-pass"));
        assertThat(instance.head(), is(head));
        assertThat(instance.revision(), is(nullValue()));
        assertThat(instance.refSpecs(), contains("+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"));
        assertThat(
                "expecting guess value until withGitHubRemote called",
                instance.remote(),
                is("https://github.test/tester/test-repo.git"));
        assertThat(instance.browser(), instanceOf(GithubWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://github.test/tester/test-repo"));

        instance.withGitHubRemote();
        assertThat(instance.remote(), is("https://github.test/tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(GithubWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://github.test/tester/test-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("https://github.test/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-pass"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://github.test/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), contains(instanceOf(GitSCMSourceDefaults.class)));
    }

    @Test
    public void given__server_branch_norev_userkey__when__build__then__scmBuilt() throws Exception {
        createGitHubSCMSourceForTest(true, "https://github.test/tester/test-repo.git");
        BranchSCMHead head = new BranchSCMHead("test-branch");
        source.setCredentialsId("user-key");
        GitHubSCMBuilder instance = new GitHubSCMBuilder(source, head, null);
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is(head));
        assertThat(instance.revision(), is(nullValue()));
        assertThat(instance.refSpecs(), contains("+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"));
        assertThat(
                "expecting guess value until withGitHubRemote called",
                instance.remote(),
                is("https://github.test/tester/test-repo.git"));
        assertThat(instance.browser(), instanceOf(GithubWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://github.test/tester/test-repo"));

        instance.withGitHubRemote();
        assertThat(instance.remote(), is("git@github.test:tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(GithubWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://github.test/tester/test-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("git@github.test:tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("git@github.test:tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), contains(instanceOf(GitSCMSourceDefaults.class)));
    }

    @Test
    public void given__cloud_pullHead_rev_anon__when__build__then__scmBuilt() throws Exception {
        createGitHubSCMSourceForTest(false, null);
        PullRequestSCMHead head = new PullRequestSCMHead(
                "PR-1",
                "qa",
                "qa-repo",
                "qa-branch",
                1,
                new BranchSCMHead("test-branch"),
                new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.HEAD);
        PullRequestSCMRevision revision = new PullRequestSCMRevision(
                head, "deadbeefcafebabedeadbeefcafebabedeadbeef", "cafebabedeadbeefcafebabedeadbeefcafebabe");
        source.setCredentialsId(null);
        GitHubSCMBuilder instance = new GitHubSCMBuilder(source, head, revision);
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.head(), is(head));
        assertThat(instance.revision(), is(revision));
        assertThat(instance.refSpecs(), contains("+refs/pull/1/head:refs/remotes/@{remote}/PR-1"));
        assertThat(
                "expecting guess value until withGitHubRemote called",
                instance.remote(),
                is("https://github.com/tester/test-repo.git"));
        assertThat(instance.browser(), instanceOf(GithubWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://github.com/qa/qa-repo"));

        instance.withGitHubRemote();
        assertThat(instance.remote(), is("https://github.com/tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(GithubWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://github.com/qa/qa-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/pull/1/head:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("https://github.com/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is(nullValue()));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://github.com/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/pull/1/head"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(
                actual.getExtensions(),
                containsInAnyOrder(instanceOf(BuildChooserSetting.class), instanceOf(GitSCMSourceDefaults.class)));
        BuildChooserSetting chooser = getExtension(actual, BuildChooserSetting.class);
        assertThat(chooser.getBuildChooser(), instanceOf(AbstractGitSCMSource.SpecificRevisionBuildChooser.class));
        AbstractGitSCMSource.SpecificRevisionBuildChooser revChooser =
                (AbstractGitSCMSource.SpecificRevisionBuildChooser) chooser.getBuildChooser();
        Collection<Revision> revisions = revChooser.getCandidateRevisions(
                false,
                "qa-branch",
                Mockito.mock(GitClient.class),
                new LogTaskListener(Logger.getAnonymousLogger(), Level.FINEST),
                null,
                null);
        assertThat(revisions, hasSize(1));
        assertThat(revisions.iterator().next().getSha1String(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
    }

    @Test
    public void given__cloud_pullHead_rev_userpass__when__build__then__scmBuilt() throws Exception {
        createGitHubSCMSourceForTest(false, null);
        PullRequestSCMHead head = new PullRequestSCMHead(
                "PR-1",
                "qa",
                "qa-repo",
                "qa-branch",
                1,
                new BranchSCMHead("test-branch"),
                new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.HEAD);
        PullRequestSCMRevision revision = new PullRequestSCMRevision(
                head, "deadbeefcafebabedeadbeefcafebabedeadbeef", "cafebabedeadbeefcafebabedeadbeefcafebabe");
        source.setCredentialsId("user-pass");
        GitHubSCMBuilder instance = new GitHubSCMBuilder(source, head, revision);
        assertThat(instance.credentialsId(), is("user-pass"));
        assertThat(instance.head(), is(head));
        assertThat(instance.revision(), is(revision));
        assertThat(instance.refSpecs(), contains("+refs/pull/1/head:refs/remotes/@{remote}/PR-1"));
        assertThat(
                "expecting guess value until withGitHubRemote called",
                instance.remote(),
                is("https://github.com/tester/test-repo.git"));
        assertThat(instance.browser(), instanceOf(GithubWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://github.com/qa/qa-repo"));

        instance.withGitHubRemote();
        assertThat(instance.remote(), is("https://github.com/tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(GithubWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://github.com/qa/qa-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/pull/1/head:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("https://github.com/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-pass"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://github.com/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/pull/1/head"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(
                actual.getExtensions(),
                containsInAnyOrder(instanceOf(BuildChooserSetting.class), instanceOf(GitSCMSourceDefaults.class)));
        BuildChooserSetting chooser = getExtension(actual, BuildChooserSetting.class);
        assertThat(chooser.getBuildChooser(), instanceOf(AbstractGitSCMSource.SpecificRevisionBuildChooser.class));
        AbstractGitSCMSource.SpecificRevisionBuildChooser revChooser =
                (AbstractGitSCMSource.SpecificRevisionBuildChooser) chooser.getBuildChooser();
        Collection<Revision> revisions = revChooser.getCandidateRevisions(
                false,
                "qa-branch",
                Mockito.mock(GitClient.class),
                new LogTaskListener(Logger.getAnonymousLogger(), Level.FINEST),
                null,
                null);
        assertThat(revisions, hasSize(1));
        assertThat(revisions.iterator().next().getSha1String(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
    }

    @Test
    public void given__cloud_pullHead_rev_userkey__when__build__then__scmBuilt() throws Exception {
        PullRequestSCMHead head = new PullRequestSCMHead(
                "PR-1",
                "qa",
                "qa-repo",
                "qa-branch",
                1,
                new BranchSCMHead("test-branch"),
                new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.HEAD);
        PullRequestSCMRevision revision = new PullRequestSCMRevision(
                head, "deadbeefcafebabedeadbeefcafebabedeadbeef", "cafebabedeadbeefcafebabedeadbeefcafebabe");
        createGitHubSCMSourceForTest(false, null);
        source.setCredentialsId("user-key");
        GitHubSCMBuilder instance = new GitHubSCMBuilder(source, head, revision);
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is(head));
        assertThat(instance.revision(), is(revision));
        assertThat(instance.refSpecs(), contains("+refs/pull/1/head:refs/remotes/@{remote}/PR-1"));
        assertThat(
                "expecting guess value until withGitHubRemote called",
                instance.remote(),
                is("https://github.com/tester/test-repo.git"));
        assertThat(instance.browser(), instanceOf(GithubWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://github.com/qa/qa-repo"));

        instance.withGitHubRemote();
        assertThat(instance.remote(), is("git@github.com:tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(GithubWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://github.com/qa/qa-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/pull/1/head:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("git@github.com:tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("git@github.com:tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/pull/1/head"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(
                actual.getExtensions(),
                containsInAnyOrder(instanceOf(BuildChooserSetting.class), instanceOf(GitSCMSourceDefaults.class)));
        BuildChooserSetting chooser = getExtension(actual, BuildChooserSetting.class);
        assertThat(chooser.getBuildChooser(), instanceOf(AbstractGitSCMSource.SpecificRevisionBuildChooser.class));
        AbstractGitSCMSource.SpecificRevisionBuildChooser revChooser =
                (AbstractGitSCMSource.SpecificRevisionBuildChooser) chooser.getBuildChooser();
        Collection<Revision> revisions = revChooser.getCandidateRevisions(
                false,
                "qa-branch",
                Mockito.mock(GitClient.class),
                new LogTaskListener(Logger.getAnonymousLogger(), Level.FINEST),
                null,
                null);
        assertThat(revisions, hasSize(1));
        assertThat(revisions.iterator().next().getSha1String(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
    }

    @Test
    public void given__cloud_pullHead_norev_anon__when__build__then__scmBuilt() throws Exception {
        createGitHubSCMSourceForTest(false, null);
        PullRequestSCMHead head = new PullRequestSCMHead(
                "PR-1",
                "qa",
                "qa-repo",
                "qa-branch",
                1,
                new BranchSCMHead("test-branch"),
                new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.HEAD);
        source.setCredentialsId(null);
        GitHubSCMBuilder instance = new GitHubSCMBuilder(source, head, null);
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.head(), is(head));
        assertThat(instance.revision(), is(nullValue()));
        assertThat(instance.refSpecs(), contains("+refs/pull/1/head:refs/remotes/@{remote}/PR-1"));
        assertThat(
                "expecting guess value until withGitHubRemote called",
                instance.remote(),
                is("https://github.com/tester/test-repo.git"));
        assertThat(instance.browser(), instanceOf(GithubWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://github.com/qa/qa-repo"));

        instance.withGitHubRemote();
        assertThat(instance.remote(), is("https://github.com/tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(GithubWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://github.com/qa/qa-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/pull/1/head:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("https://github.com/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is(nullValue()));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://github.com/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/pull/1/head"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), contains(instanceOf(GitSCMSourceDefaults.class)));
    }

    @Test
    public void given__cloud_pullHead_norev_userpass__when__build__then__scmBuilt() throws Exception {
        createGitHubSCMSourceForTest(false, null);
        PullRequestSCMHead head = new PullRequestSCMHead(
                "PR-1",
                "qa",
                "qa-repo",
                "qa-branch",
                1,
                new BranchSCMHead("test-branch"),
                new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.HEAD);
        source.setCredentialsId("user-pass");
        GitHubSCMBuilder instance = new GitHubSCMBuilder(source, head, null);
        assertThat(instance.credentialsId(), is("user-pass"));
        assertThat(instance.head(), is(head));
        assertThat(instance.revision(), is(nullValue()));
        assertThat(instance.refSpecs(), contains("+refs/pull/1/head:refs/remotes/@{remote}/PR-1"));
        assertThat(
                "expecting guess value until withGitHubRemote called",
                instance.remote(),
                is("https://github.com/tester/test-repo.git"));
        assertThat(instance.browser(), instanceOf(GithubWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://github.com/qa/qa-repo"));

        instance.withGitHubRemote();
        assertThat(instance.remote(), is("https://github.com/tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(GithubWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://github.com/qa/qa-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/pull/1/head:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("https://github.com/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-pass"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://github.com/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/pull/1/head"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), contains(instanceOf(GitSCMSourceDefaults.class)));
    }

    @Test
    public void given__cloud_pullHead_norev_userkey__when__build__then__scmBuilt() throws Exception {
        createGitHubSCMSourceForTest(false, null);
        PullRequestSCMHead head = new PullRequestSCMHead(
                "PR-1",
                "qa",
                "qa-repo",
                "qa-branch",
                1,
                new BranchSCMHead("test-branch"),
                new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.HEAD);
        source.setCredentialsId("user-key");
        GitHubSCMBuilder instance = new GitHubSCMBuilder(source, head, null);
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is(head));
        assertThat(instance.revision(), is(nullValue()));
        assertThat(instance.refSpecs(), contains("+refs/pull/1/head:refs/remotes/@{remote}/PR-1"));
        assertThat(
                "expecting guess value until withGitHubRemote called",
                instance.remote(),
                is("https://github.com/tester/test-repo.git"));
        assertThat(instance.browser(), instanceOf(GithubWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://github.com/qa/qa-repo"));

        instance.withGitHubRemote();
        assertThat(instance.remote(), is("git@github.com:tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(GithubWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://github.com/qa/qa-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/pull/1/head:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("git@github.com:tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("git@github.com:tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/pull/1/head"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), contains(instanceOf(GitSCMSourceDefaults.class)));
    }

    @Test
    public void given__server_pullHead_rev_anon__when__build__then__scmBuilt() throws Exception {
        createGitHubSCMSourceForTest(true, "https://github.test/tester/test-repo.git");
        PullRequestSCMHead head = new PullRequestSCMHead(
                "PR-1",
                "qa",
                "qa-repo",
                "qa-branch",
                1,
                new BranchSCMHead("test-branch"),
                new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.HEAD);
        PullRequestSCMRevision revision = new PullRequestSCMRevision(
                head, "deadbeefcafebabedeadbeefcafebabedeadbeef", "cafebabedeadbeefcafebabedeadbeefcafebabe");
        source.setCredentialsId(null);
        GitHubSCMBuilder instance = new GitHubSCMBuilder(source, head, revision);
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.head(), is(head));
        assertThat(instance.revision(), is(revision));
        assertThat(instance.refSpecs(), contains("+refs/pull/1/head:refs/remotes/@{remote}/PR-1"));
        assertThat(
                "expecting guess value until withGitHubRemote called",
                instance.remote(),
                is("https://github.test/tester/test-repo.git"));
        assertThat(instance.browser(), instanceOf(GithubWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://github.test/qa/qa-repo"));

        instance.withGitHubRemote();
        assertThat(instance.remote(), is("https://github.test/tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(GithubWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://github.test/qa/qa-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/pull/1/head:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("https://github.test/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is(nullValue()));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://github.test/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/pull/1/head"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(
                actual.getExtensions(),
                containsInAnyOrder(instanceOf(BuildChooserSetting.class), instanceOf(GitSCMSourceDefaults.class)));
        BuildChooserSetting chooser = getExtension(actual, BuildChooserSetting.class);
        assertThat(chooser.getBuildChooser(), instanceOf(AbstractGitSCMSource.SpecificRevisionBuildChooser.class));
        AbstractGitSCMSource.SpecificRevisionBuildChooser revChooser =
                (AbstractGitSCMSource.SpecificRevisionBuildChooser) chooser.getBuildChooser();
        Collection<Revision> revisions = revChooser.getCandidateRevisions(
                false,
                "qa-branch",
                Mockito.mock(GitClient.class),
                new LogTaskListener(Logger.getAnonymousLogger(), Level.FINEST),
                null,
                null);
        assertThat(revisions, hasSize(1));
        assertThat(revisions.iterator().next().getSha1String(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
    }

    @Test
    public void given__server_pullHead_rev_userpass__when__build__then__scmBuilt() throws Exception {
        createGitHubSCMSourceForTest(true, "https://github.test/tester/test-repo.git");
        PullRequestSCMHead head = new PullRequestSCMHead(
                "PR-1",
                "qa",
                "qa-repo",
                "qa-branch",
                1,
                new BranchSCMHead("test-branch"),
                new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.HEAD);
        PullRequestSCMRevision revision = new PullRequestSCMRevision(
                head, "deadbeefcafebabedeadbeefcafebabedeadbeef", "cafebabedeadbeefcafebabedeadbeefcafebabe");
        source.setCredentialsId("user-pass");
        GitHubSCMBuilder instance = new GitHubSCMBuilder(source, head, revision);
        assertThat(instance.credentialsId(), is("user-pass"));
        assertThat(instance.head(), is(head));
        assertThat(instance.revision(), is(revision));
        assertThat(instance.refSpecs(), contains("+refs/pull/1/head:refs/remotes/@{remote}/PR-1"));
        assertThat(
                "expecting guess value until withGitHubRemote called",
                instance.remote(),
                is("https://github.test/tester/test-repo.git"));
        assertThat(instance.browser(), instanceOf(GithubWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://github.test/qa/qa-repo"));

        instance.withGitHubRemote();
        assertThat(instance.remote(), is("https://github.test/tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(GithubWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://github.test/qa/qa-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/pull/1/head:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("https://github.test/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-pass"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://github.test/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/pull/1/head"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(
                actual.getExtensions(),
                containsInAnyOrder(instanceOf(BuildChooserSetting.class), instanceOf(GitSCMSourceDefaults.class)));
        BuildChooserSetting chooser = getExtension(actual, BuildChooserSetting.class);
        assertThat(chooser.getBuildChooser(), instanceOf(AbstractGitSCMSource.SpecificRevisionBuildChooser.class));
        AbstractGitSCMSource.SpecificRevisionBuildChooser revChooser =
                (AbstractGitSCMSource.SpecificRevisionBuildChooser) chooser.getBuildChooser();
        Collection<Revision> revisions = revChooser.getCandidateRevisions(
                false,
                "qa-branch",
                Mockito.mock(GitClient.class),
                new LogTaskListener(Logger.getAnonymousLogger(), Level.FINEST),
                null,
                null);
        assertThat(revisions, hasSize(1));
        assertThat(revisions.iterator().next().getSha1String(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
    }

    @Test
    public void given__server_pullHead_rev_userkey__when__build__then__scmBuilt() throws Exception {
        createGitHubSCMSourceForTest(true, "https://github.test/tester/test-repo.git");
        PullRequestSCMHead head = new PullRequestSCMHead(
                "PR-1",
                "qa",
                "qa-repo",
                "qa-branch",
                1,
                new BranchSCMHead("test-branch"),
                new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.HEAD);
        PullRequestSCMRevision revision = new PullRequestSCMRevision(
                head, "deadbeefcafebabedeadbeefcafebabedeadbeef", "cafebabedeadbeefcafebabedeadbeefcafebabe");
        source.setCredentialsId("user-key");
        GitHubSCMBuilder instance = new GitHubSCMBuilder(source, head, revision);
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is(head));
        assertThat(instance.revision(), is(revision));
        assertThat(instance.refSpecs(), contains("+refs/pull/1/head:refs/remotes/@{remote}/PR-1"));
        assertThat(
                "expecting guess value until withGitHubRemote called",
                instance.remote(),
                is("https://github.test/tester/test-repo.git"));
        assertThat(instance.browser(), instanceOf(GithubWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://github.test/qa/qa-repo"));

        instance.withGitHubRemote();
        assertThat(instance.remote(), is("git@github.test:tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(GithubWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://github.test/qa/qa-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/pull/1/head:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("git@github.test:tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("git@github.test:tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/pull/1/head"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(
                actual.getExtensions(),
                containsInAnyOrder(instanceOf(BuildChooserSetting.class), instanceOf(GitSCMSourceDefaults.class)));
        BuildChooserSetting chooser = getExtension(actual, BuildChooserSetting.class);
        assertThat(chooser.getBuildChooser(), instanceOf(AbstractGitSCMSource.SpecificRevisionBuildChooser.class));
        AbstractGitSCMSource.SpecificRevisionBuildChooser revChooser =
                (AbstractGitSCMSource.SpecificRevisionBuildChooser) chooser.getBuildChooser();
        Collection<Revision> revisions = revChooser.getCandidateRevisions(
                false,
                "qa-branch",
                Mockito.mock(GitClient.class),
                new LogTaskListener(Logger.getAnonymousLogger(), Level.FINEST),
                null,
                null);
        assertThat(revisions, hasSize(1));
        assertThat(revisions.iterator().next().getSha1String(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
    }

    @Test
    public void given__server_pullHead_norev_anon__when__build__then__scmBuilt() throws Exception {
        createGitHubSCMSourceForTest(true, "https://github.test/tester/test-repo.git");
        PullRequestSCMHead head = new PullRequestSCMHead(
                "PR-1",
                "qa",
                "qa-repo",
                "qa-branch",
                1,
                new BranchSCMHead("test-branch"),
                new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.HEAD);
        source.setCredentialsId(null);
        GitHubSCMBuilder instance = new GitHubSCMBuilder(source, head, null);
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.head(), is(head));
        assertThat(instance.revision(), is(nullValue()));
        assertThat(instance.refSpecs(), contains("+refs/pull/1/head:refs/remotes/@{remote}/PR-1"));
        assertThat(
                "expecting guess value until withGitHubRemote called",
                instance.remote(),
                is("https://github.test/tester/test-repo.git"));
        assertThat(instance.browser(), instanceOf(GithubWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://github.test/qa/qa-repo"));

        instance.withGitHubRemote();
        assertThat(instance.remote(), is("https://github.test/tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(GithubWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://github.test/qa/qa-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/pull/1/head:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("https://github.test/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is(nullValue()));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://github.test/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/pull/1/head"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), contains(instanceOf(GitSCMSourceDefaults.class)));
    }

    @Test
    public void given__server_pullHead_norev_userpass__when__build__then__scmBuilt() throws Exception {
        createGitHubSCMSourceForTest(true, "https://github.test/tester/test-repo.git");
        PullRequestSCMHead head = new PullRequestSCMHead(
                "PR-1",
                "qa",
                "qa-repo",
                "qa-branch",
                1,
                new BranchSCMHead("test-branch"),
                new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.HEAD);
        source.setCredentialsId("user-pass");
        GitHubSCMBuilder instance = new GitHubSCMBuilder(source, head, null);
        assertThat(instance.credentialsId(), is("user-pass"));
        assertThat(instance.head(), is(head));
        assertThat(instance.revision(), is(nullValue()));
        assertThat(instance.refSpecs(), contains("+refs/pull/1/head:refs/remotes/@{remote}/PR-1"));
        assertThat(
                "expecting guess value until withGitHubRemote called",
                instance.remote(),
                is("https://github.test/tester/test-repo.git"));
        assertThat(instance.browser(), instanceOf(GithubWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://github.test/qa/qa-repo"));

        instance.withGitHubRemote();
        assertThat(instance.remote(), is("https://github.test/tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(GithubWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://github.test/qa/qa-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/pull/1/head:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("https://github.test/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-pass"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://github.test/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/pull/1/head"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), contains(instanceOf(GitSCMSourceDefaults.class)));
    }

    @Test
    public void given__server_pullHead_norev_userkey__when__build__then__scmBuilt() throws Exception {
        createGitHubSCMSourceForTest(true, "https://github.test/tester/test-repo.git");
        PullRequestSCMHead head = new PullRequestSCMHead(
                "PR-1",
                "qa",
                "qa-repo",
                "qa-branch",
                1,
                new BranchSCMHead("test-branch"),
                new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.HEAD);
        source.setCredentialsId("user-key");
        GitHubSCMBuilder instance = new GitHubSCMBuilder(source, head, null);
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is(head));
        assertThat(instance.revision(), is(nullValue()));
        assertThat(instance.refSpecs(), contains("+refs/pull/1/head:refs/remotes/@{remote}/PR-1"));
        assertThat(
                "expecting guess value until withGitHubRemote called",
                instance.remote(),
                is("https://github.test/tester/test-repo.git"));
        assertThat(instance.browser(), instanceOf(GithubWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://github.test/qa/qa-repo"));

        instance.withGitHubRemote();
        assertThat(instance.remote(), is("git@github.test:tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(GithubWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://github.test/qa/qa-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/pull/1/head:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("git@github.test:tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("git@github.test:tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/pull/1/head"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), contains(instanceOf(GitSCMSourceDefaults.class)));
    }

    @Test
    public void given__cloud_pullMerge_rev_anon__when__build__then__scmBuilt() throws Exception {
        createGitHubSCMSourceForTest(false, null);
        PullRequestSCMHead head = new PullRequestSCMHead(
                "PR-1",
                "qa",
                "qa-repo",
                "qa-branch",
                1,
                new BranchSCMHead("test-branch"),
                new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.MERGE);
        PullRequestSCMRevision revision = new PullRequestSCMRevision(
                head, "deadbeefcafebabedeadbeefcafebabedeadbeef", "cafebabedeadbeefcafebabedeadbeefcafebabe");
        source.setCredentialsId(null);
        GitHubSCMBuilder instance = new GitHubSCMBuilder(source, head, revision);
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.head(), is(head));
        assertThat(instance.revision(), is(revision));
        assertThat(instance.refSpecs(), contains("+refs/pull/1/head:refs/remotes/@{remote}/PR-1"));
        assertThat(
                "expecting guess value until withGitHubRemote called",
                instance.remote(),
                is("https://github.com/tester/test-repo.git"));
        assertThat(instance.browser(), instanceOf(GithubWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://github.com/qa/qa-repo"));

        instance.withGitHubRemote();
        assertThat(instance.remote(), is("https://github.com/tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(GithubWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://github.com/qa/qa-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(
                config.getRefspec(),
                is("+refs/pull/1/head:refs/remotes/origin/PR-1 "
                        + "+refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("https://github.com/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is(nullValue()));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://github.com/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(2));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/pull/1/head"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(origin.getFetchRefSpecs().get(1).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(1).getDestination(), is("refs/remotes/origin/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(1).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(1).isWildcard(), is(false));
        assertThat(
                actual.getExtensions(),
                containsInAnyOrder(
                        instanceOf(GitSCMSourceDefaults.class),
                        instanceOf(BuildChooserSetting.class),
                        instanceOf(MergeWithGitSCMExtension.class)));
        BuildChooserSetting chooser = getExtension(actual, BuildChooserSetting.class);
        assertThat(chooser, notNullValue());
        assertThat(chooser.getBuildChooser(), instanceOf(AbstractGitSCMSource.SpecificRevisionBuildChooser.class));
        AbstractGitSCMSource.SpecificRevisionBuildChooser revChooser =
                (AbstractGitSCMSource.SpecificRevisionBuildChooser) chooser.getBuildChooser();
        Collection<Revision> revisions = revChooser.getCandidateRevisions(
                false,
                "test-branch",
                Mockito.mock(GitClient.class),
                new LogTaskListener(Logger.getAnonymousLogger(), Level.FINEST),
                null,
                null);
        assertThat(revisions, hasSize(1));
        assertThat(revisions.iterator().next().getSha1String(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
        MergeWithGitSCMExtension merge = getExtension(actual, MergeWithGitSCMExtension.class);
        assertThat(merge, notNullValue());
        assertThat(merge.getBaseName(), is("remotes/origin/test-branch"));
        assertThat(merge.getBaseHash(), is("deadbeefcafebabedeadbeefcafebabedeadbeef"));
    }

    @Test
    public void given__cloud_pullMerge_rev_userpass__when__build__then__scmBuilt() throws Exception {
        createGitHubSCMSourceForTest(false, null);
        PullRequestSCMHead head = new PullRequestSCMHead(
                "PR-1",
                "qa",
                "qa-repo",
                "qa-branch",
                1,
                new BranchSCMHead("test-branch"),
                new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.MERGE);
        PullRequestSCMRevision revision = new PullRequestSCMRevision(
                head, "deadbeefcafebabedeadbeefcafebabedeadbeef", "cafebabedeadbeefcafebabedeadbeefcafebabe");
        source.setCredentialsId("user-pass");
        GitHubSCMBuilder instance = new GitHubSCMBuilder(source, head, revision);
        assertThat(instance.credentialsId(), is("user-pass"));
        assertThat(instance.head(), is(head));
        assertThat(instance.revision(), is(revision));
        assertThat(instance.refSpecs(), contains("+refs/pull/1/head:refs/remotes/@{remote}/PR-1"));
        assertThat(
                "expecting guess value until withGitHubRemote called",
                instance.remote(),
                is("https://github.com/tester/test-repo.git"));
        assertThat(instance.browser(), instanceOf(GithubWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://github.com/qa/qa-repo"));

        instance.withGitHubRemote();
        assertThat(instance.remote(), is("https://github.com/tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(GithubWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://github.com/qa/qa-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(
                config.getRefspec(),
                is("+refs/pull/1/head:refs/remotes/origin/PR-1 "
                        + "+refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("https://github.com/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-pass"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://github.com/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(2));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/pull/1/head"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(origin.getFetchRefSpecs().get(1).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(1).getDestination(), is("refs/remotes/origin/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(1).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(1).isWildcard(), is(false));
        assertThat(
                actual.getExtensions(),
                containsInAnyOrder(
                        instanceOf(GitSCMSourceDefaults.class),
                        instanceOf(BuildChooserSetting.class),
                        instanceOf(MergeWithGitSCMExtension.class)));
        BuildChooserSetting chooser = getExtension(actual, BuildChooserSetting.class);
        assertThat(chooser, notNullValue());
        assertThat(chooser.getBuildChooser(), instanceOf(AbstractGitSCMSource.SpecificRevisionBuildChooser.class));
        AbstractGitSCMSource.SpecificRevisionBuildChooser revChooser =
                (AbstractGitSCMSource.SpecificRevisionBuildChooser) chooser.getBuildChooser();
        Collection<Revision> revisions = revChooser.getCandidateRevisions(
                false,
                "test-branch",
                Mockito.mock(GitClient.class),
                new LogTaskListener(Logger.getAnonymousLogger(), Level.FINEST),
                null,
                null);
        assertThat(revisions, hasSize(1));
        assertThat(revisions.iterator().next().getSha1String(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
        MergeWithGitSCMExtension merge = getExtension(actual, MergeWithGitSCMExtension.class);
        assertThat(merge, notNullValue());
        assertThat(merge.getBaseName(), is("remotes/origin/test-branch"));
        assertThat(merge.getBaseHash(), is("deadbeefcafebabedeadbeefcafebabedeadbeef"));
    }

    @Test
    public void given__cloud_pullMerge_rev_userkey__when__build__then__scmBuilt() throws Exception {
        createGitHubSCMSourceForTest(false, null);
        PullRequestSCMHead head = new PullRequestSCMHead(
                "PR-1",
                "qa",
                "qa-repo",
                "qa-branch",
                1,
                new BranchSCMHead("test-branch"),
                new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.MERGE);
        PullRequestSCMRevision revision = new PullRequestSCMRevision(
                head, "deadbeefcafebabedeadbeefcafebabedeadbeef", "cafebabedeadbeefcafebabedeadbeefcafebabe");
        source.setCredentialsId("user-key");
        GitHubSCMBuilder instance = new GitHubSCMBuilder(source, head, revision);
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is(head));
        assertThat(instance.revision(), is(revision));
        assertThat(instance.refSpecs(), contains("+refs/pull/1/head:refs/remotes/@{remote}/PR-1"));
        assertThat(
                "expecting guess value until withGitHubRemote called",
                instance.remote(),
                is("https://github.com/tester/test-repo.git"));
        assertThat(instance.browser(), instanceOf(GithubWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://github.com/qa/qa-repo"));

        instance.withGitHubRemote();
        assertThat(instance.remote(), is("git@github.com:tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(GithubWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://github.com/qa/qa-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(
                config.getRefspec(),
                is("+refs/pull/1/head:refs/remotes/origin/PR-1 "
                        + "+refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("git@github.com:tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("git@github.com:tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(2));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/pull/1/head"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(origin.getFetchRefSpecs().get(1).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(1).getDestination(), is("refs/remotes/origin/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(1).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(1).isWildcard(), is(false));
        assertThat(
                actual.getExtensions(),
                containsInAnyOrder(
                        instanceOf(GitSCMSourceDefaults.class),
                        instanceOf(BuildChooserSetting.class),
                        instanceOf(MergeWithGitSCMExtension.class)));
        BuildChooserSetting chooser = getExtension(actual, BuildChooserSetting.class);
        assertThat(chooser, notNullValue());
        assertThat(chooser.getBuildChooser(), instanceOf(AbstractGitSCMSource.SpecificRevisionBuildChooser.class));
        AbstractGitSCMSource.SpecificRevisionBuildChooser revChooser =
                (AbstractGitSCMSource.SpecificRevisionBuildChooser) chooser.getBuildChooser();
        Collection<Revision> revisions = revChooser.getCandidateRevisions(
                false,
                "test-branch",
                Mockito.mock(GitClient.class),
                new LogTaskListener(Logger.getAnonymousLogger(), Level.FINEST),
                null,
                null);
        assertThat(revisions, hasSize(1));
        assertThat(revisions.iterator().next().getSha1String(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
        MergeWithGitSCMExtension merge = getExtension(actual, MergeWithGitSCMExtension.class);
        assertThat(merge, notNullValue());
        assertThat(merge.getBaseName(), is("remotes/origin/test-branch"));
        assertThat(merge.getBaseHash(), is("deadbeefcafebabedeadbeefcafebabedeadbeef"));
    }

    @Test
    public void given__cloud_pullMerge_norev_anon__when__build__then__scmBuilt() throws Exception {
        createGitHubSCMSourceForTest(false, null);
        PullRequestSCMHead head = new PullRequestSCMHead(
                "PR-1",
                "qa",
                "qa-repo",
                "qa-branch",
                1,
                new BranchSCMHead("test-branch"),
                new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.MERGE);
        source.setCredentialsId(null);
        GitHubSCMBuilder instance = new GitHubSCMBuilder(source, head, null);
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.head(), is(head));
        assertThat(instance.revision(), is(nullValue()));
        assertThat(instance.refSpecs(), contains("+refs/pull/1/head:refs/remotes/@{remote}/PR-1"));
        assertThat(
                "expecting guess value until withGitHubRemote called",
                instance.remote(),
                is("https://github.com/tester/test-repo.git"));
        assertThat(instance.browser(), instanceOf(GithubWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://github.com/qa/qa-repo"));

        instance.withGitHubRemote();
        assertThat(instance.remote(), is("https://github.com/tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(GithubWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://github.com/qa/qa-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(
                config.getRefspec(),
                is("+refs/pull/1/head:refs/remotes/origin/PR-1 "
                        + "+refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("https://github.com/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is(nullValue()));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://github.com/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(2));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/pull/1/head"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(origin.getFetchRefSpecs().get(1).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(1).getDestination(), is("refs/remotes/origin/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(1).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(1).isWildcard(), is(false));
        assertThat(
                actual.getExtensions(),
                containsInAnyOrder(instanceOf(GitSCMSourceDefaults.class), instanceOf(MergeWithGitSCMExtension.class)));
        MergeWithGitSCMExtension merge = getExtension(actual, MergeWithGitSCMExtension.class);
        assertThat(merge.getBaseName(), is("remotes/origin/test-branch"));
        assertThat(merge.getBaseHash(), is(nullValue()));
    }

    @Test
    public void given__cloud_pullMerge_norev_userpass__when__build__then__scmBuilt() throws Exception {
        createGitHubSCMSourceForTest(false, null);
        PullRequestSCMHead head = new PullRequestSCMHead(
                "PR-1",
                "qa",
                "qa-repo",
                "qa-branch",
                1,
                new BranchSCMHead("test-branch"),
                new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.MERGE);
        source.setCredentialsId("user-pass");
        GitHubSCMBuilder instance = new GitHubSCMBuilder(source, head, null);
        assertThat(instance.credentialsId(), is("user-pass"));
        assertThat(instance.head(), is(head));
        assertThat(instance.revision(), is(nullValue()));
        assertThat(instance.refSpecs(), contains("+refs/pull/1/head:refs/remotes/@{remote}/PR-1"));
        assertThat(
                "expecting guess value until withGitHubRemote called",
                instance.remote(),
                is("https://github.com/tester/test-repo.git"));
        assertThat(instance.browser(), instanceOf(GithubWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://github.com/qa/qa-repo"));

        instance.withGitHubRemote();
        assertThat(instance.remote(), is("https://github.com/tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(GithubWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://github.com/qa/qa-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(
                config.getRefspec(),
                is("+refs/pull/1/head:refs/remotes/origin/PR-1 "
                        + "+refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("https://github.com/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-pass"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://github.com/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(2));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/pull/1/head"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(origin.getFetchRefSpecs().get(1).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(1).getDestination(), is("refs/remotes/origin/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(1).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(1).isWildcard(), is(false));
        assertThat(
                actual.getExtensions(),
                containsInAnyOrder(instanceOf(GitSCMSourceDefaults.class), instanceOf(MergeWithGitSCMExtension.class)));
        MergeWithGitSCMExtension merge = getExtension(actual, MergeWithGitSCMExtension.class);
        assertThat(merge.getBaseName(), is("remotes/origin/test-branch"));
        assertThat(merge.getBaseHash(), is(nullValue()));
    }

    @Test
    public void given__cloud_pullMerge_norev_userkey__when__build__then__scmBuilt() throws Exception {
        createGitHubSCMSourceForTest(false, null);
        PullRequestSCMHead head = new PullRequestSCMHead(
                "PR-1",
                "qa",
                "qa-repo",
                "qa-branch",
                1,
                new BranchSCMHead("test-branch"),
                new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.MERGE);
        source.setCredentialsId("user-key");
        GitHubSCMBuilder instance = new GitHubSCMBuilder(source, head, null);
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is(head));
        assertThat(instance.revision(), is(nullValue()));
        assertThat(instance.refSpecs(), contains("+refs/pull/1/head:refs/remotes/@{remote}/PR-1"));
        assertThat(
                "expecting guess value until withGitHubRemote called",
                instance.remote(),
                is("https://github.com/tester/test-repo.git"));
        assertThat(instance.browser(), instanceOf(GithubWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://github.com/qa/qa-repo"));

        instance.withGitHubRemote();
        assertThat(instance.remote(), is("git@github.com:tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(GithubWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://github.com/qa/qa-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(
                config.getRefspec(),
                is("+refs/pull/1/head:refs/remotes/origin/PR-1 "
                        + "+refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("git@github.com:tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("git@github.com:tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(2));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/pull/1/head"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(origin.getFetchRefSpecs().get(1).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(1).getDestination(), is("refs/remotes/origin/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(1).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(1).isWildcard(), is(false));
        assertThat(
                actual.getExtensions(),
                containsInAnyOrder(instanceOf(GitSCMSourceDefaults.class), instanceOf(MergeWithGitSCMExtension.class)));
        MergeWithGitSCMExtension merge = getExtension(actual, MergeWithGitSCMExtension.class);
        assertThat(merge.getBaseName(), is("remotes/origin/test-branch"));
        assertThat(merge.getBaseHash(), is(nullValue()));
    }

    @Test
    public void given__server_pullMerge_rev_anon__when__build__then__scmBuilt() throws Exception {
        createGitHubSCMSourceForTest(true, "https://github.test/tester/test-repo.git");
        PullRequestSCMHead head = new PullRequestSCMHead(
                "PR-1",
                "qa",
                "qa-repo",
                "qa-branch",
                1,
                new BranchSCMHead("test-branch"),
                new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.MERGE);
        PullRequestSCMRevision revision = new PullRequestSCMRevision(
                head, "deadbeefcafebabedeadbeefcafebabedeadbeef", "cafebabedeadbeefcafebabedeadbeefcafebabe");
        source.setCredentialsId(null);
        GitHubSCMBuilder instance = new GitHubSCMBuilder(source, head, revision);
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.head(), is(head));
        assertThat(instance.revision(), is(revision));
        assertThat(instance.refSpecs(), contains("+refs/pull/1/head:refs/remotes/@{remote}/PR-1"));
        assertThat(
                "expecting guess value until withGitHubRemote called",
                instance.remote(),
                is("https://github.test/tester/test-repo.git"));
        assertThat(instance.browser(), instanceOf(GithubWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://github.test/qa/qa-repo"));

        instance.withGitHubRemote();
        assertThat(instance.remote(), is("https://github.test/tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(GithubWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://github.test/qa/qa-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(
                config.getRefspec(),
                is("+refs/pull/1/head:refs/remotes/origin/PR-1 "
                        + "+refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("https://github.test/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is(nullValue()));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://github.test/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(2));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/pull/1/head"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(origin.getFetchRefSpecs().get(1).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(1).getDestination(), is("refs/remotes/origin/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(1).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(1).isWildcard(), is(false));
        assertThat(
                actual.getExtensions(),
                containsInAnyOrder(
                        instanceOf(MergeWithGitSCMExtension.class),
                        instanceOf(BuildChooserSetting.class),
                        instanceOf(GitSCMSourceDefaults.class)));
        BuildChooserSetting chooser = getExtension(actual, BuildChooserSetting.class);
        assertThat(chooser, notNullValue());
        assertThat(chooser.getBuildChooser(), instanceOf(AbstractGitSCMSource.SpecificRevisionBuildChooser.class));
        AbstractGitSCMSource.SpecificRevisionBuildChooser revChooser =
                (AbstractGitSCMSource.SpecificRevisionBuildChooser) chooser.getBuildChooser();
        Collection<Revision> revisions = revChooser.getCandidateRevisions(
                false,
                "test-branch",
                Mockito.mock(GitClient.class),
                new LogTaskListener(Logger.getAnonymousLogger(), Level.FINEST),
                null,
                null);
        assertThat(revisions, hasSize(1));
        assertThat(revisions.iterator().next().getSha1String(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
        MergeWithGitSCMExtension merge = getExtension(actual, MergeWithGitSCMExtension.class);
        assertThat(merge, notNullValue());
        assertThat(merge.getBaseName(), is("remotes/origin/test-branch"));
        assertThat(merge.getBaseHash(), is("deadbeefcafebabedeadbeefcafebabedeadbeef"));
    }

    @Test
    public void given__server_pullMerge_rev_userpass__when__build__then__scmBuilt() throws Exception {
        createGitHubSCMSourceForTest(true, "https://github.test/tester/test-repo.git");
        PullRequestSCMHead head = new PullRequestSCMHead(
                "PR-1",
                "qa",
                "qa-repo",
                "qa-branch",
                1,
                new BranchSCMHead("test-branch"),
                new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.MERGE);
        PullRequestSCMRevision revision = new PullRequestSCMRevision(
                head, "deadbeefcafebabedeadbeefcafebabedeadbeef", "cafebabedeadbeefcafebabedeadbeefcafebabe");
        source.setCredentialsId("user-pass");
        GitHubSCMBuilder instance = new GitHubSCMBuilder(source, head, revision);
        assertThat(instance.credentialsId(), is("user-pass"));
        assertThat(instance.head(), is(head));
        assertThat(instance.revision(), is(revision));
        assertThat(instance.refSpecs(), contains("+refs/pull/1/head:refs/remotes/@{remote}/PR-1"));
        assertThat(
                "expecting guess value until withGitHubRemote called",
                instance.remote(),
                is("https://github.test/tester/test-repo.git"));
        assertThat(instance.browser(), instanceOf(GithubWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://github.test/qa/qa-repo"));

        instance.withGitHubRemote();
        assertThat(instance.remote(), is("https://github.test/tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(GithubWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://github.test/qa/qa-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(
                config.getRefspec(),
                is("+refs/pull/1/head:refs/remotes/origin/PR-1 "
                        + "+refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("https://github.test/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-pass"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://github.test/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(2));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/pull/1/head"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(origin.getFetchRefSpecs().get(1).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(1).getDestination(), is("refs/remotes/origin/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(1).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(1).isWildcard(), is(false));
        assertThat(
                actual.getExtensions(),
                containsInAnyOrder(
                        instanceOf(MergeWithGitSCMExtension.class),
                        instanceOf(BuildChooserSetting.class),
                        instanceOf(GitSCMSourceDefaults.class)));
        BuildChooserSetting chooser = getExtension(actual, BuildChooserSetting.class);
        assertThat(chooser, notNullValue());
        assertThat(chooser.getBuildChooser(), instanceOf(AbstractGitSCMSource.SpecificRevisionBuildChooser.class));
        AbstractGitSCMSource.SpecificRevisionBuildChooser revChooser =
                (AbstractGitSCMSource.SpecificRevisionBuildChooser) chooser.getBuildChooser();
        Collection<Revision> revisions = revChooser.getCandidateRevisions(
                false,
                "test-branch",
                Mockito.mock(GitClient.class),
                new LogTaskListener(Logger.getAnonymousLogger(), Level.FINEST),
                null,
                null);
        assertThat(revisions, hasSize(1));
        assertThat(revisions.iterator().next().getSha1String(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
        MergeWithGitSCMExtension merge = getExtension(actual, MergeWithGitSCMExtension.class);
        assertThat(merge, notNullValue());
        assertThat(merge.getBaseName(), is("remotes/origin/test-branch"));
        assertThat(merge.getBaseHash(), is("deadbeefcafebabedeadbeefcafebabedeadbeef"));
    }

    @Test
    public void given__server_pullMerge_rev_userkey__when__build__then__scmBuilt() throws Exception {
        createGitHubSCMSourceForTest(true, "https://github.test/tester/test-repo.git");
        PullRequestSCMHead head = new PullRequestSCMHead(
                "PR-1",
                "qa",
                "qa-repo",
                "qa-branch",
                1,
                new BranchSCMHead("test-branch"),
                new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.MERGE);
        PullRequestSCMRevision revision = new PullRequestSCMRevision(
                head, "deadbeefcafebabedeadbeefcafebabedeadbeef", "cafebabedeadbeefcafebabedeadbeefcafebabe");
        source.setCredentialsId("user-key");
        GitHubSCMBuilder instance = new GitHubSCMBuilder(source, head, revision);
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is(head));
        assertThat(instance.revision(), is(revision));
        assertThat(instance.refSpecs(), contains("+refs/pull/1/head:refs/remotes/@{remote}/PR-1"));
        assertThat(
                "expecting guess value until withGitHubRemote called",
                instance.remote(),
                is("https://github.test/tester/test-repo.git"));
        assertThat(instance.browser(), instanceOf(GithubWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://github.test/qa/qa-repo"));

        instance.withGitHubRemote();
        assertThat(instance.remote(), is("git@github.test:tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(GithubWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://github.test/qa/qa-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(
                config.getRefspec(),
                is("+refs/pull/1/head:refs/remotes/origin/PR-1 "
                        + "+refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("git@github.test:tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("git@github.test:tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(2));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/pull/1/head"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(origin.getFetchRefSpecs().get(1).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(1).getDestination(), is("refs/remotes/origin/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(1).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(1).isWildcard(), is(false));
        assertThat(
                actual.getExtensions(),
                containsInAnyOrder(
                        instanceOf(MergeWithGitSCMExtension.class),
                        instanceOf(BuildChooserSetting.class),
                        instanceOf(GitSCMSourceDefaults.class)));
        BuildChooserSetting chooser = getExtension(actual, BuildChooserSetting.class);
        assertThat(chooser, notNullValue());
        assertThat(chooser.getBuildChooser(), instanceOf(AbstractGitSCMSource.SpecificRevisionBuildChooser.class));
        AbstractGitSCMSource.SpecificRevisionBuildChooser revChooser =
                (AbstractGitSCMSource.SpecificRevisionBuildChooser) chooser.getBuildChooser();
        Collection<Revision> revisions = revChooser.getCandidateRevisions(
                false,
                "test-branch",
                Mockito.mock(GitClient.class),
                new LogTaskListener(Logger.getAnonymousLogger(), Level.FINEST),
                null,
                null);
        assertThat(revisions, hasSize(1));
        assertThat(revisions.iterator().next().getSha1String(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
        MergeWithGitSCMExtension merge = getExtension(actual, MergeWithGitSCMExtension.class);
        assertThat(merge, notNullValue());
        assertThat(merge.getBaseName(), is("remotes/origin/test-branch"));
        assertThat(merge.getBaseHash(), is("deadbeefcafebabedeadbeefcafebabedeadbeef"));
    }

    @Test
    public void given__server_pullMerge_norev_anon__when__build__then__scmBuilt() throws Exception {
        createGitHubSCMSourceForTest(true, "https://github.test/tester/test-repo.git");
        PullRequestSCMHead head = new PullRequestSCMHead(
                "PR-1",
                "qa",
                "qa-repo",
                "qa-branch",
                1,
                new BranchSCMHead("test-branch"),
                new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.MERGE);
        source.setCredentialsId(null);
        GitHubSCMBuilder instance = new GitHubSCMBuilder(source, head, null);
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.head(), is(head));
        assertThat(instance.revision(), is(nullValue()));
        assertThat(instance.refSpecs(), contains("+refs/pull/1/head:refs/remotes/@{remote}/PR-1"));
        assertThat(
                "expecting guess value until withGitHubRemote called",
                instance.remote(),
                is("https://github.test/tester/test-repo.git"));
        assertThat(instance.browser(), instanceOf(GithubWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://github.test/qa/qa-repo"));

        instance.withGitHubRemote();
        assertThat(instance.remote(), is("https://github.test/tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(GithubWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://github.test/qa/qa-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(
                config.getRefspec(),
                is("+refs/pull/1/head:refs/remotes/origin/PR-1 "
                        + "+refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("https://github.test/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is(nullValue()));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://github.test/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(2));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/pull/1/head"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(origin.getFetchRefSpecs().get(1).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(1).getDestination(), is("refs/remotes/origin/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(1).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(1).isWildcard(), is(false));
        assertThat(
                actual.getExtensions(),
                containsInAnyOrder(instanceOf(MergeWithGitSCMExtension.class), instanceOf(GitSCMSourceDefaults.class)));
        MergeWithGitSCMExtension merge = getExtension(actual, MergeWithGitSCMExtension.class);
        assertThat(merge, notNullValue());
        assertThat(merge.getBaseName(), is("remotes/origin/test-branch"));
        assertThat(merge.getBaseHash(), is(nullValue()));
    }

    @Test
    public void given__server_pullMerge_norev_userpass__when__build__then__scmBuilt() throws Exception {
        createGitHubSCMSourceForTest(true, "https://github.test/tester/test-repo.git");
        PullRequestSCMHead head = new PullRequestSCMHead(
                "PR-1",
                "qa",
                "qa-repo",
                "qa-branch",
                1,
                new BranchSCMHead("test-branch"),
                new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.MERGE);
        source.setCredentialsId("user-pass");
        GitHubSCMBuilder instance = new GitHubSCMBuilder(source, head, null);
        assertThat(instance.credentialsId(), is("user-pass"));
        assertThat(instance.head(), is(head));
        assertThat(instance.revision(), is(nullValue()));
        assertThat(instance.refSpecs(), contains("+refs/pull/1/head:refs/remotes/@{remote}/PR-1"));
        assertThat(
                "expecting guess value until withGitHubRemote called",
                instance.remote(),
                is("https://github.test/tester/test-repo.git"));
        assertThat(instance.browser(), instanceOf(GithubWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://github.test/qa/qa-repo"));

        instance.withGitHubRemote();
        assertThat(instance.remote(), is("https://github.test/tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(GithubWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://github.test/qa/qa-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(
                config.getRefspec(),
                is("+refs/pull/1/head:refs/remotes/origin/PR-1 "
                        + "+refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("https://github.test/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-pass"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://github.test/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(2));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/pull/1/head"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(origin.getFetchRefSpecs().get(1).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(1).getDestination(), is("refs/remotes/origin/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(1).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(1).isWildcard(), is(false));
        assertThat(
                actual.getExtensions(),
                containsInAnyOrder(instanceOf(MergeWithGitSCMExtension.class), instanceOf(GitSCMSourceDefaults.class)));
        MergeWithGitSCMExtension merge = getExtension(actual, MergeWithGitSCMExtension.class);
        assertThat(merge, notNullValue());
        assertThat(merge.getBaseName(), is("remotes/origin/test-branch"));
        assertThat(merge.getBaseHash(), is(nullValue()));
    }

    @Test
    public void given__server_pullMerge_norev_userkey__when__build__then__scmBuilt() throws Exception {
        createGitHubSCMSourceForTest(true, "https://github.test/tester/test-repo.git");
        PullRequestSCMHead head = new PullRequestSCMHead(
                "PR-1",
                "qa",
                "qa-repo",
                "qa-branch",
                1,
                new BranchSCMHead("test-branch"),
                new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.MERGE);
        source.setCredentialsId("user-key");
        GitHubSCMBuilder instance = new GitHubSCMBuilder(source, head, null);
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is(head));
        assertThat(instance.revision(), is(nullValue()));
        assertThat(instance.refSpecs(), contains("+refs/pull/1/head:refs/remotes/@{remote}/PR-1"));
        assertThat(
                "expecting guess value until withGitHubRemote called",
                instance.remote(),
                is("https://github.test/tester/test-repo.git"));
        assertThat(instance.browser(), instanceOf(GithubWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://github.test/qa/qa-repo"));

        instance.withGitHubRemote();
        assertThat(instance.remote(), is("git@github.test:tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(GithubWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://github.test/qa/qa-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(
                config.getRefspec(),
                is("+refs/pull/1/head:refs/remotes/origin/PR-1 "
                        + "+refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("git@github.test:tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("git@github.test:tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(2));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/pull/1/head"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(origin.getFetchRefSpecs().get(1).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(1).getDestination(), is("refs/remotes/origin/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(1).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(1).isWildcard(), is(false));
        assertThat(
                actual.getExtensions(),
                containsInAnyOrder(instanceOf(MergeWithGitSCMExtension.class), instanceOf(GitSCMSourceDefaults.class)));
        MergeWithGitSCMExtension merge = getExtension(actual, MergeWithGitSCMExtension.class);
        assertThat(merge, notNullValue());
        assertThat(merge.getBaseName(), is("remotes/origin/test-branch"));
        assertThat(merge.getBaseHash(), is(nullValue()));
    }

    private static <T extends GitSCMExtension> T getExtension(GitSCM scm, Class<T> type) {
        for (GitSCMExtension e : scm.getExtensions()) {
            if (type.isInstance(e)) {
                return type.cast(e);
            }
        }
        return null;
    }
}
