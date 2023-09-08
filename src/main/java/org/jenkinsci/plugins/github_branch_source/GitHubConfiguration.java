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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.util.ListBoxModel;
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

@Extension
public class GitHubConfiguration extends GlobalConfiguration {

    public static GitHubConfiguration get() {
        return GlobalConfiguration.all().get(GitHubConfiguration.class);
    }

    private List<Endpoint> endpoints;

    private ApiRateLimitChecker apiRateLimitChecker;

    public GitHubConfiguration() {
        load();
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        req.bindJSON(this, json);
        return true;
    }

    @NonNull
    public synchronized List<Endpoint> getEndpoints() {
        return endpoints == null ? Collections.emptyList() : Collections.unmodifiableList(endpoints);
    }

    @NonNull
    public synchronized ApiRateLimitChecker getApiRateLimitChecker() {
        if (apiRateLimitChecker == null) {
            return ApiRateLimitChecker.ThrottleForNormalize;
        }
        return apiRateLimitChecker;
    }

    public synchronized void setApiRateLimitChecker(@CheckForNull ApiRateLimitChecker apiRateLimitChecker) {
        this.apiRateLimitChecker = apiRateLimitChecker;
        save();
    }

    /**
     * Fix an apiUri.
     *
     * @param apiUri the api URI.
     * @return the normalized api URI.
     */
    @CheckForNull
    public static String normalizeApiUri(@CheckForNull String apiUri) {
        if (apiUri == null) {
            return null;
        }
        try {
            URI uri = new URI(apiUri).normalize();
            String scheme = uri.getScheme();
            if ("http".equals(scheme) || "https".equals(scheme)) {
                // we only expect http / https, but also these are the only ones where we know the authority
                // is server based, i.e. [userinfo@]server[:port]
                // DNS names must be US-ASCII and are case insensitive, so we force all to lowercase

                String host = uri.getHost() == null ? null : uri.getHost().toLowerCase(Locale.ENGLISH);
                int port = uri.getPort();
                if ("http".equals(scheme) && port == 80) {
                    port = -1;
                } else if ("https".equals(scheme) && port == 443) {
                    port = -1;
                }
                apiUri = new URI(
                                scheme, uri.getUserInfo(), host, port, uri.getPath(), uri.getQuery(), uri.getFragment())
                        .toASCIIString();
            }
        } catch (URISyntaxException e) {
            // ignore, this was a best effort tidy-up
        }
        return apiUri.replaceAll("/$", "");
    }

    public synchronized void setEndpoints(@CheckForNull List<Endpoint> endpoints) {
        endpoints = new ArrayList<>(endpoints == null ? Collections.emptyList() : endpoints);
        // remove duplicates and empty urls
        Set<String> apiUris = new HashSet<>();
        for (Iterator<Endpoint> iterator = endpoints.iterator(); iterator.hasNext(); ) {
            Endpoint endpoint = iterator.next();
            if (StringUtils.isBlank(endpoint.getApiUri()) || apiUris.contains(endpoint.getApiUri())) {
                iterator.remove();
            }
            apiUris.add(endpoint.getApiUri());
        }
        this.endpoints = endpoints;
        save();
    }

    /**
     * Adds an endpoint.
     *
     * @param endpoint the endpoint to add.
     * @return {@code true} if the list of endpoints was modified
     */
    public synchronized boolean addEndpoint(@NonNull Endpoint endpoint) {
        if (StringUtils.isBlank(endpoint.getApiUri())) {
            return false;
        }
        List<Endpoint> endpoints = new ArrayList<>(getEndpoints());
        for (Endpoint ep : endpoints) {
            if (StringUtils.equals(ep.getApiUri(), endpoint.getApiUri())) {
                return false;
            }
        }
        endpoints.add(endpoint);
        setEndpoints(endpoints);
        return true;
    }

    /**
     * Updates an existing endpoint (or adds if missing).
     *
     * @param endpoint the endpoint to update.
     */
    public synchronized void updateEndpoint(@NonNull Endpoint endpoint) {
        if (StringUtils.isBlank(endpoint.getApiUri())) {
            return;
        }
        List<Endpoint> endpoints = new ArrayList<>(getEndpoints());
        boolean found = false;
        for (int i = 0; i < endpoints.size(); i++) {
            Endpoint ep = endpoints.get(i);
            if (StringUtils.equals(ep.getApiUri(), endpoint.getApiUri())) {
                endpoints.set(i, endpoint);
                found = true;
                break;
            }
        }
        if (!found) {
            endpoints.add(endpoint);
        }
        setEndpoints(endpoints);
    }

    /**
     * Removes an endpoint.
     *
     * @param endpoint the endpoint to remove.
     * @return {@code true} if the list of endpoints was modified
     */
    public boolean removeEndpoint(@NonNull Endpoint endpoint) {
        return removeEndpoint(endpoint.getApiUri());
    }

    /**
     * Removes an endpoint.
     *
     * @param apiUri the API URI to remove.
     * @return {@code true} if the list of endpoints was modified
     */
    public synchronized boolean removeEndpoint(@CheckForNull String apiUri) {
        apiUri = normalizeApiUri(apiUri);
        boolean modified = false;
        List<Endpoint> endpoints = new ArrayList<>(getEndpoints());
        for (Iterator<Endpoint> iterator = endpoints.iterator(); iterator.hasNext(); ) {
            if (StringUtils.equals(apiUri, iterator.next().getApiUri())) {
                iterator.remove();
                modified = true;
            }
        }
        setEndpoints(endpoints);
        return modified;
    }

    /**
     * Checks to see if the supplied server URL is defined in the global configuration.
     *
     * @param apiUri the server url to check.
     * @return the global configuration for the specified server url or {@code null} if not defined.
     */
    @CheckForNull
    public synchronized Endpoint findEndpoint(@CheckForNull String apiUri) {
        apiUri = normalizeApiUri(apiUri);
        for (Endpoint endpoint : getEndpoints()) {
            if (StringUtils.equals(apiUri, endpoint.getApiUri())) {
                return endpoint;
            }
        }
        return null;
    }

    public ListBoxModel doFillApiRateLimitCheckerItems() {
        ListBoxModel items = new ListBoxModel();
        for (ApiRateLimitChecker mode : ApiRateLimitChecker.values()) {
            items.add(mode.getDisplayName(), mode.name());
        }
        return items;
    }
}
