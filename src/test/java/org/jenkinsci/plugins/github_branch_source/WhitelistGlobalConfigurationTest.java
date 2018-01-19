package org.jenkinsci.plugins.github_branch_source;

import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;

import org.junit.Test;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlTextArea;
import com.gargoylesoftware.htmlunit.html.HtmlFormUtil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class WhitelistGlobalConfigurationTest {

    static final Set<String> WHITELISTED_USERS = new HashSet<String>(Arrays.asList("user1", "user2"));

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @Test
    public void given__globalConfiguration__when__formIsSubmitted__then__whitelistIsCorrect() throws Exception {
        HtmlPage page = jenkinsRule.createWebClient().goTo("configure");
        HtmlTextArea input = page.getElementByName("_.pullRequestWhitelist");
        input.setText("user1 user2");
        HtmlForm form = page.getFormByName("config");
        HtmlFormUtil.submit(form);

        WhitelistGlobalConfiguration whitelist = new WhitelistGlobalConfiguration();
        assertEquals(whitelist.getUserIds(), WHITELISTED_USERS);
    }

}
