package org.jenkinsci.plugins.github_branch_source;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import jenkins.model.Jenkins;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.impl.trait.WildcardSCMHeadFilterTrait;
import org.hamcrest.Matchers;
import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class GitHubSCMSourceTraitsTest {
    /** All tests in this class only use Jenkins for the extensions */
    private static JenkinsRule r;

    private String currentTestName;

    @BeforeAll
    static void beforeAll(JenkinsRule rule) {
        r = rule;
    }

    @BeforeEach
    void beforeEach(TestInfo info) {
        currentTestName = info.getTestMethod().orElseThrow().getName();
    }

    private GitHubSCMSource load() {
        return load(currentTestName);
    }

    private GitHubSCMSource load(String dataSet) {
        return (GitHubSCMSource)
                Jenkins.XSTREAM2.fromXML(getClass().getResource(getClass().getSimpleName() + "/" + dataSet + ".xml"));
    }

    @Test
    void given__configuredInstance__when__uninstantiating__then__deprecatedFieldsIgnored() {
        GitHubSCMSource instance = new GitHubSCMSource("repo-owner", "repo", null, false);
        instance.setId("test");

        DescribableModel model = DescribableModel.of(GitHubSCMSource.class);
        UninstantiatedDescribable ud = model.uninstantiate2(instance);
        Map<String, Object> udMap = ud.toMap();
        GitHubSCMSource recreated = (GitHubSCMSource) model.instantiate(udMap);

        assertThat(
                DescribableModel.uninstantiate2_(recreated).toString(),
                is("@github(id=test,repoOwner=repo-owner,repository=repo)"));
        recreated.setBuildOriginBranch(true);
        recreated.setBuildOriginBranchWithPR(false);
        recreated.setBuildOriginPRHead(false);
        recreated.setBuildOriginPRMerge(true);
        recreated.setBuildForkPRHead(true);
        recreated.setBuildForkPRMerge(false);
        recreated.setIncludes("i*");
        recreated.setExcludes("production");
        recreated.setScanCredentialsId("foo");
        String originTrait;
        String forkTrait;
        if (r.jenkins.getPlugin("cloudbees-bitbucket-branch-source") != null) {
            originTrait = "org.jenkinsci.plugins.github_branch_source.OriginPullRequestDiscoveryTrait";
            forkTrait = "org.jenkinsci.plugins.github_branch_source.ForkPullRequestDiscoveryTrait";
        } else {
            originTrait = "OriginPullRequestDiscoveryTrait";
            forkTrait = "ForkPullRequestDiscoveryTrait";
        }
        assertThat(
                DescribableModel.uninstantiate2_(recreated).toString(),
                is("@github("
                        + "credentialsId=foo,"
                        + "id=test,"
                        + "repoOwner=repo-owner,"
                        + "repository=repo,"
                        + "traits=["
                        + "@gitHubBranchDiscovery$org.jenkinsci.plugins.github_branch_source.BranchDiscoveryTrait(strategyId=1), "
                        + "@gitHubPullRequestDiscovery$" + originTrait + "(strategyId=1), "
                        + "@gitHubForkDiscovery$" + forkTrait + "("
                        + "strategyId=2,"
                        + "trust=@gitHubTrustPermissions$TrustPermission()), "
                        + "@headWildcardFilter$WildcardSCMHeadFilterTrait(excludes=production,includes=i*)])"));
    }

    @Test
    void repositoryUrl() {
        GitHubSCMSource instance = load();

        assertThat(instance.getId(), is("e4d8c11a-0d24-472f-b86b-4b017c160e9a"));
        assertThat(instance.getApiUri(), is(GitHubSCMSource.GITHUB_URL));
        assertThat(instance.getRepoOwner(), is("joseblas"));
        assertThat(instance.getRepository(), is("jx"));
        assertThat(instance.getCredentialsId(), is("abcd"));
        assertThat(instance.getRepositoryUrl(), is("https://github.com/joseblas/jx"));
        assertThat(instance.getTraits(), is(Collections.<SCMSourceTrait>emptyList()));
        // Legacy API
        assertThat(instance.getCheckoutCredentialsId(), is(GitHubSCMSource.DescriptorImpl.SAME));
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(instance.getBuildOriginBranch(), is(false));
        assertThat(instance.getBuildOriginBranchWithPR(), is(false));
        assertThat(instance.getBuildOriginPRHead(), is(false));
        assertThat(instance.getBuildOriginPRMerge(), is(false));
        assertThat(instance.getBuildForkPRHead(), is(false));
        assertThat(instance.getBuildForkPRMerge(), is(false));
    }

    @Test
    void modern() {
        GitHubSCMSource instance = load();
        assertThat(instance.getId(), is("e4d8c11a-0d24-472f-b86b-4b017c160e9a"));
        assertThat(instance.getApiUri(), is(GitHubSCMSource.GITHUB_URL));
        assertThat(instance.getRepoOwner(), is("cloudbeers"));
        assertThat(instance.getRepository(), is("stunning-adventure"));
        assertThat(instance.getCredentialsId(), is(nullValue()));
        assertThat(instance.getRepositoryUrl(), is("https://github.com/cloudbeers/stunning-adventure"));
        assertThat(instance.getTraits(), is(Collections.<SCMSourceTrait>emptyList()));
        // Legacy API
        assertThat(instance.getCheckoutCredentialsId(), is(GitHubSCMSource.DescriptorImpl.SAME));
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(instance.getBuildOriginBranch(), is(false));
        assertThat(instance.getBuildOriginBranchWithPR(), is(false));
        assertThat(instance.getBuildOriginPRHead(), is(false));
        assertThat(instance.getBuildOriginPRMerge(), is(false));
        assertThat(instance.getBuildForkPRHead(), is(false));
        assertThat(instance.getBuildForkPRMerge(), is(false));
    }

    @Test
    void basic_cloud() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getId(),
                is("org.jenkinsci.plugins.github_branch_source.GitHubSCMNavigator"
                        + "::https://api.github.com"
                        + "::cloudbeers"
                        + "::stunning-adventure"));
        assertThat(instance.getApiUri(), is(GitHubSCMSource.GITHUB_URL));
        assertThat(instance.getRepoOwner(), is("cloudbeers"));
        assertThat(instance.getRepository(), is("stunning-adventure"));
        assertThat(instance.getCredentialsId(), is("e4d8c11a-0d24-472f-b86b-4b017c160e9a"));
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(true))),
                        Matchers.allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class),
                                hasProperty("strategyId", is(1)),
                                hasProperty(
                                        "trust", instanceOf(ForkPullRequestDiscoveryTrait.TrustPermission.class)))));
        // Legacy API
        assertThat(instance.getCheckoutCredentialsId(), is(GitHubSCMSource.DescriptorImpl.SAME));
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(instance.getBuildOriginBranch(), is(true));
        assertThat(instance.getBuildOriginBranchWithPR(), is(true));
        assertThat(instance.getBuildOriginPRHead(), is(false));
        assertThat(instance.getBuildOriginPRMerge(), is(false));
        assertThat(instance.getBuildForkPRHead(), is(false));
        assertThat(instance.getBuildForkPRMerge(), is(true));
    }

    @Test
    void basic_server() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getId(),
                is("org.jenkinsci.plugins.github_branch_source.GitHubSCMNavigator"
                        + "::https://github.test/api/v3"
                        + "::cloudbeers"
                        + "::stunning-adventure"));
        assertThat(instance.getApiUri(), is("https://github.test/api/v3"));
        assertThat(instance.getRepoOwner(), is("cloudbeers"));
        assertThat(instance.getRepository(), is("stunning-adventure"));
        assertThat(instance.getCredentialsId(), is("e4d8c11a-0d24-472f-b86b-4b017c160e9a"));
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(false))),
                        Matchers.allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(2))),
                        Matchers.allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class),
                                hasProperty("strategyId", is(2)),
                                hasProperty(
                                        "trust", instanceOf(ForkPullRequestDiscoveryTrait.TrustPermission.class)))));
        // Legacy API
        assertThat(instance.getCheckoutCredentialsId(), is(GitHubSCMSource.DescriptorImpl.SAME));
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(instance.getBuildOriginBranch(), is(true));
        assertThat(instance.getBuildOriginBranchWithPR(), is(false));
        assertThat(instance.getBuildOriginPRHead(), is(true));
        assertThat(instance.getBuildOriginPRMerge(), is(false));
        assertThat(instance.getBuildForkPRHead(), is(true));
        assertThat(instance.getBuildForkPRMerge(), is(false));
    }

    @Test
    void custom_checkout_credentials() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getId(),
                is("org.jenkinsci.plugins.github_branch_source.GitHubSCMNavigator"
                        + "::https://github.test/api/v3"
                        + "::cloudbeers"
                        + "::stunning-adventure"));
        assertThat(instance.getApiUri(), is("https://github.test/api/v3"));
        assertThat(instance.getRepoOwner(), is("cloudbeers"));
        assertThat(instance.getRepository(), is("stunning-adventure"));
        assertThat(instance.getCredentialsId(), is("e4d8c11a-0d24-472f-b86b-4b017c160e9a"));
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(false))),
                        Matchers.allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(2))),
                        Matchers.allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class),
                                hasProperty("strategyId", is(2)),
                                hasProperty("trust", instanceOf(ForkPullRequestDiscoveryTrait.TrustPermission.class))),
                        Matchers.allOf(
                                Matchers.instanceOf(SSHCheckoutTrait.class),
                                hasProperty("credentialsId", is("other-credentials")))));
        // Legacy API
        assertThat(instance.getCheckoutCredentialsId(), is("other-credentials"));
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(instance.getBuildOriginBranch(), is(true));
        assertThat(instance.getBuildOriginBranchWithPR(), is(false));
        assertThat(instance.getBuildOriginPRHead(), is(true));
        assertThat(instance.getBuildOriginPRMerge(), is(false));
        assertThat(instance.getBuildForkPRHead(), is(true));
        assertThat(instance.getBuildForkPRMerge(), is(false));
    }

    @Issue("JENKINS-45467")
    @Test
    void same_checkout_credentials() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getId(),
                is("org.jenkinsci.plugins.github_branch_source.GitHubSCMNavigator"
                        + "::https://github.test/api/v3"
                        + "::cloudbeers"
                        + "::stunning-adventure"));
        assertThat(instance.getApiUri(), is("https://github.test/api/v3"));
        assertThat(instance.getRepoOwner(), is("cloudbeers"));
        assertThat(instance.getRepository(), is("stunning-adventure"));
        assertThat(instance.getCredentialsId(), is("e4d8c11a-0d24-472f-b86b-4b017c160e9a"));
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(false))),
                        Matchers.allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(2))),
                        Matchers.allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class),
                                hasProperty("strategyId", is(2)),
                                hasProperty(
                                        "trust", instanceOf(ForkPullRequestDiscoveryTrait.TrustPermission.class)))));
        // Legacy API
        assertThat(instance.getCheckoutCredentialsId(), is(GitHubSCMSource.DescriptorImpl.SAME));
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(instance.getBuildOriginBranch(), is(true));
        assertThat(instance.getBuildOriginBranchWithPR(), is(false));
        assertThat(instance.getBuildOriginPRHead(), is(true));
        assertThat(instance.getBuildOriginPRMerge(), is(false));
        assertThat(instance.getBuildForkPRHead(), is(true));
        assertThat(instance.getBuildForkPRMerge(), is(false));
    }

    @Test
    void exclude_branches() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getId(),
                is("org.jenkinsci.plugins.github_branch_source.GitHubSCMNavigator"
                        + "::https://api.github.com"
                        + "::cloudbeers"
                        + "::stunning-adventure"));
        assertThat(instance.getApiUri(), is(GitHubSCMSource.GITHUB_URL));
        assertThat(instance.getRepoOwner(), is("cloudbeers"));
        assertThat(instance.getRepository(), is("stunning-adventure"));
        assertThat(instance.getCredentialsId(), is("e4d8c11a-0d24-472f-b86b-4b017c160e9a"));
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(true))),
                        Matchers.allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class),
                                hasProperty("strategyId", is(1)),
                                hasProperty("trust", instanceOf(ForkPullRequestDiscoveryTrait.TrustPermission.class))),
                        Matchers.allOf(
                                instanceOf(WildcardSCMHeadFilterTrait.class),
                                hasProperty("includes", is("*")),
                                hasProperty("excludes", is("master")))));
        // Legacy API
        assertThat(instance.getCheckoutCredentialsId(), is(GitHubSCMSource.DescriptorImpl.SAME));
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is("master"));
        assertThat(instance.getBuildOriginBranch(), is(true));
        assertThat(instance.getBuildOriginBranchWithPR(), is(true));
        assertThat(instance.getBuildOriginPRHead(), is(false));
        assertThat(instance.getBuildOriginPRMerge(), is(false));
        assertThat(instance.getBuildForkPRHead(), is(false));
        assertThat(instance.getBuildForkPRMerge(), is(true));
    }

    @Test
    void limit_branches() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getId(),
                is("org.jenkinsci.plugins.github_branch_source.GitHubSCMNavigator"
                        + "::https://api.github.com"
                        + "::cloudbeers"
                        + "::stunning-adventure"));
        assertThat(instance.getApiUri(), is(GitHubSCMSource.GITHUB_URL));
        assertThat(instance.getRepoOwner(), is("cloudbeers"));
        assertThat(instance.getRepository(), is("stunning-adventure"));
        assertThat(instance.getCredentialsId(), is("e4d8c11a-0d24-472f-b86b-4b017c160e9a"));
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(true))),
                        Matchers.allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class),
                                hasProperty("strategyId", is(1)),
                                hasProperty("trust", instanceOf(ForkPullRequestDiscoveryTrait.TrustPermission.class))),
                        Matchers.allOf(
                                instanceOf(WildcardSCMHeadFilterTrait.class),
                                hasProperty("includes", is("feature/*")),
                                hasProperty("excludes", is("")))));
        // Legacy API
        assertThat(instance.getCheckoutCredentialsId(), is(GitHubSCMSource.DescriptorImpl.SAME));
        assertThat(instance.getIncludes(), is("feature/*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(instance.getBuildOriginBranch(), is(true));
        assertThat(instance.getBuildOriginBranchWithPR(), is(true));
        assertThat(instance.getBuildOriginPRHead(), is(false));
        assertThat(instance.getBuildOriginPRMerge(), is(false));
        assertThat(instance.getBuildForkPRHead(), is(false));
        assertThat(instance.getBuildForkPRMerge(), is(true));
    }

    @Test
    void use_agent_checkout() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getId(),
                is("org.jenkinsci.plugins.github_branch_source.GitHubSCMNavigator"
                        + "::https://api.github.com"
                        + "::cloudbeers"
                        + "::stunning-adventure"));
        assertThat(instance.getApiUri(), is(GitHubSCMSource.GITHUB_URL));
        assertThat(instance.getRepoOwner(), is("cloudbeers"));
        assertThat(instance.getRepository(), is("stunning-adventure"));
        assertThat(instance.getCredentialsId(), is("e4d8c11a-0d24-472f-b86b-4b017c160e9a"));
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(true))),
                        Matchers.allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class),
                                hasProperty("strategyId", is(1)),
                                hasProperty("trust", instanceOf(ForkPullRequestDiscoveryTrait.TrustPermission.class))),
                        Matchers.allOf(
                                Matchers.instanceOf(SSHCheckoutTrait.class),
                                hasProperty("credentialsId", is(nullValue())))));
        // Legacy API
        assertThat(instance.getCheckoutCredentialsId(), is(GitHubSCMSource.DescriptorImpl.ANONYMOUS));
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(instance.getBuildOriginBranch(), is(true));
        assertThat(instance.getBuildOriginBranchWithPR(), is(true));
        assertThat(instance.getBuildOriginPRHead(), is(false));
        assertThat(instance.getBuildOriginPRMerge(), is(false));
        assertThat(instance.getBuildForkPRHead(), is(false));
        assertThat(instance.getBuildForkPRMerge(), is(true));
    }

    @Test
    void given__legacyCode__when__constructor_cloud__then__discoveryTraitDefaults() {
        GitHubSCMSource instance = new GitHubSCMSource(
                "preserve-id",
                null,
                "SAME",
                "e4d8c11a-0d24-472f-b86b-4b017c160e9a",
                "cloudbeers",
                "stunning-adventure");
        assertThat(instance.getId(), is("preserve-id"));
        assertThat(instance.getApiUri(), is(GitHubSCMSource.GITHUB_URL));
        assertThat(instance.getRepoOwner(), is("cloudbeers"));
        assertThat(instance.getRepository(), is("stunning-adventure"));
        assertThat(instance.getCredentialsId(), is("e4d8c11a-0d24-472f-b86b-4b017c160e9a"));
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(true))),
                        Matchers.allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class),
                                hasProperty("strategies", is(EnumSet.of(ChangeRequestCheckoutStrategy.MERGE))),
                                hasProperty(
                                        "trust", instanceOf(ForkPullRequestDiscoveryTrait.TrustPermission.class)))));
        // Legacy API
        assertThat(instance.getCheckoutCredentialsId(), is(GitHubSCMSource.DescriptorImpl.SAME));
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(instance.getBuildOriginBranch(), is(true));
        assertThat(instance.getBuildOriginBranchWithPR(), is(true));
        assertThat(instance.getBuildOriginPRHead(), is(false));
        assertThat(instance.getBuildOriginPRMerge(), is(false));
        assertThat(instance.getBuildForkPRHead(), is(false));
        assertThat(instance.getBuildForkPRMerge(), is(true));
    }

    @Test
    void given__legacyCode__when__constructor_server__then__discoveryTraitDefaults() {
        GitHubSCMSource instance = new GitHubSCMSource(
                null,
                "https://github.test/api/v3",
                "8b2e4f77-39c5-41a9-b63b-8d367350bfdf",
                "e4d8c11a-0d24-472f-b86b-4b017c160e9a",
                "cloudbeers",
                "stunning-adventure");
        assertThat(instance.getId(), is(notNullValue()));
        assertThat(instance.getApiUri(), is("https://github.test/api/v3"));
        assertThat(instance.getRepoOwner(), is("cloudbeers"));
        assertThat(instance.getRepository(), is("stunning-adventure"));
        assertThat(instance.getCredentialsId(), is("e4d8c11a-0d24-472f-b86b-4b017c160e9a"));
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(true))),
                        Matchers.allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class),
                                hasProperty("strategies", is(EnumSet.of(ChangeRequestCheckoutStrategy.MERGE))),
                                hasProperty("trust", instanceOf(ForkPullRequestDiscoveryTrait.TrustPermission.class))),
                        Matchers.allOf(
                                Matchers.instanceOf(SSHCheckoutTrait.class),
                                hasProperty("credentialsId", is("8b2e4f77-39c5-41a9-b63b-8d367350bfdf")))));
        // Legacy API
        assertThat(instance.getCheckoutCredentialsId(), is("8b2e4f77-39c5-41a9-b63b-8d367350bfdf"));
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(instance.getBuildOriginBranch(), is(true));
        assertThat(instance.getBuildOriginBranchWithPR(), is(true));
        assertThat(instance.getBuildOriginPRHead(), is(false));
        assertThat(instance.getBuildOriginPRMerge(), is(false));
        assertThat(instance.getBuildForkPRHead(), is(false));
        assertThat(instance.getBuildForkPRMerge(), is(true));
    }

    @Test
    void given__instance__when__setTraits_empty__then__traitsEmpty() {
        GitHubSCMSource instance = new GitHubSCMSource("testing", "test-repo");
        instance.setTraits(Collections.emptyList());
        assertThat(instance.getTraits(), is(Collections.<SCMSourceTrait>emptyList()));
    }

    @Test
    void given__legacyCode__when__setBuildOriginBranch__then__traitsMaintained() {
        GitHubSCMSource instance = new GitHubSCMSource("testing", "test-repo");
        instance.setTraits(Collections.emptyList());
        assertThat(instance.getTraits(), is(Collections.<SCMSourceTrait>emptyList()));
        instance.setBuildOriginBranch(true);
        assertThat(instance.getTraits(), contains(instanceOf(BranchDiscoveryTrait.class)));
        instance.setBuildOriginBranch(false);
        assertThat(instance.getTraits(), is(Collections.<SCMSourceTrait>emptyList()));

        instance.setBuildOriginBranchWithPR(true);
        assertThat(instance.getTraits(), contains(instanceOf(BranchDiscoveryTrait.class)));
        instance.setBuildOriginBranchWithPR(false);
        assertThat(instance.getTraits(), is(Collections.<SCMSourceTrait>emptyList()));

        instance.setBuildOriginBranchWithPR(true);
        instance.setBuildOriginBranch(true);
        assertThat(instance.getTraits(), contains(instanceOf(BranchDiscoveryTrait.class)));
        instance.setBuildOriginBranchWithPR(false);
        assertThat(instance.getTraits(), contains(instanceOf(BranchDiscoveryTrait.class)));
        instance.setBuildOriginBranch(false);
        assertThat(instance.getTraits(), is(Collections.<SCMSourceTrait>emptyList()));

        instance.setBuildOriginBranchWithPR(true);
        instance.setBuildOriginBranch(true);
        assertThat(instance.getTraits(), contains(instanceOf(BranchDiscoveryTrait.class)));
        instance.setBuildOriginBranch(false);
        assertThat(instance.getTraits(), contains(instanceOf(BranchDiscoveryTrait.class)));
        instance.setBuildOriginBranchWithPR(false);
        assertThat(instance.getTraits(), is(Collections.<SCMSourceTrait>emptyList()));
    }

    @Test
    void given__instance__when__setTraits__then__traitsSet() {
        GitHubSCMSource instance = new GitHubSCMSource("testing", "test-repo");
        instance.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(BranchDiscoveryTrait.EXCLUDE_PRS), new SSHCheckoutTrait("value")));
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(false))),
                        Matchers.allOf(instanceOf(SSHCheckoutTrait.class), hasProperty("credentialsId", is("value")))));
    }

    @Test
    void given__instance__when__setApiUri__then__valueSet() {
        GitHubSCMSource instance = new GitHubSCMSource("testing", "test-repo");
        assertThat("initial default", instance.getApiUri(), is(GitHubSCMSource.GITHUB_URL));
        instance.setApiUri("https://github.test/api/v3");
        assertThat(instance.getApiUri(), is("https://github.test/api/v3"));
        instance.setApiUri(null);
        assertThat(instance.getApiUri(), is(GitHubSCMSource.GITHUB_URL));
    }

    @Test
    void given__instance__when__setCredentials_empty__then__credentials_null() {
        GitHubSCMSource instance = new GitHubSCMSource("testing", "test-repo");
        instance.setCredentialsId("");
        assertThat(instance.getCredentialsId(), is(nullValue()));
    }

    @Test
    void given__instance__when__setCredentials_null__then__credentials_null() {
        GitHubSCMSource instance = new GitHubSCMSource("testing", "test-repo");
        instance.setCredentialsId("");
        assertThat(instance.getCredentialsId(), is(nullValue()));
    }

    @Test
    void given__instance__when__setCredentials__then__credentials_set() {
        GitHubSCMSource instance = new GitHubSCMSource("testing", "test-repo");
        instance.setCredentialsId("test");
        assertThat(instance.getCredentialsId(), is("test"));
    }

    @Test
    void given__legacyCode_withoutExcludes__when__setIncludes_default__then__traitRemoved() {
        GitHubSCMSource instance = new GitHubSCMSource("testing", "test-repo");
        instance.setTraits(
                Arrays.asList(new BranchDiscoveryTrait(true, false), new WildcardSCMHeadFilterTrait("feature/*", "")));
        assertThat(instance.getIncludes(), is("feature/*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(
                instance.getTraits(),
                Matchers.hasItem(allOf(
                        instanceOf(WildcardSCMHeadFilterTrait.class),
                        hasProperty("includes", is("feature/*")),
                        hasProperty("excludes", is("")))));
        instance.setIncludes("*");
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(instance.getTraits(), not(Matchers.hasItem(instanceOf(WildcardSCMHeadFilterTrait.class))));
    }

    @Test
    void given__legacyCode_withoutExcludes__when__setIncludes_value__then__traitUpdated() {
        GitHubSCMSource instance = new GitHubSCMSource("testing", "test-repo");
        instance.setTraits(
                Arrays.asList(new BranchDiscoveryTrait(true, false), new WildcardSCMHeadFilterTrait("feature/*", "")));
        assertThat(instance.getIncludes(), is("feature/*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(
                instance.getTraits(),
                Matchers.hasItem(allOf(
                        instanceOf(WildcardSCMHeadFilterTrait.class),
                        hasProperty("includes", is("feature/*")),
                        hasProperty("excludes", is("")))));
        instance.setIncludes("bug/*");
        assertThat(instance.getIncludes(), is("bug/*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(
                instance.getTraits(),
                Matchers.hasItem(allOf(
                        instanceOf(WildcardSCMHeadFilterTrait.class),
                        hasProperty("includes", is("bug/*")),
                        hasProperty("excludes", is("")))));
    }

    @Test
    void given__legacyCode_withoutTrait__when__setIncludes_value__then__traitAdded() {
        GitHubSCMSource instance = new GitHubSCMSource("testing", "test-repo");
        instance.setTraits(Arrays.asList(new BranchDiscoveryTrait(true, false), new SSHCheckoutTrait("someValue")));
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(instance.getTraits(), not(Matchers.hasItem(instanceOf(WildcardSCMHeadFilterTrait.class))));
        instance.setIncludes("feature/*");
        assertThat(instance.getIncludes(), is("feature/*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(
                instance.getTraits(),
                Matchers.hasItem(allOf(
                        instanceOf(WildcardSCMHeadFilterTrait.class),
                        hasProperty("includes", is("feature/*")),
                        hasProperty("excludes", is("")))));
    }

    @Test
    void given__legacyCode_withExcludes__when__setIncludes_default__then__traitUpdated() {
        GitHubSCMSource instance = new GitHubSCMSource("testing", "test-repo");
        instance.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, false),
                new WildcardSCMHeadFilterTrait("feature/*", "feature/ignore"),
                new SSHCheckoutTrait("someValue")));
        assertThat(instance.getIncludes(), is("feature/*"));
        assertThat(instance.getExcludes(), is("feature/ignore"));
        assertThat(
                instance.getTraits(),
                Matchers.hasItem(allOf(
                        instanceOf(WildcardSCMHeadFilterTrait.class),
                        hasProperty("includes", is("feature/*")),
                        hasProperty("excludes", is("feature/ignore")))));
        instance.setIncludes("*");
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is("feature/ignore"));
        assertThat(
                instance.getTraits(),
                Matchers.hasItem(allOf(
                        instanceOf(WildcardSCMHeadFilterTrait.class),
                        hasProperty("includes", is("*")),
                        hasProperty("excludes", is("feature/ignore")))));
    }

    @Test
    void given__legacyCode_withExcludes__when__setIncludes_value__then__traitUpdated() {
        GitHubSCMSource instance = new GitHubSCMSource("testing", "test-repo");
        instance.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, false),
                new WildcardSCMHeadFilterTrait("feature/*", "feature/ignore"),
                new SSHCheckoutTrait("someValue")));
        assertThat(instance.getIncludes(), is("feature/*"));
        assertThat(instance.getExcludes(), is("feature/ignore"));
        assertThat(
                instance.getTraits(),
                Matchers.hasItem(allOf(
                        instanceOf(WildcardSCMHeadFilterTrait.class),
                        hasProperty("includes", is("feature/*")),
                        hasProperty("excludes", is("feature/ignore")))));
        instance.setIncludes("bug/*");
        assertThat(instance.getIncludes(), is("bug/*"));
        assertThat(instance.getExcludes(), is("feature/ignore"));
        assertThat(
                instance.getTraits(),
                Matchers.hasItem(allOf(
                        instanceOf(WildcardSCMHeadFilterTrait.class),
                        hasProperty("includes", is("bug/*")),
                        hasProperty("excludes", is("feature/ignore")))));
    }

    @Test
    void given__legacyCode_withoutIncludes__when__setExcludes_default__then__traitRemoved() {
        GitHubSCMSource instance = new GitHubSCMSource("testing", "test-repo");
        instance.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, false),
                new WildcardSCMHeadFilterTrait("*", "feature/ignore"),
                new SSHCheckoutTrait("someValue")));
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is("feature/ignore"));
        assertThat(
                instance.getTraits(),
                Matchers.hasItem(allOf(
                        instanceOf(WildcardSCMHeadFilterTrait.class),
                        hasProperty("includes", is("*")),
                        hasProperty("excludes", is("feature/ignore")))));
        instance.setExcludes("");
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(instance.getTraits(), not(Matchers.hasItem(instanceOf(WildcardSCMHeadFilterTrait.class))));
    }

    @Test
    void given__legacyCode_withoutIncludes__when__setExcludes_value__then__traitUpdated() {
        GitHubSCMSource instance = new GitHubSCMSource("testing", "test-repo");
        instance.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, false),
                new WildcardSCMHeadFilterTrait("*", "feature/ignore"),
                new SSHCheckoutTrait("someValue")));
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is("feature/ignore"));
        assertThat(
                instance.getTraits(),
                Matchers.hasItem(allOf(
                        instanceOf(WildcardSCMHeadFilterTrait.class),
                        hasProperty("includes", is("*")),
                        hasProperty("excludes", is("feature/ignore")))));
        instance.setExcludes("bug/ignore");
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is("bug/ignore"));
        assertThat(
                instance.getTraits(),
                Matchers.hasItem(allOf(
                        instanceOf(WildcardSCMHeadFilterTrait.class),
                        hasProperty("includes", is("*")),
                        hasProperty("excludes", is("bug/ignore")))));
    }

    @Test
    void given__legacyCode_withoutTrait__when__setExcludes_value__then__traitAdded() {
        GitHubSCMSource instance = new GitHubSCMSource("testing", "test-repo");
        instance.setTraits(Arrays.asList(new BranchDiscoveryTrait(true, false), new SSHCheckoutTrait("someValue")));
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(instance.getTraits(), not(Matchers.hasItem(instanceOf(WildcardSCMHeadFilterTrait.class))));
        instance.setExcludes("feature/ignore");
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is("feature/ignore"));
        assertThat(
                instance.getTraits(),
                Matchers.hasItem(allOf(
                        instanceOf(WildcardSCMHeadFilterTrait.class),
                        hasProperty("includes", is("*")),
                        hasProperty("excludes", is("feature/ignore")))));
    }

    @Test
    void given__legacyCode_withIncludes__when__setExcludes_default__then__traitUpdated() {
        GitHubSCMSource instance = new GitHubSCMSource("testing", "test-repo");
        instance.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, false),
                new WildcardSCMHeadFilterTrait("feature/*", "feature/ignore"),
                new SSHCheckoutTrait("someValue")));
        assertThat(instance.getIncludes(), is("feature/*"));
        assertThat(instance.getExcludes(), is("feature/ignore"));
        assertThat(
                instance.getTraits(),
                Matchers.hasItem(allOf(
                        instanceOf(WildcardSCMHeadFilterTrait.class),
                        hasProperty("includes", is("feature/*")),
                        hasProperty("excludes", is("feature/ignore")))));
        instance.setExcludes("");
        assertThat(instance.getIncludes(), is("feature/*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(
                instance.getTraits(),
                Matchers.hasItem(allOf(
                        instanceOf(WildcardSCMHeadFilterTrait.class),
                        hasProperty("includes", is("feature/*")),
                        hasProperty("excludes", is("")))));
    }

    @Test
    void given__legacyCode_withIncludes__when__setExcludes_value__then__traitUpdated() {
        GitHubSCMSource instance = new GitHubSCMSource("testing", "test-repo");
        instance.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, false),
                new WildcardSCMHeadFilterTrait("feature/*", ""),
                new SSHCheckoutTrait("someValue")));
        assertThat(instance.getIncludes(), is("feature/*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(
                instance.getTraits(),
                Matchers.hasItem(allOf(
                        instanceOf(WildcardSCMHeadFilterTrait.class),
                        hasProperty("includes", is("feature/*")),
                        hasProperty("excludes", is("")))));
        instance.setExcludes("feature/ignore");
        assertThat(instance.getIncludes(), is("feature/*"));
        assertThat(instance.getExcludes(), is("feature/ignore"));
        assertThat(
                instance.getTraits(),
                Matchers.hasItem(allOf(
                        instanceOf(WildcardSCMHeadFilterTrait.class),
                        hasProperty("includes", is("feature/*")),
                        hasProperty("excludes", is("feature/ignore")))));
    }

    @Test
    void build_000000() {
        GitHubSCMSource instance = load();
        assertThat(instance.getTraits(), empty());
    }

    @Test
    void build_000001() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                contains(Matchers.allOf(
                        instanceOf(ForkPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(2)))));
    }

    @Test
    void build_000010() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                contains(Matchers.allOf(
                        instanceOf(ForkPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(1)))));
    }

    @Test
    void build_000011() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                contains(Matchers.allOf(
                        instanceOf(ForkPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(3)))));
    }

    @Test
    void build_000100() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                contains(Matchers.allOf(
                        instanceOf(OriginPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(2)))));
    }

    @Test
    void build_000101() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(2))),
                        Matchers.allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(2)))));
    }

    @Test
    void build_000110() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(2))),
                        Matchers.allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(1)))));
    }

    @Test
    void build_000111() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(2))),
                        Matchers.allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(3)))));
    }

    @Test
    void build_001000() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                contains(Matchers.allOf(
                        instanceOf(OriginPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(1)))));
    }

    @Test
    void build_001001() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(1))),
                        Matchers.allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(2)))));
    }

    @Test
    void build_001010() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(1))),
                        Matchers.allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(1)))));
    }

    @Test
    void build_001011() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(1))),
                        Matchers.allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(3)))));
    }

    @Test
    void build_001100() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                contains(Matchers.allOf(
                        instanceOf(OriginPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(3)))));
    }

    @Test
    void build_001101() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(3))),
                        Matchers.allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(2)))));
    }

    @Test
    void build_001110() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(3))),
                        Matchers.allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(1)))));
    }

    @Test
    void build_001111() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(3))),
                        Matchers.allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(3)))));
    }

    @Test
    void build_010000() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                contains(Matchers.allOf(
                        instanceOf(BranchDiscoveryTrait.class),
                        hasProperty("buildBranch", is(false)),
                        hasProperty("buildBranchesWithPR", is(true)))));
    }

    @Test
    void build_010001() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(false)),
                                hasProperty("buildBranchesWithPR", is(true))),
                        Matchers.allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(2)))));
    }

    @Test
    void build_010010() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(false)),
                                hasProperty("buildBranchesWithPR", is(true))),
                        Matchers.allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(1)))));
    }

    @Test
    void build_010011() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(false)),
                                hasProperty("buildBranchesWithPR", is(true))),
                        Matchers.allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(3)))));
    }

    @Test
    void build_010100() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(false)),
                                hasProperty("buildBranchesWithPR", is(true))),
                        Matchers.allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(2)))));
    }

    @Test
    void build_010101() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(false)),
                                hasProperty("buildBranchesWithPR", is(true))),
                        Matchers.allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(2))),
                        Matchers.allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(2)))));
    }

    @Test
    void build_010110() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(false)),
                                hasProperty("buildBranchesWithPR", is(true))),
                        Matchers.allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(2))),
                        Matchers.allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(1)))));
    }

    @Test
    void build_010111() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(false)),
                                hasProperty("buildBranchesWithPR", is(true))),
                        Matchers.allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(2))),
                        Matchers.allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(3)))));
    }

    @Test
    void build_011000() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(false)),
                                hasProperty("buildBranchesWithPR", is(true))),
                        Matchers.allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(1)))));
    }

    @Test
    void build_011001() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(false)),
                                hasProperty("buildBranchesWithPR", is(true))),
                        Matchers.allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(1))),
                        Matchers.allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(2)))));
    }

    @Test
    void build_011010() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(false)),
                                hasProperty("buildBranchesWithPR", is(true))),
                        Matchers.allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(1))),
                        Matchers.allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(1)))));
    }

    @Test
    void build_011011() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(false)),
                                hasProperty("buildBranchesWithPR", is(true))),
                        Matchers.allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(1))),
                        Matchers.allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(3)))));
    }

    @Test
    void build_011100() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(false)),
                                hasProperty("buildBranchesWithPR", is(true))),
                        Matchers.allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(3)))));
    }

    @Test
    void build_011101() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(false)),
                                hasProperty("buildBranchesWithPR", is(true))),
                        Matchers.allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(3))),
                        Matchers.allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(2)))));
    }

    @Test
    void build_011110() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(false)),
                                hasProperty("buildBranchesWithPR", is(true))),
                        Matchers.allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(3))),
                        Matchers.allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(1)))));
    }

    @Test
    void build_011111() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(false)),
                                hasProperty("buildBranchesWithPR", is(true))),
                        Matchers.allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(3))),
                        Matchers.allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(3)))));
    }

    @Test
    void build_100000() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                contains(Matchers.allOf(
                        instanceOf(BranchDiscoveryTrait.class),
                        hasProperty("buildBranch", is(true)),
                        hasProperty("buildBranchesWithPR", is(false)))));
    }

    @Test
    void build_100001() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(false))),
                        Matchers.allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(2)))));
    }

    @Test
    void build_100010() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(false))),
                        Matchers.allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(1)))));
    }

    @Test
    void build_100011() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(false))),
                        Matchers.allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(3)))));
    }

    @Test
    void build_100100() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(false))),
                        Matchers.allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(2)))));
    }

    @Test
    void build_100101() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(false))),
                        Matchers.allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(2))),
                        Matchers.allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(2)))));
    }

    @Test
    void build_100110() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(false))),
                        Matchers.allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(2))),
                        Matchers.allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(1)))));
    }

    @Test
    void build_100111() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(false))),
                        Matchers.allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(2))),
                        Matchers.allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(3)))));
    }

    @Test
    void build_101000() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(false))),
                        Matchers.allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(1)))));
    }

    @Test
    void build_101001() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(false))),
                        Matchers.allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(1))),
                        Matchers.allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(2)))));
    }

    @Test
    void build_101010() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(false))),
                        Matchers.allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(1))),
                        Matchers.allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(1)))));
    }

    @Test
    void build_101011() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(false))),
                        Matchers.allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(1))),
                        Matchers.allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(3)))));
    }

    @Test
    void build_101100() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(false))),
                        Matchers.allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(3)))));
    }

    @Test
    void build_101101() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(false))),
                        Matchers.allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(3))),
                        Matchers.allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(2)))));
    }

    @Test
    void build_101110() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(false))),
                        Matchers.allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(3))),
                        Matchers.allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(1)))));
    }

    @Test
    void build_101111() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(false))),
                        Matchers.allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(3))),
                        Matchers.allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(3)))));
    }

    @Test
    void build_110000() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                contains(Matchers.allOf(
                        instanceOf(BranchDiscoveryTrait.class),
                        hasProperty("buildBranch", is(true)),
                        hasProperty("buildBranchesWithPR", is(true)))));
    }

    @Test
    void build_110001() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(true))),
                        Matchers.allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(2)))));
    }

    @Test
    void build_110010() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(true))),
                        Matchers.allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(1)))));
    }

    @Test
    void build_110011() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(true))),
                        Matchers.allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(3)))));
    }

    @Test
    void build_110100() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(true))),
                        Matchers.allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(2)))));
    }

    @Test
    void build_110101() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(true))),
                        Matchers.allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(2))),
                        Matchers.allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(2)))));
    }

    @Test
    void build_110110() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(true))),
                        Matchers.allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(2))),
                        Matchers.allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(1)))));
    }

    @Test
    void build_110111() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(true))),
                        Matchers.allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(2))),
                        Matchers.allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(3)))));
    }

    @Test
    void build_111000() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(true))),
                        Matchers.allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(1)))));
    }

    @Test
    void build_111001() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(true))),
                        Matchers.allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(1))),
                        Matchers.allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(2)))));
    }

    @Test
    void build_111010() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(true))),
                        Matchers.allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(1))),
                        Matchers.allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(1)))));
    }

    @Test
    void build_111011() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(true))),
                        Matchers.allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(1))),
                        Matchers.allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(3)))));
    }

    @Test
    void build_111100() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(true))),
                        Matchers.allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(3)))));
    }

    @Test
    void build_111101() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(true))),
                        Matchers.allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(3))),
                        Matchers.allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(2)))));
    }

    @Test
    void build_111110() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(true))),
                        Matchers.allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(3))),
                        Matchers.allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(1)))));
    }

    @Test
    void build_111111() {
        GitHubSCMSource instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        Matchers.allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(true))),
                        Matchers.allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(3))),
                        Matchers.allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(3)))));
    }
}
