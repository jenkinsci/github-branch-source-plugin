package org.jenkinsci.plugins.github_branch_source;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import jenkins.model.Jenkins;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import jenkins.scm.api.trait.SCMTrait;
import jenkins.scm.impl.trait.RegexSCMSourceFilterTrait;
import jenkins.scm.impl.trait.WildcardSCMHeadFilterTrait;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.WithoutJenkins;

public class GitHubSCMNavigatorTraitsTest {
    @ClassRule
    public static JenkinsRule j = new JenkinsRule();

    @Rule
    public TestName currentTestName = new TestName();

    private GitHubSCMNavigator load() {
        return load(currentTestName.getMethodName());
    }

    private GitHubSCMNavigator load(String dataSet) {
        return (GitHubSCMNavigator)
                Jenkins.XSTREAM2.fromXML(getClass().getResource(getClass().getSimpleName() + "/" + dataSet + ".xml"));
    }

    private static Matcher<SCMTrait<? extends SCMTrait<?>>> branchDiscoveryTraitItem(
            boolean buildBranch, boolean buildBranchesWithPR) {
        return allOf(
                instanceOf(BranchDiscoveryTrait.class),
                hasProperty("buildBranch", is(buildBranch)),
                hasProperty("buildBranchesWithPR", is(buildBranchesWithPR)));
    }

    private static Matcher<SCMTrait<?>> sshCheckoutTraitItem(Matcher<Object> credentialsMatcher) {
        return Matchers.<SCMTrait<?>>allOf(
                Matchers.instanceOf(SSHCheckoutTrait.class), hasProperty("credentialsId", credentialsMatcher));
    }

    private static Matcher<SCMTrait<?>> sshCheckoutTraitItem(String credentialsId) {
        return sshCheckoutTraitItem(is(credentialsId));
    }

    private static Matcher<SCMTrait<? extends SCMTrait<?>>> forkPullRequestDiscoveryTraitItem(int strategyId) {
        return allOf(instanceOf(ForkPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(strategyId)));
    }

    private static <T> Matcher<SCMTrait<? extends SCMTrait<?>>> forkPullRequestDiscoveryTraitItem(
            int strategyId, Matcher<T> trust) {
        return allOf(
                instanceOf(ForkPullRequestDiscoveryTrait.class),
                hasProperty("strategyId", is(strategyId)),
                hasProperty("trust", trust));
    }

    private static Matcher<SCMTrait<? extends SCMTrait<?>>> originPullRequestDiscoveryTraitItem(int strategyId) {
        return allOf(instanceOf(OriginPullRequestDiscoveryTrait.class), hasProperty("strategyId", is(strategyId)));
    }

    private static Matcher<Object> regexSCMSourceFilterTraitItem(String pattern) {
        return allOf(instanceOf(RegexSCMSourceFilterTrait.class), hasProperty("regex", is(pattern)));
    }

    private static Matcher<Object> wildcardSCMHeadFilterTraitItem(String includes, String excludes) {
        return allOf(
                instanceOf(WildcardSCMHeadFilterTrait.class),
                hasProperty("includes", is(includes)),
                hasProperty("excludes", is(excludes)));
    }

