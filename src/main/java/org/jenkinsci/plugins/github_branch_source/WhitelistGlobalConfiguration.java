/*
 * The MIT License
 *
 * Copyright 2015 CloudBees, Inc.
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

import org.jenkinsci.plugins.github_branch_source.WhitelistSource;
import java.util.Arrays;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.StaplerRequest;

/**
 * A configuration entry in the central Jenkins configuration page for defining a global trusted
 * pull request author whitelist.
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
        userIds = getUserIds();
        sanitizeUserIds(userIds);
        return true;
    }

    @NonNull
    public synchronized String getWhitelist() {
        return pullRequestWhitelist;
    }

    public synchronized void setPullRequestWhitelist(@CheckForNull String whitelist) {
        this.pullRequestWhitelist = whitelist;
        save();
    }

    @Override
    public Set<String> getUserIds() {
        return new HashSet<String>(pullRequestWhitelist == null ? Collections.<String>emptySet() : Arrays.asList(pullRequestWhitelist.split("\\s+")));
    }

    @Override
    public boolean contains(String userId) {
        return userIds.contains(userId);
    }

    private void sanitizeUserIds(Set<String> userIds) {
        String[] stringsArray = userIds.toArray(new String[0]);
        for (int i = 0; i < stringsArray.length; ++i) {
            stringsArray[i] = stringsArray[i].toLowerCase().trim();
        }
        userIds.clear();
        userIds.addAll(Arrays.asList(stringsArray));
    }

}
