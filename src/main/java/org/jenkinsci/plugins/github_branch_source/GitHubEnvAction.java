/*
 * The MIT License
 *
 * Copyright 2019 Tim Jacomb
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

import hudson.model.InvisibleAction;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Caches SCM values for a Run
 */
@Restricted(NoExternalUse.class)
public class GitHubEnvAction extends InvisibleAction {

    private static final String CHANGE_SOURCE_COMMIT_ID = "CHANGE_SOURCE_COMMIT_ID";
    private static final String CHANGE_MERGE_COMMIT_ID = "CHANGE_MERGE_COMMIT_ID";
    private static final String CHANGE_BASE_COMMIT_ID = "CHANGE_BASE_COMMIT_ID";
    private static final String CHANGE_BRANCH = "CHANGE_BRANCH";


    private final Map<String, String> values;

    public GitHubEnvAction(String sourceCommit, String branch) {
        HashMap<String, String> theValues = new HashMap<>();
        theValues.put(CHANGE_SOURCE_COMMIT_ID, sourceCommit);
        theValues.put(CHANGE_BRANCH, branch);

        this.values = theValues;
    }

    public GitHubEnvAction(String sourceCommit, String mergeCommit, String baseCommit) {
        Map<String, String> theValues = new HashMap<>();
        theValues.put(CHANGE_SOURCE_COMMIT_ID, sourceCommit);
        theValues.put(CHANGE_MERGE_COMMIT_ID, mergeCommit);
        theValues.put(CHANGE_BASE_COMMIT_ID, baseCommit);

        this.values = theValues;
    }

    public Map<String, String> getValues() {
        return values;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GitHubEnvAction action = (GitHubEnvAction) o;
        return Objects.equals(values, action.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(values);
    }

    @Override
    public String toString() {
        return String.format("GitHubEnvAction{values=%s}", values);
    }
}
