package org.jenkinsci.plugins.github_branch_source;

import hudson.scm.SCM;
import hudson.util.LogTaskListener;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMHeadObserver.Collector;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.*;
import static org.hamcrest.Matchers.contains;

public class GitHubSCMSourceTest {
    @ClassRule
    public static WireMockClassRule wireMockRule = new WireMockClassRule(8089);
    @Rule
    public WireMockClassRule instanceRule = wireMockRule;

    @Rule
    public JenkinsRule jrule = new JenkinsRule();


    @Test
    public void testRetrieve() throws Exception {
        GitHubSCMSource ghss = new GitHubSCMSource("testSource", "http://localhost:8089/", null, null, "testOwner", "testRepo");
        Set<SCMHead> results = getResults(ghss).keySet();
        

        //check the contents that were retrieved
        assertEquals(3, results.size());
        List<String> branches = Arrays.asList(new String[] {"PR-1", "master", "test-failure"});
        assertEquals(branches.size(), results.size());

        //check the status of a particular SCM Branch
        LogTaskListener logListener2 = new LogTaskListener(Logger.getLogger(getClass().getName()), Level.FINE);
        SCMHead master = getMaster(results);
        assertNotNull("No master branch was found.", master);
        SCMRevision rev = ghss.retrieve(master, logListener2);
        assertTrue(rev.getHead().equals(master));
    }

    @Test
    public void testBuild() throws Exception { 
        LogTaskListener logListener = new LogTaskListener(Logger.getLogger(getClass().getName()), Level.FINE);

        GitHubSCMSource ghss = new GitHubSCMSource("testSource", "http://localhost:8089/", null, null, "testOwner", "testRepo");
        Map<SCMHead, SCMRevision> results = getResults(ghss);
        SCMHead master = getMaster(results.keySet());
        SCMRevision mRev = results.get(master);

        SCM scm = ghss.build(master, mRev);
        assertNotNull("SCM exists", scm);
        assertEquals("hudson.plugins.git.GitSCM", scm.getType());
    }

    private Map<SCMHead, SCMRevision> getResults(GitHubSCMSource ghss) throws Exception {
        LogTaskListener logListener = new LogTaskListener(Logger.getLogger(getClass().getName()), Level.FINE);
        Collector c = SCMHeadObserver.collect();
        ghss.retrieve(c, logListener);
        return c.result();
    }

    private SCMHead getMaster(Set<SCMHead> allBranches) {
        Iterator<SCMHead> i = allBranches.iterator();
        SCMHead master = null;
        while(i.hasNext()) {
            SCMHead toCheck = i.next();
            System.out.println("BRACH NAME " + toCheck.getName());
            if(toCheck.getName().equals("master")){
                //assertNull("FOUND IN MASTER?","FOUND IN MASTER\n" + toCheck);
                master = toCheck;
                break;
            }
        }
        return master;
    }

    private SCMHead getMaster(GitHubSCMSource ghss) throws Exception {
        return getMaster(getResults(ghss).keySet());
    }
}