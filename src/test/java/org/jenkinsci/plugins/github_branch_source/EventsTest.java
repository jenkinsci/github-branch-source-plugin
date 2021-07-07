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

import static org.junit.Assert.assertEquals;

import hudson.model.Cause;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import jenkins.scm.api.SCMEvent;
import jenkins.scm.api.SCMEvents;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMSourceEvent;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.github.extension.GHSubscriberEvent;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

public class EventsTest {

  /** All tests in this class only use Jenkins for the extensions */
  @ClassRule public static JenkinsRule r = new JenkinsRule();

  private static int defaultFireDelayInSeconds = GitHubSCMSource.getEventDelaySeconds();

  private static SCMEvent.Type firedEventType;
  private static GHSubscriberEvent ghEvent;
  private static Cause[] firedEventCauses;

  @BeforeClass
  public static void setupDelay() {
    GitHubSCMSource.setEventDelaySeconds(1);
  }

  @Before
  public void resetFiredEvent() {
    firedEventType = null;
    firedEventCauses = null;
    ghEvent = null;
    TestSCMEventListener.setReceived(false);
    TestSCMEventListener.setError(null);
  }

  @AfterClass
  public static void resetDelay() {
    GitHubSCMSource.setEventDelaySeconds(defaultFireDelayInSeconds);
  }

  @Test
  public void given_ghPushEventCreated_then_createdHeadEventFired() throws Exception {
    PushGHEventSubscriber subscriber = new PushGHEventSubscriber();

    firedEventType = SCMEvent.Type.CREATED;
    firedEventCauses =
        new Cause[] {
          new GitHubSenderCause(
              6752317, "baxterthehacker", null, GitHubSenderCause.Kind.BRANCH_CREATED)
        };
    ghEvent = callOnEvent(subscriber, "EventsTest/pushEventCreated.json");
    waitAndAssertReceived(true);
  }

  @Test
  public void given_ghPushEventDeleted_then_removedHeadEventFired() throws Exception {
    PushGHEventSubscriber subscriber = new PushGHEventSubscriber();

    firedEventType = SCMEvent.Type.REMOVED;
    firedEventCauses =
        new Cause[] {
          new GitHubSenderCause(
              6752317, "baxterthehacker", null, GitHubSenderCause.Kind.BRANCH_DELETED)
        };
    ghEvent = callOnEvent(subscriber, "EventsTest/pushEventRemoved.json");
    waitAndAssertReceived(true);
  }

  @Test
  public void given_ghPushEventUpdated_then_updatedHeadEventFired() throws Exception {
    PushGHEventSubscriber subscriber = new PushGHEventSubscriber();

    firedEventType = SCMEvent.Type.UPDATED;
    firedEventCauses =
        new Cause[] {
          new GitHubSenderCause(
              6752317, "baxterthehacker", null, GitHubSenderCause.Kind.BRANCH_UPDATED)
        };
    ghEvent = callOnEvent(subscriber, "EventsTest/pushEventUpdated.json");
    waitAndAssertReceived(true);
  }

  @Test
  public void given_ghPullRequestEventOpened_then_createdHeadEventFired() throws Exception {
    PullRequestGHEventSubscriber subscriber = new PullRequestGHEventSubscriber();

    firedEventType = SCMEvent.Type.CREATED;
    firedEventCauses =
        new Cause[] {
          new GitHubSenderCause(
              6752317, "baxterthehacker", null, GitHubSenderCause.Kind.PULL_REQUEST_CREATED)
        };
    ghEvent = callOnEvent(subscriber, "EventsTest/pullRequestEventCreated.json");
    waitAndAssertReceived(true);
  }

  @Test
  public void given_ghPullRequestEventClosed_then_removedHeadEventFired() throws Exception {
    PullRequestGHEventSubscriber subscriber = new PullRequestGHEventSubscriber();

    firedEventType = SCMEvent.Type.REMOVED;
    firedEventCauses =
        new Cause[] {
          new GitHubSenderCause(
              6752317, "baxterthehacker", null, GitHubSenderCause.Kind.PULL_REQUEST_DELETED)
        };
    ghEvent = callOnEvent(subscriber, "EventsTest/pullRequestEventRemoved.json");
    waitAndAssertReceived(true);
  }

  @Test
  public void given_ghPullRequestEventReopened_then_updatedHeadEventFired() throws Exception {
    PullRequestGHEventSubscriber subscriber = new PullRequestGHEventSubscriber();

    firedEventType = SCMEvent.Type.UPDATED;
    firedEventCauses =
        new Cause[] {
          new GitHubSenderCause(
              6752317, "baxterthehacker", null, GitHubSenderCause.Kind.PULL_REQUEST_UPDATED)
        };
    ghEvent = callOnEvent(subscriber, "EventsTest/pullRequestEventUpdated.json");
    waitAndAssertReceived(true);
  }

