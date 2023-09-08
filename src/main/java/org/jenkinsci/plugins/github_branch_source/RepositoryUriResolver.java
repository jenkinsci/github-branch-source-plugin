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
import java.net.MalformedURLException;
import java.net.URL;

/** Resolves the URI of a GitHub repository from the API URI, owner and repository name. */
public abstract class RepositoryUriResolver {

    /**
     * Resolves the URI of a repository.
     *
     * @param apiUri the API URL of the GitHub server.
     * @param owner the owner of the repository.
     * @param repository the name of the repository.
     * @return the GIT URL of the repository.
     */
    @NonNull
    public abstract String getRepositoryUri(@NonNull String apiUri, @NonNull String owner, @NonNull String repository);

    /**
     * Helper method that returns the hostname of a GitHub server from its API URL.
     *
     * @param apiUri the API URL.
     * @return the hostname of a GitHub server
     */
    @NonNull
    public static String hostnameFromApiUri(@CheckForNull String apiUri) {
        if (apiUri != null) {
            try {
                URL endpoint = new URL(apiUri);
                if (!"api.github.com".equals(endpoint.getHost())) {
                    return endpoint.getHost();
                }
            } catch (MalformedURLException e) {
                // ignore
            }
        }
        return "github.com";
    }
}
