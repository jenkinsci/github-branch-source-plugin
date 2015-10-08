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

package org.jenkinsci.plugins.github.multibranch;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

@Extension public class GitHubConfiguration extends GlobalConfiguration {

    public static GitHubConfiguration get() {
        return GlobalConfiguration.all().get(GitHubConfiguration.class);
    }

    private List<Endpoint> endpoints;

    public GitHubConfiguration() {
        load();
    }

    @Override public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        req.bindJSON(this, json);
        return true;
    }

    @NonNull
    public List<Endpoint> getEndpoints() {
        return endpoints == null ? Collections.<Endpoint>emptyList() : Collections.unmodifiableList(endpoints);
    }

    public void setEndpoints(@CheckForNull List<Endpoint> endpoints) {
        endpoints = new ArrayList<Endpoint>(endpoints == null ? Collections.<Endpoint>emptyList() : endpoints);
        // remove duplicates and empty urls
        Set<String> apiUris = new HashSet<String>();
        for (Iterator<Endpoint> iterator = endpoints.iterator(); iterator.hasNext(); ) {
            Endpoint endpoint = iterator.next();
            if (endpoint.getApiUri() == null || apiUris.contains(endpoint.getApiUri())) {
                iterator.remove();
            }
            apiUris.add(endpoint.getApiUri());
        }
        this.endpoints = endpoints;
        save();
    }

}
