package org.jenkinsci.plugins.github_branch_source;

import hudson.util.LogTaskListener;
import jenkins.scm.api.SCMHeadObserver;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.logging.Level;
import java.util.logging.Logger;

public class GitHubSCMSourceTest {
    @ClassRule
    public static WireMockClassRule wireMockRule = new WireMockClassRule(8089);
    @Rule
    public WireMockClassRule instanceRule = wireMockRule;

    @Rule
    public JenkinsRule jrule = new JenkinsRule();


    @Test
    public void testRetrieve() throws Exception{
        LogTaskListener logListener = new LogTaskListener(Logger.getLogger(getClass().getName()), Level.FINE);

        GitHubSCMSource ghss = new GitHubSCMSource("testSource", "http://localhost:8089/", null, null, "testOwner", "testRepo");
        ghss.retrieve(SCMHeadObserver.collect(), logListener);

    }



}