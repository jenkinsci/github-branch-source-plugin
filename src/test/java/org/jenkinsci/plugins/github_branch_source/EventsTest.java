/*
 * The MIT License
 *
 * Copyright (c) 2017 CloudBees, Inc.
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

import jenkins.scm.api.SCMEvent;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMSourceEvent;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.github.extension.GHSubscriberEvent;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class EventsTest {
    /**
     * All tests in this class only use Jenkins for the extensions
     */
    @ClassRule
    public static JenkinsRule r = new JenkinsRule();

    private static SCMEvent.Type firedEventType;
    private static GHSubscriberEvent ghEvent;

    @Test
    public void pushEvents() throws Exception {
        PushGHEventSubscriber subscriber = new PushGHEventSubscriber();

        firedEventType = SCMEvent.Type.CREATED;
        ghEvent = callOnEvent(subscriber, "EventsTest/pushEventCreated.json");
        waitAndAssertReceived(true);

        firedEventType = SCMEvent.Type.REMOVED;
        ghEvent = callOnEvent(subscriber, "EventsTest/pushEventRemoved.json");
        waitAndAssertReceived(true);

        firedEventType = SCMEvent.Type.UPDATED;
        ghEvent = callOnEvent(subscriber, "EventsTest/pushEventUpdated.json");
        waitAndAssertReceived(true);
    }

    @Test
    public void pullRequestEvents() throws Exception {
        PullRequestGHEventSubscriber subscriber = new PullRequestGHEventSubscriber();

        firedEventType = SCMEvent.Type.CREATED;
        ghEvent = callOnEvent(subscriber, "EventsTest/pullRequestEventCreated.json");
        waitAndAssertReceived(true);

        firedEventType = SCMEvent.Type.REMOVED;
        ghEvent = callOnEvent(subscriber, "EventsTest/pullRequestEventRemoved.json");
        waitAndAssertReceived(true);

        firedEventType = SCMEvent.Type.UPDATED;
        ghEvent = callOnEvent(subscriber, "EventsTest/pullRequestEventUpdated.json");
        waitAndAssertReceived(true);

        ghEvent = callOnEvent(subscriber, "EventsTest/pullRequestEventUpdatedSync.json");
        waitAndAssertReceived(true);
    }

    @Test
    public void repositoryEvents() throws Exception {
        GitHubRepositoryEventSubscriber subscriber = new GitHubRepositoryEventSubscriber();

        firedEventType = SCMEvent.Type.CREATED;
        ghEvent = callOnEvent(subscriber, "EventsTest/repositoryEventCreated.json");
        waitAndAssertReceived(true);

        ghEvent = callOnEvent(subscriber, "EventsTest/repositoryEventNotFiredNotFork.json");
        waitAndAssertReceived(false);

        ghEvent = callOnEvent(subscriber, "EventsTest/repositoryEventNotFiredWrongAction.json");
        waitAndAssertReceived(false);
    }

    private GHSubscriberEvent callOnEvent(PushGHEventSubscriber subscriber, String eventPayloadFile) throws IOException {
        GHSubscriberEvent event = createMockEvent(eventPayloadFile);
        subscriber.onEvent(event);
        return event;
    }

    private GHSubscriberEvent callOnEvent(PullRequestGHEventSubscriber subscriber, String eventPayloadFile) throws IOException {
        GHSubscriberEvent event = createMockEvent(eventPayloadFile);
        subscriber.onEvent(event);
        return event;
    }

    private GHSubscriberEvent callOnEvent(GitHubRepositoryEventSubscriber subscriber, String eventPayloadFile) throws IOException {
        GHSubscriberEvent event = createMockEvent(eventPayloadFile);
        subscriber.onEvent(event);
        return event;
    }

    private GHSubscriberEvent createMockEvent(String eventPayloadFile) throws IOException {
        GHSubscriberEvent event = mock(GHSubscriberEvent.class);
        when(event.getPayload()).thenReturn(IOUtils.toString(getClass().getResourceAsStream(eventPayloadFile)));
        when(event.getGHEvent()).thenReturn(null);
        when(event.getOrigin()).thenReturn("myOrigin");
        when(event.getTimestamp()).thenReturn(123456789L);

        return event;
    }

    private void waitAndAssertReceived(boolean received) throws InterruptedException {
        Thread.sleep(5100);
        assertEquals("Event should have " + ((!received) ? "not " : "") + "been received", received, TestSCMEventListener.didReceive());

        if (received) {
            TestSCMEventListener.setReceived(false);
        }
    }

    @TestExtension
    public static class TestSCMEventListener extends jenkins.scm.api.SCMEventListener {

        private static boolean eventReceived = false;

        public void onSCMHeadEvent(SCMHeadEvent<?> event) {
            receiveEvent(event.getType(), event.getTimestamp(), event.getOrigin());
        }

        public void onSCMSourceEvent(SCMSourceEvent<?> event) {
            receiveEvent(event.getType(), event.getTimestamp(), event.getOrigin());
        }

        private void receiveEvent(SCMEvent.Type type, long timestamp, String origin) {
            eventReceived = true;

            assertEquals("Event type should be the same", type, firedEventType);
            assertEquals("Event timestamp should be the same", timestamp, ghEvent.getTimestamp());
            assertEquals("Event origin should be the same", origin, ghEvent.getOrigin());
        }

        public static boolean didReceive() {
            return eventReceived;
        }

        public static void setReceived(boolean received) {
            eventReceived = received;
        }

    }

}
