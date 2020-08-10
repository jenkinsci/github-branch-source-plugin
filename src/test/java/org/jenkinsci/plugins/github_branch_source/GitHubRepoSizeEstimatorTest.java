package org.jenkinsci.plugins.github_branch_source;

import com.cloudbees.plugins.credentials.*;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.TopLevelItem;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.SubmoduleConfig;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.DisableRemotePoll;
import hudson.triggers.SCMTrigger;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.gitclient.JGitTool;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

public class GitHubRepoSizeEstimatorTest {

    @Rule
    public final JenkinsRule j = new JenkinsRule();

    private CredentialsStore store = null;

    @Before
    public void enableSystemCredentialsProvider() {
        SystemCredentialsProvider.getInstance().setDomainCredentialsMap(
                Collections.singletonMap(Domain.global(), Collections.<Credentials>emptyList()));
        for (CredentialsStore s : CredentialsProvider.lookupStores(Jenkins.get())) {
            if (s.getProvider() instanceof SystemCredentialsProvider.ProviderImpl) {
                store = s;
                break;
            }
        }
        assertThat("The system credentials provider is enabled", store, notNullValue());
    }

    @Test
    public void testExtensionApplicability() throws Exception {
        GitHubRepoSizeEstimator.RepositorySizeGithubAPI repositorySizeGithubAPI = new GitHubRepoSizeEstimator.RepositorySizeGithubAPI();

        String url = "https://github.com/jenkinsci/github-branch-source-plugin.git";

        store.addCredentials(Domain.global(), createCredential(CredentialsScope.GLOBAL, "github"));
        store.save();

//        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
//        p.setDefinition(new CpsFlowDefinition(
//                "node {\n"
//                        + "  checkout(\n"
//                        + "    [$class: 'GitSCM', \n"
//                        + "      userRemoteConfigs: [[credentialsId: 'github', url: $/" + url + "/$]]]\n"
//                        + "  )"
//                        + "}", true));
//        WorkflowRun b = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
//        j.waitForMessage("using credential github", b);
        List<UserRemoteConfig> repos = new ArrayList<>();
        repos.add(new UserRemoteConfig(url, "origin", null, "github"));
        FreeStyleProject projectWithMaster = setupProject(repos, Collections.singletonList(new BranchSpec("master")), null, false);
        projectWithMaster.scheduleBuild2(0);

        List<TopLevelItem> items =  j.jenkins.getItems();

        assertThat(repositorySizeGithubAPI.isApplicableTo(url, items.get(0), "github"), is(true));
    }

    private StandardCredentials createCredential(CredentialsScope scope, String id) {
        return new UsernamePasswordCredentialsImpl(scope, id, "desc: " + id, "rishabhBudhouliya", "rishabh2020");
    }

    protected FreeStyleProject setupProject(List<UserRemoteConfig> repos, List<BranchSpec> branchSpecs,
                                            String scmTriggerSpec, boolean disableRemotePoll) throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        GitSCM scm = new GitSCM(
                repos,
                branchSpecs,
                false, Collections.<SubmoduleConfig>emptyList(),
                null, JGitTool.MAGIC_EXENAME,
                Collections.<GitSCMExtension>emptyList());
        if(disableRemotePoll) scm.getExtensions().add(new DisableRemotePoll());
        project.setScm(scm);
        if(scmTriggerSpec != null) {
            SCMTrigger trigger = new SCMTrigger(scmTriggerSpec);
            project.addTrigger(trigger);
            trigger.start(project, true);
        }
        //project.getBuildersList().add(new CaptureEnvironmentBuilder());
        project.save();
        return project;
    }
}
