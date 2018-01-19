package org.jenkinsci.plugins.github_branch_source;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlFormUtil;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlTextArea;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class WhitelistGlobalConfigurationTest {

    static final Set<String> WHITELISTED_USERS = new HashSet<String>(Arrays.asList("user1", "user2"));

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @Test
    public void given__whitelistField__when__cleanUserIdsAreInput__then__usersIdsAreCorrect() throws Exception {
        testGetUserIds("user1 user2");
    }

    @Test
    public void given__whitelistField__when__userIdsAreInputWithExtraWhitespace__then__usersIdsAreCorrect() throws Exception {
        testGetUserIds("   user1   user2   ");
    }

    @Test
    public void given__whitelistField__when__userIdsAreInputWithNewlines__then__usersIdsAreCorrect() throws Exception {
        testGetUserIds("user1\nuser2");
    }

    @Test
    public void given__whitelistField__when__uppercaseIdsAreInput__then__usersIdsAreCorrect() throws Exception {
        testGetUserIds("USER1 USER2");
    }

    @Test
    public void given__whitelistField__when__userIdsAreInput__then__firstUserIdIsFound() throws Exception {
        testContains("user1 user2", "user1");
    }

    @Test
    public void given__whitelistField__when__userIdsAreInput__then__secondUserIdIsFound() throws Exception {
        testContains("user1 user2", "user2");
    }

    private void testGetUserIds(String inputText) throws Exception {
        HtmlPage page = jenkinsRule.createWebClient().goTo("configure");
        HtmlTextArea input = page.getElementByName("_.pullRequestWhitelist");
        input.setText(inputText);
        HtmlForm form = page.getFormByName("config");
        HtmlFormUtil.submit(form);

        WhitelistGlobalConfiguration whitelist = new WhitelistGlobalConfiguration();
        assertEquals(WHITELISTED_USERS, whitelist.getUserIds());
    }

    private void testContains(String inputText, String userId) throws Exception {
        HtmlPage page = jenkinsRule.createWebClient().goTo("configure");
        HtmlTextArea input = page.getElementByName("_.pullRequestWhitelist");
        input.setText(inputText);
        HtmlForm form = page.getFormByName("config");
        HtmlFormUtil.submit(form);

        WhitelistGlobalConfiguration whitelist = new WhitelistGlobalConfiguration();
        assertThat(whitelist.contains(userId), is(true));
    }

}