    @Test
    public void modern() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(instance.id(), is("https://api.github.com::cloudbeers"));
        assertThat(instance.getRepoOwner(), is("cloudbeers"));
        assertThat(instance.getApiUri(), is(nullValue()));
        assertThat(instance.getCredentialsId(), is("bcaef157-f105-407f-b150-df7722eab6c1"));
        assertThat(instance.getTraits(), is(Collections.<SCMTrait<?>>emptyList()));
    }

    @Test
    public void basic_cloud() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(instance.id(), is("https://api.github.com::cloudbeers"));
        assertThat(instance.getRepoOwner(), is("cloudbeers"));
        assertThat(instance.getApiUri(), is(nullValue()));
        assertThat(instance.getCredentialsId(), is("bcaef157-f105-407f-b150-df7722eab6c1"));
        assertThat(
                "SAME checkout credentials should mean no checkout trait",
                instance.getTraits(),
                not(hasItem(instanceOf(SSHCheckoutTrait.class))));
        assertThat(
                ".* as a pattern should mean no RegexSCMSourceFilterTrait",
                instance.getTraits(),
                not(hasItem(instanceOf(RegexSCMSourceFilterTrait.class))));
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        branchDiscoveryTraitItem(true, true),
                        originPullRequestDiscoveryTraitItem(2),
                        forkPullRequestDiscoveryTraitItem(
                                2, instanceOf(ForkPullRequestDiscoveryTrait.TrustPermission.class))));
        // legacy API
        assertThat(instance.getCheckoutCredentialsId(), is("SAME"));
        assertThat(instance.getPattern(), is(".*"));
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is(""));
    }

    @Test
    public void basic_server() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(instance.id(), is("https://github.test/api/v3::cloudbeers"));
        assertThat(instance.getRepoOwner(), is("cloudbeers"));
        assertThat(instance.getApiUri(), is("https://github.test/api/v3"));
        assertThat(instance.getCredentialsId(), is("bcaef157-f105-407f-b150-df7722eab6c1"));
        assertThat(
                "checkout credentials should mean checkout trait",
                instance.getTraits(),
                hasItem(sshCheckoutTraitItem("8b2e4f77-39c5-41a9-b63b-8d367350bfdf")));
        assertThat(
                ".* as a pattern should mean no RegexSCMSourceFilterTrait",
                instance.getTraits(),
                not(hasItem(instanceOf(RegexSCMSourceFilterTrait.class))));
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        branchDiscoveryTraitItem(true, true),
                        originPullRequestDiscoveryTraitItem(2),
                        forkPullRequestDiscoveryTraitItem(
                                2, instanceOf(ForkPullRequestDiscoveryTrait.TrustPermission.class)),
                        sshCheckoutTraitItem("8b2e4f77-39c5-41a9-b63b-8d367350bfdf")));
        // legacy API
        assertThat(instance.getCheckoutCredentialsId(), is("8b2e4f77-39c5-41a9-b63b-8d367350bfdf"));
        assertThat(instance.getPattern(), is(".*"));
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is(""));
    }

    @Test
    public void use_agent_checkout() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(instance.id(), is("https://github.test/api/v3::cloudbeers"));
        assertThat(instance.getRepoOwner(), is("cloudbeers"));
        assertThat(instance.getApiUri(), is("https://github.test/api/v3"));
        assertThat(instance.getCredentialsId(), is("bcaef157-f105-407f-b150-df7722eab6c1"));
        assertThat(
                "checkout credentials should mean checkout trait",
                instance.getTraits(),
                hasItem(sshCheckoutTraitItem(nullValue())));
        assertThat(
                ".* as a pattern should mean no RegexSCMSourceFilterTrait",
                instance.getTraits(),
                not(hasItem(instanceOf(RegexSCMSourceFilterTrait.class))));
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        branchDiscoveryTraitItem(true, true),
                        originPullRequestDiscoveryTraitItem(2),
                        forkPullRequestDiscoveryTraitItem(
                                2, instanceOf(ForkPullRequestDiscoveryTrait.TrustPermission.class)),
                        sshCheckoutTraitItem(nullValue())));
        // legacy API
        assertThat(instance.getCheckoutCredentialsId(), is(GitHubSCMSource.DescriptorImpl.ANONYMOUS));
        assertThat(instance.getPattern(), is(".*"));
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is(""));
    }

    @Issue("JENKINS-45467")
    @Test
    public void same_checkout_credentials() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(instance.id(), is("https://github.test/api/v3::cloudbeers"));
        assertThat(instance.getRepoOwner(), is("cloudbeers"));
        assertThat(instance.getApiUri(), is("https://github.test/api/v3"));
        assertThat(instance.getCredentialsId(), is("bcaef157-f105-407f-b150-df7722eab6c1"));
        assertThat(
                "checkout credentials equal to scan should mean no checkout trait",
                instance.getTraits(),
                not(hasItem(sshCheckoutTraitItem(nullValue()))));
        assertThat(
                ".* as a pattern should mean no RegexSCMSourceFilterTrait",
                instance.getTraits(),
                not(hasItem(instanceOf(RegexSCMSourceFilterTrait.class))));
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        branchDiscoveryTraitItem(true, true),
                        originPullRequestDiscoveryTraitItem(2),
                        forkPullRequestDiscoveryTraitItem(
                                2, instanceOf(ForkPullRequestDiscoveryTrait.TrustPermission.class))));
        // legacy API
        assertThat(instance.getCheckoutCredentialsId(), is(GitHubSCMSource.DescriptorImpl.SAME));
        assertThat(instance.getPattern(), is(".*"));
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is(""));
    }

    @Test
    public void limit_repositories() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(instance.id(), is("https://github.test/api/v3::cloudbeers"));
        assertThat(instance.getRepoOwner(), is("cloudbeers"));
        assertThat(instance.getApiUri(), is("https://github.test/api/v3"));
        assertThat(instance.getCredentialsId(), is("bcaef157-f105-407f-b150-df7722eab6c1"));
        assertThat(
                "checkout credentials should mean checkout trait",
                instance.getTraits(),
                hasItem(sshCheckoutTraitItem("8b2e4f77-39c5-41a9-b63b-8d367350bfdf")));
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        branchDiscoveryTraitItem(true, true),
                        originPullRequestDiscoveryTraitItem(2),
                        forkPullRequestDiscoveryTraitItem(
                                2, instanceOf(ForkPullRequestDiscoveryTrait.TrustPermission.class)),
                        sshCheckoutTraitItem("8b2e4f77-39c5-41a9-b63b-8d367350bfdf"),
                        allOf(instanceOf(RegexSCMSourceFilterTrait.class), hasProperty("regex", is("limited.*")))));
        // legacy API
        assertThat(instance.getCheckoutCredentialsId(), is("8b2e4f77-39c5-41a9-b63b-8d367350bfdf"));
        assertThat(instance.getPattern(), is("limited.*"));
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is(""));
    }

    @Test
    public void exclude_branches() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(instance.id(), is("https://api.github.com::cloudbeers"));
        assertThat(instance.getRepoOwner(), is("cloudbeers"));
        assertThat(instance.getApiUri(), is(nullValue()));
        assertThat(instance.getCredentialsId(), is("bcaef157-f105-407f-b150-df7722eab6c1"));
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        branchDiscoveryTraitItem(true, true),
                        originPullRequestDiscoveryTraitItem(2),
                        forkPullRequestDiscoveryTraitItem(
                                2, instanceOf(ForkPullRequestDiscoveryTrait.TrustPermission.class)),
                        wildcardSCMHeadFilterTraitItem("*", "master")));
        // legacy API
        assertThat(instance.getCheckoutCredentialsId(), is("SAME"));
        assertThat(instance.getPattern(), is(".*"));
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is("master"));
    }

    @Test
    public void limit_branches() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(instance.id(), is("https://api.github.com::cloudbeers"));
        assertThat(instance.getRepoOwner(), is("cloudbeers"));
        assertThat(instance.getApiUri(), is(nullValue()));
        assertThat(instance.getCredentialsId(), is("bcaef157-f105-407f-b150-df7722eab6c1"));
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        branchDiscoveryTraitItem(true, true),
                        originPullRequestDiscoveryTraitItem(2),
                        forkPullRequestDiscoveryTraitItem(
                                2, instanceOf(ForkPullRequestDiscoveryTrait.TrustPermission.class)),
                        wildcardSCMHeadFilterTraitItem("feature/*", "")));
        // legacy API
        assertThat(instance.getCheckoutCredentialsId(), is("SAME"));
        assertThat(instance.getPattern(), is(".*"));
        assertThat(instance.getIncludes(), is("feature/*"));
        assertThat(instance.getExcludes(), is(""));
    }

    @Test
    public void build_000000() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(instance.getTraits(), empty());
    }

    @Test
    public void build_000001() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(instance.getTraits(), contains(forkPullRequestDiscoveryTraitItem(2)));
    }

    @Test
    public void build_000010() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(instance.getTraits(), contains(forkPullRequestDiscoveryTraitItem(1)));
    }

    @Test
    public void build_000011() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(instance.getTraits(), contains(forkPullRequestDiscoveryTraitItem(3)));
    }

    @Test
    public void build_000100() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(instance.getTraits(), contains(originPullRequestDiscoveryTraitItem(2)));
    }

    @Test
    public void build_000101() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(originPullRequestDiscoveryTraitItem(2), forkPullRequestDiscoveryTraitItem(2)));
    }

    @Test
    public void build_000110() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(originPullRequestDiscoveryTraitItem(2), forkPullRequestDiscoveryTraitItem(1)));
    }

    @Test
    public void build_000111() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(originPullRequestDiscoveryTraitItem(2), forkPullRequestDiscoveryTraitItem(3)));
    }

    @Test
    public void build_001000() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(instance.getTraits(), contains(originPullRequestDiscoveryTraitItem(1)));
    }

    @Test
    public void build_001001() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(originPullRequestDiscoveryTraitItem(1), forkPullRequestDiscoveryTraitItem(2)));
    }

    @Test
    public void build_001010() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(originPullRequestDiscoveryTraitItem(1), forkPullRequestDiscoveryTraitItem(1)));
    }

    @Test
    public void build_001011() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(originPullRequestDiscoveryTraitItem(1), forkPullRequestDiscoveryTraitItem(3)));
    }

    @Test
    public void build_001100() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(instance.getTraits(), contains(originPullRequestDiscoveryTraitItem(3)));
    }

    @Test
    public void build_001101() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(originPullRequestDiscoveryTraitItem(3), forkPullRequestDiscoveryTraitItem(2)));
    }

    @Test
    public void build_001110() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(originPullRequestDiscoveryTraitItem(3), forkPullRequestDiscoveryTraitItem(1)));
    }

    @Test
    public void build_001111() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(originPullRequestDiscoveryTraitItem(3), forkPullRequestDiscoveryTraitItem(3)));
    }

    @Test
    public void build_010000() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(instance.getTraits(), contains(branchDiscoveryTraitItem(false, true)));
    }

    @Test
    public void build_010001() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(branchDiscoveryTraitItem(false, true), forkPullRequestDiscoveryTraitItem(2)));
    }

    @Test
    public void build_010010() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(branchDiscoveryTraitItem(false, true), forkPullRequestDiscoveryTraitItem(1)));
    }

    @Test
    public void build_010011() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(branchDiscoveryTraitItem(false, true), forkPullRequestDiscoveryTraitItem(3)));
    }

    @Test
    public void build_010100() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(branchDiscoveryTraitItem(false, true), originPullRequestDiscoveryTraitItem(2)));
    }

    @Test
    public void build_010101() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        branchDiscoveryTraitItem(false, true),
                        originPullRequestDiscoveryTraitItem(2),
                        forkPullRequestDiscoveryTraitItem(2)));
    }

    @Test
    public void build_010110() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        branchDiscoveryTraitItem(false, true),
                        originPullRequestDiscoveryTraitItem(2),
                        forkPullRequestDiscoveryTraitItem(1)));
    }

    @Test
    public void build_010111() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        branchDiscoveryTraitItem(false, true),
                        originPullRequestDiscoveryTraitItem(2),
                        forkPullRequestDiscoveryTraitItem(3)));
    }

    @Test
    public void build_011000() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(branchDiscoveryTraitItem(false, true), originPullRequestDiscoveryTraitItem(1)));
    }

    @Test
    public void build_011001() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        branchDiscoveryTraitItem(false, true),
                        originPullRequestDiscoveryTraitItem(1),
                        forkPullRequestDiscoveryTraitItem(2)));
    }

    @Test
    public void build_011010() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        branchDiscoveryTraitItem(false, true),
                        originPullRequestDiscoveryTraitItem(1),
                        forkPullRequestDiscoveryTraitItem(1)));
    }

    @Test
    public void build_011011() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        branchDiscoveryTraitItem(false, true),
                        originPullRequestDiscoveryTraitItem(1),
                        forkPullRequestDiscoveryTraitItem(3)));
    }

    @Test
    public void build_011100() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(branchDiscoveryTraitItem(false, true), originPullRequestDiscoveryTraitItem(3)));
    }

    @Test
    public void build_011101() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        branchDiscoveryTraitItem(false, true),
                        originPullRequestDiscoveryTraitItem(3),
                        forkPullRequestDiscoveryTraitItem(2)));
    }

    @Test
    public void build_011110() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        branchDiscoveryTraitItem(false, true),
                        originPullRequestDiscoveryTraitItem(3),
                        forkPullRequestDiscoveryTraitItem(1)));
    }

    @Test
    public void build_011111() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        branchDiscoveryTraitItem(false, true),
                        originPullRequestDiscoveryTraitItem(3),
                        forkPullRequestDiscoveryTraitItem(3)));
    }

    @Test
    public void build_100000() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(instance.getTraits(), contains(branchDiscoveryTraitItem(true, false)));
    }

    @Test
    public void build_100001() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(branchDiscoveryTraitItem(true, false), forkPullRequestDiscoveryTraitItem(2)));
    }

    @Test
    public void build_100010() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(branchDiscoveryTraitItem(true, false), forkPullRequestDiscoveryTraitItem(1)));
    }

    @Test
    public void build_100011() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(branchDiscoveryTraitItem(true, false), forkPullRequestDiscoveryTraitItem(3)));
    }

    @Test
    public void build_100100() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(branchDiscoveryTraitItem(true, false), originPullRequestDiscoveryTraitItem(2)));
    }

    @Test
    public void build_100101() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        branchDiscoveryTraitItem(true, false),
                        originPullRequestDiscoveryTraitItem(2),
                        forkPullRequestDiscoveryTraitItem(2)));
    }

    @Test
    public void build_100110() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        branchDiscoveryTraitItem(true, false),
                        originPullRequestDiscoveryTraitItem(2),
                        forkPullRequestDiscoveryTraitItem(1)));
    }

    @Test
    public void build_100111() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        branchDiscoveryTraitItem(true, false),
                        originPullRequestDiscoveryTraitItem(2),
                        forkPullRequestDiscoveryTraitItem(3)));
    }

    @Test
    public void build_101000() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(branchDiscoveryTraitItem(true, false), originPullRequestDiscoveryTraitItem(1)));
    }

    @Test
    public void build_101001() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        branchDiscoveryTraitItem(true, false),
                        originPullRequestDiscoveryTraitItem(1),
                        forkPullRequestDiscoveryTraitItem(2)));
    }

    @Test
    public void build_101010() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        branchDiscoveryTraitItem(true, false),
                        originPullRequestDiscoveryTraitItem(1),
                        forkPullRequestDiscoveryTraitItem(1)));
    }

    @Test
    public void build_101011() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        branchDiscoveryTraitItem(true, false),
                        originPullRequestDiscoveryTraitItem(1),
                        forkPullRequestDiscoveryTraitItem(3)));
    }

    @Test
    public void build_101100() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(branchDiscoveryTraitItem(true, false), originPullRequestDiscoveryTraitItem(3)));
    }

    @Test
    public void build_101101() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        branchDiscoveryTraitItem(true, false),
                        originPullRequestDiscoveryTraitItem(3),
                        forkPullRequestDiscoveryTraitItem(2)));
    }

    @Test
    public void build_101110() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        branchDiscoveryTraitItem(true, false),
                        originPullRequestDiscoveryTraitItem(3),
                        forkPullRequestDiscoveryTraitItem(1)));
    }

    @Test
    public void build_101111() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        branchDiscoveryTraitItem(true, false),
                        originPullRequestDiscoveryTraitItem(3),
                        forkPullRequestDiscoveryTraitItem(3)));
    }

    @Test
    public void build_110000() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(instance.getTraits(), contains(branchDiscoveryTraitItem(true, true)));
    }

    @Test
    public void build_110001() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(branchDiscoveryTraitItem(true, true), forkPullRequestDiscoveryTraitItem(2)));
    }

    @Test
    public void build_110010() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(branchDiscoveryTraitItem(true, true), forkPullRequestDiscoveryTraitItem(1)));
    }

    @Test
    public void build_110011() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(branchDiscoveryTraitItem(true, true), forkPullRequestDiscoveryTraitItem(3)));
    }

    @Test
    public void build_110100() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(branchDiscoveryTraitItem(true, true), originPullRequestDiscoveryTraitItem(2)));
    }

    @Test
    public void build_110101() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        branchDiscoveryTraitItem(true, true),
                        originPullRequestDiscoveryTraitItem(2),
                        forkPullRequestDiscoveryTraitItem(2)));
    }

    @Test
    public void build_110110() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        branchDiscoveryTraitItem(true, true),
                        originPullRequestDiscoveryTraitItem(2),
                        forkPullRequestDiscoveryTraitItem(1)));
    }

    @Test
    public void build_110111() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        branchDiscoveryTraitItem(true, true),
                        originPullRequestDiscoveryTraitItem(2),
                        forkPullRequestDiscoveryTraitItem(3)));
    }

    @Test
    public void build_111000() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(branchDiscoveryTraitItem(true, true), originPullRequestDiscoveryTraitItem(1)));
    }

    @Test
    public void build_111001() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        branchDiscoveryTraitItem(true, true),
                        originPullRequestDiscoveryTraitItem(1),
                        forkPullRequestDiscoveryTraitItem(2)));
    }

    @Test
    public void build_111010() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        branchDiscoveryTraitItem(true, true),
                        originPullRequestDiscoveryTraitItem(1),
                        forkPullRequestDiscoveryTraitItem(1)));
    }

    @Test
    public void build_111011() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        branchDiscoveryTraitItem(true, true),
                        originPullRequestDiscoveryTraitItem(1),
                        forkPullRequestDiscoveryTraitItem(3)));
    }

    @Test
    public void build_111100() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(branchDiscoveryTraitItem(true, true), originPullRequestDiscoveryTraitItem(3)));
    }

    @Test
    public void build_111101() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        branchDiscoveryTraitItem(true, true),
                        originPullRequestDiscoveryTraitItem(3),
                        forkPullRequestDiscoveryTraitItem(2)));
    }

    @Test
    public void build_111110() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        branchDiscoveryTraitItem(true, true),
                        originPullRequestDiscoveryTraitItem(3),
                        forkPullRequestDiscoveryTraitItem(1)));
    }

    @Test
    public void build_111111() throws Exception {
        GitHubSCMNavigator instance = load();
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        branchDiscoveryTraitItem(true, true),
                        originPullRequestDiscoveryTraitItem(3),
                        forkPullRequestDiscoveryTraitItem(3)));
    }

    @WithoutJenkins
    @Test
    public void given__legacyCode__when__constructor_cloud__then__discoveryTraitDefaults() throws Exception {
        GitHubSCMNavigator instance =
                new GitHubSCMNavigator(null, "cloudbeers", "bcaef157-f105-407f-b150-df7722eab6c1", "SAME");
        assertThat(instance.id(), is("https://api.github.com::cloudbeers"));
        assertThat(instance.getRepoOwner(), is("cloudbeers"));
        assertThat(instance.getApiUri(), is("https://api.github.com"));
        assertThat(instance.getCredentialsId(), is("bcaef157-f105-407f-b150-df7722eab6c1"));
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        branchDiscoveryTraitItem(true, true),
                        allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class),
                                hasProperty("strategies", is(EnumSet.of(ChangeRequestCheckoutStrategy.MERGE))),
                                hasProperty(
                                        "trust", instanceOf(ForkPullRequestDiscoveryTrait.TrustPermission.class)))));
        // legacy API
        assertThat(instance.getCheckoutCredentialsId(), is("SAME"));
        assertThat(instance.getPattern(), is(".*"));
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is(""));
    }

    @Test
    public void given__legacyCode__when__constructor_server__then__discoveryTraitDefaults() throws Exception {
        GitHubSCMNavigator instance = new GitHubSCMNavigator(
                "https://github.test/api/v3",
                "cloudbeers",
                "bcaef157-f105-407f-b150-df7722eab6c1",
                "8b2e4f77-39c5-41a9-b63b-8d367350bfdf");
        assertThat(instance.id(), is("https://github.test/api/v3::cloudbeers"));
        assertThat(instance.getRepoOwner(), is("cloudbeers"));
        assertThat(instance.getApiUri(), is("https://github.test/api/v3"));
        assertThat(instance.getCredentialsId(), is("bcaef157-f105-407f-b150-df7722eab6c1"));
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(
                        branchDiscoveryTraitItem(true, true),
                        sshCheckoutTraitItem("8b2e4f77-39c5-41a9-b63b-8d367350bfdf"),
                        allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class),
                                hasProperty("strategies", is(EnumSet.of(ChangeRequestCheckoutStrategy.MERGE))),
                                hasProperty(
                                        "trust", instanceOf(ForkPullRequestDiscoveryTrait.TrustPermission.class)))));
        // legacy API
        assertThat(instance.getCheckoutCredentialsId(), is("8b2e4f77-39c5-41a9-b63b-8d367350bfdf"));
        assertThat(instance.getPattern(), is(".*"));
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is(""));
    }

    @Test
    public void given__instance__when__setTraits_empty__then__traitsEmpty() {
        GitHubSCMNavigator instance = new GitHubSCMNavigator("test");
        instance.setTraits(new SCMTrait[0]);
        assertThat(instance.getTraits(), is(Collections.<SCMTrait<?>>emptyList()));
    }

    @Test
    public void given__instance__when__setTraits__then__traitsSet() {
        GitHubSCMNavigator instance = new GitHubSCMNavigator("test");
        instance.setTraits(
                Arrays.asList(new BranchDiscoveryTrait(BranchDiscoveryTrait.EXCLUDE_PRS), new SSHCheckoutTrait(null)));
        assertThat(
                instance.getTraits(),
                containsInAnyOrder(branchDiscoveryTraitItem(true, false), sshCheckoutTraitItem(nullValue())));
    }

    @Test
    public void given__instance__when__setCredentials_empty__then__credentials_null() {
        GitHubSCMNavigator instance = new GitHubSCMNavigator("test");
        instance.setCredentialsId("");
        assertThat(instance.getCredentialsId(), is(nullValue()));
    }

    @Test
    public void given__instance__when__setCredentials_null__then__credentials_null() {
        GitHubSCMNavigator instance = new GitHubSCMNavigator("test");
        instance.setCredentialsId("");
        assertThat(instance.getCredentialsId(), is(nullValue()));
    }

    @Test
    public void given__instance__when__setCredentials__then__credentials_set() {
        GitHubSCMNavigator instance = new GitHubSCMNavigator("test");
        instance.setCredentialsId("test");
        assertThat(instance.getCredentialsId(), is("test"));
    }

    @WithoutJenkins
    @Test
    public void given__instance__when__setApiUri_null__then__set__to_api_github_com() {
        GitHubSCMNavigator instance = new GitHubSCMNavigator("test");
        instance.setApiUri(null);
        assertThat(instance.getApiUri(), is("https://api.github.com"));
    }

    @Test
    public void given__instance__when__setApiUri_value__then__valueApplied() {
        GitHubSCMNavigator instance = new GitHubSCMNavigator("test");
        instance.setApiUri("https://github.test");
        assertThat(instance.getApiUri(), is("https://github.test"));
    }

    @Test
    public void given__instance__when__setApiUri_cloudUrl__then__valueApplied() {
        GitHubSCMNavigator instance = new GitHubSCMNavigator("test");
        instance.setApiUri("https://github.com");
        assertThat(instance.getApiUri(), is("https://github.com"));
    }

    @Test
    public void given__legacyCode__when__setPattern_default__then__patternSetAndTraitRemoved() {
        GitHubSCMNavigator instance = new GitHubSCMNavigator("test");
        instance.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, false),
                new RegexSCMSourceFilterTrait("job.*"),
                new SSHCheckoutTrait("dummy")));
        assertThat(instance.getPattern(), is("job.*"));
        assertThat(instance.getTraits(), hasItem(instanceOf(RegexSCMSourceFilterTrait.class)));
        instance.setPattern(".*");
        assertThat(instance.getPattern(), is(".*"));
        assertThat(instance.getTraits(), not(hasItem(instanceOf(RegexSCMSourceFilterTrait.class))));
    }

    @Test
    public void given__legacyCode__when__setPattern_custom__then__patternSetAndTraitAdded() {
        GitHubSCMNavigator instance = new GitHubSCMNavigator("test");
        instance.setTraits(Arrays.asList(new BranchDiscoveryTrait(true, false), new SSHCheckoutTrait("dummy")));
        assertThat(instance.getPattern(), is(".*"));
        assertThat(instance.getTraits(), not(hasItem(instanceOf(RegexSCMSourceFilterTrait.class))));
        instance.setPattern("job.*");
        assertThat(instance.getPattern(), is("job.*"));
        assertThat(instance.getTraits(), hasItem(regexSCMSourceFilterTraitItem("job.*")));
    }

    @Test
    public void given__legacyCode__when__setPattern_custom__then__patternSetAndTraitUpdated() {
        GitHubSCMNavigator instance = new GitHubSCMNavigator("test");
        instance.setTraits(new SCMTrait[] {
            new BranchDiscoveryTrait(true, false), new RegexSCMSourceFilterTrait("job.*"), new SSHCheckoutTrait("dummy")
        });
        assertThat(instance.getPattern(), is("job.*"));
        assertThat(instance.getTraits(), hasItem(instanceOf(RegexSCMSourceFilterTrait.class)));
        instance.setPattern("project.*");
        assertThat(instance.getPattern(), is("project.*"));
        assertThat(instance.getTraits(), not(hasItem(regexSCMSourceFilterTraitItem("job.*"))));
        assertThat(instance.getTraits(), hasItem(regexSCMSourceFilterTraitItem("project.*")));
    }

    @Test
    public void given__legacyCode__when__checkoutCredentials_SAME__then__noTraitAdded() {
        GitHubSCMNavigator instance = new GitHubSCMNavigator(null, "test", "scan", GitHubSCMSource.DescriptorImpl.SAME);
        assertThat(instance.getCheckoutCredentialsId(), is(GitHubSCMNavigator.DescriptorImpl.SAME));
        assertThat(instance.getTraits(), not(hasItem(instanceOf(SSHCheckoutTrait.class))));
    }

    @Test
    public void given__legacyCode__when__checkoutCredentials_null__then__traitAdded_ANONYMOUS() {
        GitHubSCMNavigator instance = new GitHubSCMNavigator(null, "test", "scan", null);
        assertThat(instance.getCheckoutCredentialsId(), is(GitHubSCMSource.DescriptorImpl.ANONYMOUS));
        assertThat(instance.getTraits(), hasItem(sshCheckoutTraitItem(nullValue())));
    }

    @Test
    public void given__legacyCode__when__checkoutCredentials_value__then__traitAdded() {
        GitHubSCMNavigator instance = new GitHubSCMNavigator(null, "test", "scan", "value");
        assertThat(instance.getCheckoutCredentialsId(), is("value"));
        assertThat(instance.getTraits(), hasItem(sshCheckoutTraitItem("value")));
    }

    @Test
    public void given__legacyCode__when__checkoutCredentials_ANONYMOUS__then__traitAdded() {
        GitHubSCMNavigator instance =
                new GitHubSCMNavigator(null, "test", "scan", GitHubSCMSource.DescriptorImpl.ANONYMOUS);
        assertThat(instance.getCheckoutCredentialsId(), is(GitHubSCMSource.DescriptorImpl.ANONYMOUS));
        assertThat(instance.getTraits(), hasItem(sshCheckoutTraitItem(nullValue())));
    }

    @Test
    public void given__legacyCode_withoutExcludes__when__setIncludes_default__then__traitRemoved() {
        GitHubSCMNavigator instance = new GitHubSCMNavigator("test");
        instance.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, false),
                new RegexSCMSourceFilterTrait("job.*"),
                new WildcardSCMHeadFilterTrait("feature/*", "")));
        assertThat(instance.getIncludes(), is("feature/*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(instance.getTraits(), hasItem(wildcardSCMHeadFilterTraitItem("feature/*", "")));
        instance.setIncludes("*");
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(instance.getTraits(), not(hasItem(instanceOf(WildcardSCMHeadFilterTrait.class))));
    }

    @Test
    public void given__legacyCode_withoutExcludes__when__setIncludes_value__then__traitUpdated() {
        GitHubSCMNavigator instance = new GitHubSCMNavigator("test");
        instance.setTraits(new SCMTrait[] {
            new BranchDiscoveryTrait(true, false),
            new RegexSCMSourceFilterTrait("job.*"),
            new WildcardSCMHeadFilterTrait("feature/*", "")
        });
        assertThat(instance.getIncludes(), is("feature/*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(instance.getTraits(), hasItem(wildcardSCMHeadFilterTraitItem("feature/*", "")));
        instance.setIncludes("bug/*");
        assertThat(instance.getIncludes(), is("bug/*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(instance.getTraits(), hasItem(wildcardSCMHeadFilterTraitItem("bug/*", "")));
    }

    @Test
    public void given__legacyCode_withoutTrait__when__setIncludes_value__then__traitAdded() {
        GitHubSCMNavigator instance = new GitHubSCMNavigator("test");
        instance.setTraits(
                Arrays.asList(new BranchDiscoveryTrait(true, false), new RegexSCMSourceFilterTrait("job.*")));
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(instance.getTraits(), not(hasItem(instanceOf(WildcardSCMHeadFilterTrait.class))));
        instance.setIncludes("feature/*");
        assertThat(instance.getIncludes(), is("feature/*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(instance.getTraits(), hasItem(wildcardSCMHeadFilterTraitItem("feature/*", "")));
    }

    @Test
    public void given__legacyCode_withExcludes__when__setIncludes_default__then__traitUpdated() {
        GitHubSCMNavigator instance = new GitHubSCMNavigator("test");
        instance.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, false),
                new RegexSCMSourceFilterTrait("job.*"),
                new WildcardSCMHeadFilterTrait("feature/*", "feature/ignore")));
        assertThat(instance.getIncludes(), is("feature/*"));
        assertThat(instance.getExcludes(), is("feature/ignore"));
        assertThat(instance.getTraits(), hasItem(wildcardSCMHeadFilterTraitItem("feature/*", "feature/ignore")));
        instance.setIncludes("*");
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is("feature/ignore"));
        assertThat(instance.getTraits(), hasItem(wildcardSCMHeadFilterTraitItem("*", "feature/ignore")));
    }

    @Test
    public void given__legacyCode_withExcludes__when__setIncludes_value__then__traitUpdated() {
        GitHubSCMNavigator instance = new GitHubSCMNavigator("test");
        instance.setTraits(new SCMTrait[] {
            new BranchDiscoveryTrait(true, false),
            new RegexSCMSourceFilterTrait("job.*"),
            new WildcardSCMHeadFilterTrait("feature/*", "feature/ignore")
        });
        assertThat(instance.getIncludes(), is("feature/*"));
        assertThat(instance.getExcludes(), is("feature/ignore"));
        assertThat(instance.getTraits(), hasItem(wildcardSCMHeadFilterTraitItem("feature/*", "feature/ignore")));
        instance.setIncludes("bug/*");
        assertThat(instance.getIncludes(), is("bug/*"));
        assertThat(instance.getExcludes(), is("feature/ignore"));
        assertThat(instance.getTraits(), hasItem(wildcardSCMHeadFilterTraitItem("bug/*", "feature/ignore")));
    }

    @Test
    public void given__legacyCode_withoutIncludes__when__setExcludes_default__then__traitRemoved() {
        GitHubSCMNavigator instance = new GitHubSCMNavigator("test");
        instance.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, false),
                new RegexSCMSourceFilterTrait("job.*"),
                new WildcardSCMHeadFilterTrait("*", "feature/ignore")));
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is("feature/ignore"));
        assertThat(instance.getTraits(), hasItem(wildcardSCMHeadFilterTraitItem("*", "feature/ignore")));
        instance.setExcludes("");
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(instance.getTraits(), not(hasItem(instanceOf(WildcardSCMHeadFilterTrait.class))));
    }

    @Test
    public void given__legacyCode_withoutIncludes__when__setExcludes_value__then__traitUpdated() {
        GitHubSCMNavigator instance = new GitHubSCMNavigator("test");
        instance.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, false),
                new RegexSCMSourceFilterTrait("job.*"),
                new WildcardSCMHeadFilterTrait("*", "feature/ignore")));
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is("feature/ignore"));
        assertThat(instance.getTraits(), hasItem(wildcardSCMHeadFilterTraitItem("*", "feature/ignore")));
        instance.setExcludes("bug/ignore");
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is("bug/ignore"));
        assertThat(instance.getTraits(), hasItem(wildcardSCMHeadFilterTraitItem("*", "bug/ignore")));
    }

    @Test
    public void given__legacyCode_withoutTrait__when__setExcludes_value__then__traitAdded() {
        GitHubSCMNavigator instance = new GitHubSCMNavigator("test");
        instance.setTraits(
                Arrays.asList(new BranchDiscoveryTrait(true, false), new RegexSCMSourceFilterTrait("job.*")));
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(instance.getTraits(), not(hasItem(instanceOf(WildcardSCMHeadFilterTrait.class))));
        instance.setExcludes("feature/ignore");
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is("feature/ignore"));
        assertThat(instance.getTraits(), hasItem(wildcardSCMHeadFilterTraitItem("*", "feature/ignore")));
    }

    @Test
    public void given__legacyCode_withIncludes__when__setExcludes_default__then__traitUpdated() {
        GitHubSCMNavigator instance = new GitHubSCMNavigator("test");
        instance.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, false),
                new RegexSCMSourceFilterTrait("job.*"),
                new WildcardSCMHeadFilterTrait("feature/*", "feature/ignore")));
        assertThat(instance.getIncludes(), is("feature/*"));
        assertThat(instance.getExcludes(), is("feature/ignore"));
        assertThat(instance.getTraits(), hasItem(wildcardSCMHeadFilterTraitItem("feature/*", "feature/ignore")));
        instance.setExcludes("");
        assertThat(instance.getIncludes(), is("feature/*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(instance.getTraits(), hasItem(wildcardSCMHeadFilterTraitItem("feature/*", "")));
    }

    @Test
    public void given__legacyCode_withIncludes__when__setExcludes_value__then__traitUpdated() {
        GitHubSCMNavigator instance = new GitHubSCMNavigator("test");
        instance.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, false),
                new RegexSCMSourceFilterTrait("job.*"),
                new WildcardSCMHeadFilterTrait("feature/*", "")));
        assertThat(instance.getIncludes(), is("feature/*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(instance.getTraits(), hasItem(wildcardSCMHeadFilterTraitItem("feature/*", "")));
        instance.setExcludes("feature/ignore");
        assertThat(instance.getIncludes(), is("feature/*"));
        assertThat(instance.getExcludes(), is("feature/ignore"));
        assertThat(instance.getTraits(), hasItem(wildcardSCMHeadFilterTraitItem("feature/*", "feature/ignore")));
    }

    @Test
    public void given__legacyCode__when__setBuildOriginBranch__then__traitsMaintained() {
        GitHubSCMNavigator instance = new GitHubSCMNavigator("test");
        instance.setTraits(Collections.emptyList());
        assertThat(instance.getTraits(), is(empty()));
        instance.setBuildOriginBranch(true);
        assertThat(instance.getTraits(), contains(instanceOf(BranchDiscoveryTrait.class)));
        instance.setBuildOriginBranch(false);
        assertThat(instance.getTraits(), is(empty()));

        instance.setBuildOriginBranchWithPR(true);
        assertThat(instance.getTraits(), contains(instanceOf(BranchDiscoveryTrait.class)));
        instance.setBuildOriginBranchWithPR(false);
        assertThat(instance.getTraits(), is(empty()));

        instance.setBuildOriginBranchWithPR(true);
        instance.setBuildOriginBranch(true);
        assertThat(instance.getTraits(), contains(instanceOf(BranchDiscoveryTrait.class)));
        instance.setBuildOriginBranchWithPR(false);
        assertThat(instance.getTraits(), contains(instanceOf(BranchDiscoveryTrait.class)));
        instance.setBuildOriginBranch(false);
        assertThat(instance.getTraits(), is(empty()));

        instance.setBuildOriginBranchWithPR(true);
        instance.setBuildOriginBranch(true);
        assertThat(instance.getTraits(), contains(instanceOf(BranchDiscoveryTrait.class)));
        instance.setBuildOriginBranch(false);
        assertThat(instance.getTraits(), contains(instanceOf(BranchDiscoveryTrait.class)));
        instance.setBuildOriginBranchWithPR(false);
        assertThat(instance.getTraits(), is(empty()));
    }
}
