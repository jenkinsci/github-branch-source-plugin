/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
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
 */

package org.jenkinsci.plugins.github_branch_source;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.util.io.FileBoolean;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHHook;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;

/** Manages the GitHub organization webhook. */
public class GitHubOrgWebHook {

    private static final Logger LOGGER = Logger.getLogger(GitHubOrgWebHook.class.getName());
    private static final List<GHEvent> EVENTS =
            Arrays.asList(GHEvent.REPOSITORY, GHEvent.PUSH, GHEvent.PULL_REQUEST, GHEvent.PULL_REQUEST_REVIEW_COMMENT);

    public static void register(GitHub hub, String orgName) throws IOException {
        String rootUrl = System.getProperty("jenkins.hook.url");
        if (rootUrl == null) {
            rootUrl = Jenkins.get().getRootUrl();
        }
        if (rootUrl == null) {
            return;
        }
        GHUser u = hub.getUser(orgName);
        FileBoolean orghook = new FileBoolean(getTrackingFile(orgName));
        if (orghook.isOff()) {
            try {
                GHOrganization org = hub.getOrganization(orgName);
                String url = rootUrl + "github-webhook/";
                boolean found = false;
                for (GHHook hook : org.getHooks()) {
                    if (hook.getConfig().get("url").equals(url)) {
                        found = !hook.getEvents().containsAll(EVENTS);
                        break;
                    }
                }
                if (!found) {
                    org.createWebHook(new URL(url), EVENTS);
                    LOGGER.log(Level.INFO, "A webhook was registered for the organization {0}", org.getHtmlUrl());
                    // keep trying until the hook gets successfully installed
                    // if the user doesn't have the proper permission, this will cause
                    // a repeated failure, but this code doesn't execute too often.
                }
                orghook.on();
            } catch (FileNotFoundException e) {
                LOGGER.log(
                        Level.WARNING,
                        "Failed to register GitHub Org hook to {0} (missing permissions?): {1}",
                        new Object[] {u.getHtmlUrl(), e.getMessage()});
                LOGGER.log(Level.FINE, null, e);
            } catch (RateLimitExceededException e) {
                LOGGER.log(Level.WARNING, "Failed to register GitHub Org hook to {0}: {1}", new Object[] {
                    u.getHtmlUrl(), e.getMessage()
                });
                LOGGER.log(Level.FINE, null, e);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to register GitHub Org hook to " + u.getHtmlUrl(), e);
            }
        }
    }

    private static File getTrackingFile(String orgName) {
        return new File(Jenkins.get().getRootDir(), "github-webhooks/GitHubOrgHook." + orgName);
    }

    public static void deregister(GitHub hub, String orgName) throws IOException {
        String rootUrl = Jenkins.get().getRootUrl();
        if (rootUrl == null) {
            return;
        }
        GHUser u = hub.getUser(orgName);
        FileBoolean orghook = new FileBoolean(getTrackingFile(orgName));
        if (orghook.isOn()) {
            try {
                GHOrganization org = hub.getOrganization(orgName);
                String url = rootUrl + "github-webhook/";
                for (GHHook hook : org.getHooks()) {
                    if (hook.getConfig().get("url").equals(url)) {
                        hook.delete();
                        LOGGER.log(Level.INFO, "A webhook was deregistered for the organization {0}", org.getHtmlUrl());
                        // keep trying until the hook gets successfully uninstalled
                        // if the user doesn't have the proper permission, this will cause
                        // a repeated failure, but this code doesn't execute too often.
                    }
                }
                orghook.off();
            } catch (FileNotFoundException e) {
                LOGGER.log(
                        Level.WARNING,
                        "Failed to deregister GitHub Org hook to {0} (missing permissions?): {1}",
                        new Object[] {u.getHtmlUrl(), e.getMessage()});
                LOGGER.log(Level.FINE, null, e);
            } catch (RateLimitExceededException e) {
                LOGGER.log(Level.WARNING, "Failed to deregister GitHub Org hook to {0}: {1}", new Object[] {
                    u.getHtmlUrl(), e.getMessage()
                });
                LOGGER.log(Level.FINE, null, e);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to deregister GitHub Org hook to " + u.getHtmlUrl(), e);
            }
        }
    }
}
