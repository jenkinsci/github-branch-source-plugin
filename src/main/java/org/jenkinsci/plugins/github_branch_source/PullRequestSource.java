/*
 * The MIT License
 *
 * Copyright 2016-2017 CloudBees, Inc.
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

/**
 * Used for data migration for a 1.x upgrade.
 *
 * @since 2.2.0
 */
@Deprecated // TODO remove once migration from 1.x is no longer supported
class PullRequestSource {
    private final String sourceOwner;
    private final String sourceRepo;
    private final String sourceBranch;

    PullRequestSource(String sourceOwner, String sourceRepo, String sourceBranch) {
        this.sourceOwner = sourceOwner;
        this.sourceRepo = sourceRepo;
        this.sourceBranch = sourceBranch;
    }

    public String getSourceOwner() {
        return sourceOwner;
    }

    public String getSourceRepo() {
        return sourceRepo;
    }

    public String getSourceBranch() {
        return sourceBranch;
    }
}
