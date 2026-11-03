/*
 * The MIT License
 *
 * Copyright (c) 2026 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 */

package org.jenkinsci.plugins.github_branch_source;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.model.Item;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * A source and a navigator hold on to the credentials object they resolved, so a credential whose content
 * changed reaches them only when that reference is dropped.
 */
public class CredentialsRefreshTest {

    private static final String CREDENTIALS_ID = "github";

    @Rule
    public JenkinsRule r = new JenkinsRule();

    private final long originalTtl = Connector.credentialsTtlMillis;

    @After
    public void restoreTtl() {
        Connector.credentialsTtlMillis = originalTtl;
    }

    @Test
    public void sourceResolvesCredentialsAgainOnceTheTtlHasElapsed() throws Exception {
        Item context = r.createFolder("source");
        store("first-secret");

        GitHubSCMSource source = new GitHubSCMSource("cloudbeers", "yolo", null, false);
        source.setCredentialsId(CREDENTIALS_ID);

        StandardCredentials resolved = resolve(source, context, false);
        assertThat(password(resolved), is("first-secret"));

        store("second-secret");
        assertThat(resolve(source, context, false), sameInstance(resolved));

        expireTtl();
        assertThat(password(resolve(source, context, false)), is("second-secret"));
    }

    @Test
    public void navigatorResolvesCredentialsAgainOnceTheTtlHasElapsed() throws Exception {
        Item context = r.createFolder("navigator");
        store("first-secret");

        GitHubSCMNavigator navigator = new GitHubSCMNavigator("cloudbeers");
        navigator.setCredentialsId(CREDENTIALS_ID);

        StandardCredentials resolved = resolve(navigator, context, false);
        assertThat(password(resolved), is("first-secret"));

        store("second-secret");
        assertThat(resolve(navigator, context, false), sameInstance(resolved));

        expireTtl();
        assertThat(password(resolve(navigator, context, false)), is("second-secret"));
    }

    @Test
    public void forceRefreshResolvesInsideTheTtl() throws Exception {
        Item context = r.createFolder("force");
        store("first-secret");

        GitHubSCMSource source = new GitHubSCMSource("cloudbeers", "yolo", null, false);
        source.setCredentialsId(CREDENTIALS_ID);
        assertThat(password(resolve(source, context, false)), is("first-secret"));

        store("second-secret");
        assertThat(password(resolve(source, context, true)), is("second-secret"));
    }

    @Test
    public void negativeTtlSecondsMeansNoExpiry() {
        assertEquals(-1L, Connector.credentialsTtlMillis(-1));
        assertEquals(0L, Connector.credentialsTtlMillis(0));
        assertEquals(60_000L, Connector.credentialsTtlMillis(60));
    }

    @Test
    public void staleOnlyOnceTheTtlHasElapsed() {
        long now = System.currentTimeMillis();

        Connector.credentialsTtlMillis = 60_000L;
        assertFalse(Connector.isCredentialsStale(now));
        assertTrue(Connector.isCredentialsStale(now - 60_000L));

        Connector.credentialsTtlMillis = 0L;
        assertTrue(Connector.isCredentialsStale(now));

        Connector.credentialsTtlMillis = -1L;
        assertFalse(Connector.isCredentialsStale(0L));
    }

    /**
     * {@code getCredentials} is private on both classes, deliberately so per the review of #787, hence the
     * reflection rather than widening it for this test.
     */
    private static StandardCredentials resolve(Object sourceOrNavigator, Item context, boolean forceRefresh)
            throws Exception {
        Method method = sourceOrNavigator.getClass().getDeclaredMethod("getCredentials", Item.class, boolean.class);
        method.setAccessible(true);
        return (StandardCredentials) method.invoke(sourceOrNavigator, context, forceRefresh);
    }

    /** Lets the TTL elapse for real, rather than exercising only the "resolve on every use" shortcut. */
    private static void expireTtl() throws InterruptedException {
        Connector.credentialsTtlMillis = 1L;
        Thread.sleep(10L);
    }

    private static void store(String password) throws Exception {
        Credentials credentials =
                new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, CREDENTIALS_ID, null, "user", password);
        SystemCredentialsProvider.getInstance()
                .setDomainCredentialsMap(Collections.singletonMap(Domain.global(), List.of(credentials)));
    }

    private static String password(StandardCredentials credentials) {
        return ((StandardUsernamePasswordCredentials) credentials).getPassword().getPlainText();
    }
}
