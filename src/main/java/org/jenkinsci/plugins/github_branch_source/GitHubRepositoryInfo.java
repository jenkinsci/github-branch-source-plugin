/*
 * The MIT License
 *
 * Copyright 2019 CloudBees, Inc.
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

import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.commons.lang.StringUtils;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Used to compute values for GitHubSCMSource from a user-specified repository URL.
 * 
 * TODO: Is it possible to compute the API URI from just a repository URL, or not because of the possibility of proxies, etc.?
 * Is it worth making a guess based on the specified host?
 */
class GitHubRepositoryInfo {
    @NonNull
    private final String repoOwner;

    @NonNull
    private final String repository;

    @NonNull
    private final String repositoryUrl;

    private GitHubRepositoryInfo(String repoOwner, String repository, String repositoryUrl) {
        this.repoOwner = repoOwner;
        this.repository = repository;
        this.repositoryUrl = repositoryUrl;
    }

    public String getRepoOwner() {
        return repoOwner;
    }

    public String getRepository() {
        return repository;
    }

    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    @NonNull
    public static GitHubRepositoryInfo forRepositoryUrl(@NonNull String repositoryUrl) {
        String trimmedRepoUrl = StringUtils.removeEnd(repositoryUrl.trim(), ".git");
        if (StringUtils.isBlank(trimmedRepoUrl)) {
            throw new IllegalArgumentException("Repository URL must not be empty");
        }
        URL url;
        try {
            url = new URL(trimmedRepoUrl);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
        String[] pathParts = StringUtils.removeStart(url.getPath(), "/").split("/");
        if (pathParts.length != 2) {
            throw new IllegalArgumentException("Invalid repository URL: " + repositoryUrl);
        } else {
            String repoOwner = pathParts[0];
            String repository = pathParts[1];
            return new GitHubRepositoryInfo(repoOwner, repository, repositoryUrl.trim());
        }
    }

}
