package org.jenkinsci.plugins.github_branch_source;

import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class GitHubSCMSourceTraitsTest {
    /**
     * All tests in this class only use Jenkins for the extensions
     */
    @ClassRule
    public static JenkinsRule r = new JenkinsRule();

    @Test
    public void given__configuredInstance__when__uninstantiating__then__deprecatedFieldsIgnored() throws Exception {
        GitHubSCMSource instance = new GitHubSCMSource("repo-owner", "repo");
        System.out.println(DescribableModel.uninstantiate2_(instance).toMap());
        instance.setExcludes("production");
        System.out.println(DescribableModel.uninstantiate2_(instance).toMap());
    }
}
