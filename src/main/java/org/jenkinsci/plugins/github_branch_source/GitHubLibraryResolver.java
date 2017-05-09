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

import hudson.Extension;
import hudson.model.Job;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import jenkins.plugins.git.GitSCMSource;
import org.jenkinsci.plugins.workflow.libs.LibraryConfiguration;
import org.jenkinsci.plugins.workflow.libs.LibraryResolver;
import org.jenkinsci.plugins.workflow.libs.SCMSourceRetriever;

/**
 * Allows libraries to be loaded on the fly from GitHub.
 */
@Extension(optional = true)
public class GitHubLibraryResolver extends LibraryResolver {

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isTrusted() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<LibraryConfiguration> forJob(Job<?,?> job, Map<String,String> libraryVersions) {
        List<LibraryConfiguration> libs = new ArrayList<>();
        for (Map.Entry<String,String> entry : libraryVersions.entrySet()) {
            if (entry.getKey().matches("github[.]com/([^/]+)/([^/]+)")) {
                String name = entry.getKey();
                // Currently GitHubSCMSource offers no particular advantage here over GitSCMSource.
                LibraryConfiguration lib = new LibraryConfiguration(name, new SCMSourceRetriever(new GitSCMSource(null, "https://" + name + ".git", "", "*", "", true)));
                lib.setDefaultVersion("master");
                libs.add(lib);
            }
        }
        return libs;
    }

}
