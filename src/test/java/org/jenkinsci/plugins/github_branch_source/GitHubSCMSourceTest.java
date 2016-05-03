package org.jenkinsci.plugins.github_branch_source;

import com.cloudbees.jenkins.GitHubWebHook;
import hudson.util.LogTaskListener;
import jenkins.scm.api.SCMHeadObserver;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.*;

import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;


@RunWith(PowerMockRunner.class)
@PrepareForTest( { GitHubWebHook.class })
@PowerMockIgnore("javax.net.ssl.*")
public class GitHubSCMSourceTest {
    @ClassRule
    public static WireMockClassRule wireMockRule = new WireMockClassRule(8089);
    @Rule
    public WireMockClassRule instanceRule = wireMockRule;

    @Rule
    public JenkinsRule jrule = new JenkinsRule();


    @Test
    public void testRetrieve(){
        try {
            mockStatic(GitHubWebHook.class);
            when(GitHubWebHook.getJenkinsInstance()).thenReturn(jrule.getInstance());

            LogTaskListener logListener = new LogTaskListener(Logger.getLogger(getClass().getName()), Level.FINE);

            GitHubSCMSource ghss = new GitHubSCMSource("testSource", "http://localhost:8089/", null, null, "testOwner", "testRepo");
            ghss.retrieve(SCMHeadObserver.collect(), logListener);

        } catch (IOException e) {
            fail(e.toString());
        } catch (InterruptedException e) {
            fail(e.toString());
        } catch (Throwable throwable) {
            fail(throwable.toString());
        }


    }

}