package org.jenkinsci.plugins.github_branch_source;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

import java.util.Collections;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMHeadOrigin;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import jenkins.scm.api.trait.SCMHeadAuthority;
import jenkins.scm.impl.ChangeRequestSCMHeadCategory;
import jenkins.scm.impl.TagSCMHeadCategory;
import jenkins.scm.impl.UncategorizedSCMHeadCategory;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class TagDiscoveryTraitTest {
    @ClassRule
    public static JenkinsRule j = new JenkinsRule();

    @Test
    public void decorateContext() throws Exception {
        GitHubSCMSourceContext probe = new GitHubSCMSourceContext(null, SCMHeadObserver.collect());
        assertThat(probe.wantBranches(), is(false));
        assertThat(probe.wantPRs(), is(false));
        assertThat(probe.wantTags(), is(false));
        assertThat(probe.authorities(), is(Collections.<SCMHeadAuthority<?, ?, ?>>emptyList()));
        new TagDiscoveryTrait().applyToContext(probe);
        assertThat(probe.wantBranches(), is(false));
        assertThat(probe.wantPRs(), is(false));
        assertThat(probe.wantTags(), is(true));
        assertThat(probe.authorities(), contains(instanceOf(TagDiscoveryTrait.TagSCMHeadAuthority.class)));
    }

    @Test
    public void includeCategory() throws Exception {
        assertThat(new TagDiscoveryTrait().includeCategory(ChangeRequestSCMHeadCategory.DEFAULT), is(false));
        assertThat(new TagDiscoveryTrait().includeCategory(UncategorizedSCMHeadCategory.DEFAULT), is(false));
        assertThat(new TagDiscoveryTrait().includeCategory(TagSCMHeadCategory.DEFAULT), is(true));
    }

    @Test
    public void authority() throws Exception {
        try (GitHubSCMSourceRequest probe = new GitHubSCMSourceContext(null, SCMHeadObserver.collect())
                .newRequest(new GitHubSCMSource("does-not-exist", "http://does-not-exist.test"), null)) {
            TagDiscoveryTrait.TagSCMHeadAuthority instance = new TagDiscoveryTrait.TagSCMHeadAuthority();
            assertThat(instance.isTrusted(probe, new SCMHead("v1.0.0")), is(false));
            assertThat(
                    instance.isTrusted(
                            probe,
                            new PullRequestSCMHead(
                                    "PR-1",
                                    "does-not-exists",
                                    "http://does-not-exist.test",
                                    "feature/1",
                                    1,
                                    new BranchSCMHead("master"),
                                    SCMHeadOrigin.DEFAULT,
                                    ChangeRequestCheckoutStrategy.MERGE)),
                    is(false));
            assertThat(instance.isTrusted(probe, new GitHubTagSCMHead("v1.0.0", 0L)), is(true));
        }
    }

    @Test
    public void authority_with_repositoryUrl() throws Exception {
        try (GitHubSCMSourceRequest probe = new GitHubSCMSourceContext(null, SCMHeadObserver.collect())
                .newRequest(new GitHubSCMSource("", "", "https://github.com/example/does-not-exist", true), null)) {
            TagDiscoveryTrait.TagSCMHeadAuthority instance = new TagDiscoveryTrait.TagSCMHeadAuthority();
            assertThat(instance.isTrusted(probe, new SCMHead("v1.0.0")), is(false));
            assertThat(
                    instance.isTrusted(
                            probe,
                            new PullRequestSCMHead(
                                    "PR-1",
                                    "does-not-exists",
                                    "http://does-not-exist.test",
                                    "feature/1",
                                    1,
                                    new BranchSCMHead("master"),
                                    SCMHeadOrigin.DEFAULT,
                                    ChangeRequestCheckoutStrategy.MERGE)),
                    is(false));
            assertThat(instance.isTrusted(probe, new GitHubTagSCMHead("v1.0.0", 0L)), is(true));
        }
    }
}