  @Test
  public void given_ghPullRequestEventSync_then_updatedHeadEventFired() throws Exception {
    PullRequestGHEventSubscriber subscriber = new PullRequestGHEventSubscriber();

    firedEventType = SCMEvent.Type.UPDATED;
    firedEventCauses =
        new Cause[] {
          new GitHubSenderCause(
              6752317, "baxterthehacker", null, GitHubSenderCause.Kind.PULL_REQUEST_UPDATED)
        };
    ghEvent = callOnEvent(subscriber, "EventsTest/pullRequestEventUpdatedSync.json");
    waitAndAssertReceived(true);
  }

  @Test
  public void given_ghRepositoryEventCreatedFromFork_then_createdSourceEventFired()
      throws Exception {
    GitHubRepositoryEventSubscriber subscriber = new GitHubRepositoryEventSubscriber();

    firedEventType = SCMEvent.Type.CREATED;
    firedEventCauses = new Cause[] {};
    ghEvent = callOnEvent(subscriber, "EventsTest/repositoryEventCreated.json");
    waitAndAssertReceived(true);
  }

  @Test
  public void given_ghRepositoryEventCreatedNotFork_then_noSourceEventFired() throws Exception {
    GitHubRepositoryEventSubscriber subscriber = new GitHubRepositoryEventSubscriber();

    ghEvent = callOnEvent(subscriber, "EventsTest/repositoryEventNotFiredNotFork.json");
    waitAndAssertReceived(false);
  }

  @Test
  public void given_ghRepositoryEventWrongAction_then_noSourceEventFired() throws Exception {
    GitHubRepositoryEventSubscriber subscriber = new GitHubRepositoryEventSubscriber();

    ghEvent = callOnEvent(subscriber, "EventsTest/repositoryEventNotFiredWrongAction.json");
    waitAndAssertReceived(false);
  }

  private GHSubscriberEvent callOnEvent(PushGHEventSubscriber subscriber, String eventPayloadFile)
      throws IOException {
    GHSubscriberEvent event = createEvent(eventPayloadFile);
    subscriber.onEvent(event);
    return event;
  }

  private GHSubscriberEvent callOnEvent(
      PullRequestGHEventSubscriber subscriber, String eventPayloadFile) throws IOException {
    GHSubscriberEvent event = createEvent(eventPayloadFile);
    subscriber.onEvent(event);
    return event;
  }

  private GHSubscriberEvent callOnEvent(
      GitHubRepositoryEventSubscriber subscriber, String eventPayloadFile) throws IOException {
    GHSubscriberEvent event = createEvent(eventPayloadFile);
    subscriber.onEvent(event);
    return event;
  }

  private GHSubscriberEvent createEvent(String eventPayloadFile) throws IOException {
    String payload = IOUtils.toString(getClass().getResourceAsStream(eventPayloadFile));
    return new GHSubscriberEvent("myOrigin", null, payload);
  }

  private void waitAndAssertReceived(boolean received) throws InterruptedException {
    long watermark = SCMEvents.getWatermark();
    // event will be fired by subscriber at some point
    SCMEvents.awaitOne(watermark, 1200, TimeUnit.MILLISECONDS);

    assertEquals(
        "Event should have " + ((!received) ? "not " : "") + "been received",
        received,
        TestSCMEventListener.didReceive());
    if (TestSCMEventListener.didError() != null) {
      throw TestSCMEventListener.didError();
    }
  }

  @TestExtension
  public static class TestSCMEventListener extends jenkins.scm.api.SCMEventListener {

    private static boolean eventReceived = false;
    private static Error error = null;

    public void onSCMHeadEvent(SCMHeadEvent<?> event) {
      receiveEvent(event.getType(), event.getOrigin(), event.asCauses());
    }

    public void onSCMSourceEvent(SCMSourceEvent<?> event) {
      receiveEvent(event.getType(), event.getOrigin(), event.asCauses());
    }

    private void receiveEvent(SCMEvent.Type type, String origin, Cause[] causes) {
      eventReceived = true;

      try {
        assertEquals("Event type should be the same", type, firedEventType);
        assertEquals("Event causes should be the same", causes, firedEventCauses);
        assertEquals("Event origin should be the same", origin, ghEvent.getOrigin());
      } catch (Error e) {
        error = e;
      }
    }

    public static boolean didReceive() {
      return eventReceived;
    }

    public static void setReceived(boolean received) {
      eventReceived = received;
    }

    public static Error didError() {
      return error;
    }

    public static void setError(Error e) {
      error = e;
    }
  }
}
