/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.Iterator;

/**
 * A configuration entry in the central Jenkins configuration page for defining a global trusted
 * pull request author whitelist.
 *
 * @since 2.3.2
 */
@Extension
public class WhitelistGlobalConfiguration extends GlobalConfiguration implements WhitelistSource {

    public static WhitelistGlobalConfiguration get() {
        return GlobalConfiguration.all().get(WhitelistGlobalConfiguration.class);
    }

    private String pullRequestWhitelist;

    private Set<String> userIds;

    public WhitelistGlobalConfiguration() {
        load();
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        req.bindJSON(this, json);
        userIds = extractUserIds(getPullRequestWhitelist());
        return true;
    }

    public synchronized String getPullRequestWhitelist() {
        return pullRequestWhitelist;
    }

    public synchronized void setPullRequestWhitelist(@CheckForNull String whitelist) {
        this.pullRequestWhitelist = whitelist;
        save();
    }

    @Override
    public synchronized Set<String> getUserIds() {
        return extractUserIds(pullRequestWhitelist);
    }

    @Override
    public synchronized boolean contains(String userId) {
        // GitHub user IDs are case-insensitive
        return getUserIds().contains(userId.toLowerCase().trim());
    }

    private Set<String> extractUserIds(String whitelist) {
        Set<String> userIds = new HashSet<String>(whitelist == null ? Collections.<String>emptySet() : Arrays.asList(whitelist.split("\\s+")));
        Iterator<String> iterator = userIds.iterator();
        Set<String> sanitizedSet = new HashSet<String>();
        while (iterator.hasNext()) {
            String sanitizedString = iterator.next().toLowerCase().trim();
            if (!sanitizedString.isEmpty()) {
                sanitizedSet.add(sanitizedString);
            }
        }
        return sanitizedSet;
    }

}
