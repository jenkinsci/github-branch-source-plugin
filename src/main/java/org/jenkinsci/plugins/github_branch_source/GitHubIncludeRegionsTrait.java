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
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Item;
import jenkins.scm.api.SCMHeadCategory;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import jenkins.scm.impl.trait.Discovery;
import org.antlr.v4.runtime.misc.NotNull;
import org.apache.tools.ant.types.selectors.SelectorUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.stapler.DataBoundConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * A {@link Discovery} trait for GitHub that will discover branches on the repository.
 *
 * @since 2.2.0
 */
public class GitHubIncludeRegionsTrait extends SCMSourceTrait {
    private static final Logger LOGGER = LoggerFactory.getLogger(GitHubIncludeRegionsTrait.class);

    /**
     * The regions to include for this multi branch project
     */
    private final String includeRegions;

    /**
     * Constructor for stapler.
     *
     * @param includeRegions the strategy id.
     */
    @DataBoundConstructor
    public GitHubIncludeRegionsTrait(String includeRegions) {
        this.includeRegions = includeRegions;
    }

    /**
     * Returns the included regions
     *
     * @return the included regions string.
     */
    public String getIncludeRegions() {
        return this.includeRegions;
    }

    public List<String> getIncludeRegionsList() {
        return Arrays.stream(this.includeRegions.split("\n"))
            .map(e -> e.trim())
            .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void decorateContext(SCMSourceContext<?, ?> context) {
        GitHubSCMSourceContext ctx = (GitHubSCMSourceContext) context;
        ctx.wantBranches(true);
        ctx.withAuthority(new BranchDiscoveryTrait.BranchSCMHeadAuthority());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean includeCategory(@NonNull SCMHeadCategory category) {
        return category.isUncategorized();
    }

    @CheckForNull
    public Match matchFilesToIncludedRegions(List<String> changedFiles) {
        for (String filePath : changedFiles) {
            for (String includedRegionPattern : this.getIncludeRegionsList()) {
                if (SelectorUtils.matchPath(includedRegionPattern, filePath)) {
                    return new Match(filePath, includedRegionPattern);
                }
            }
        }
        return null;
    }

    @CheckForNull
    public static Match isBuildableSCMEvent(SCMSourceOwner owner, @CheckForNull SCMHeadEvent<?> event) {
        if (event == null) {
            LOGGER.info("cant match on null event - returning null");
            return null;
        }

        GHEventPayload.Push payload;
        if (event.getPayload() instanceof GHEventPayload.Push) {
            payload = (GHEventPayload.Push) event.getPayload();
        } else {
            LOGGER.info("can only operate on Github SCM events. Returning no match.");
            return null;
        }

        List<String> commits = collectCommits(payload);

        for (SCMSource src : owner.getSCMSources()) {
            for (SCMSourceTrait trait : src.getTraits()) {
                if (trait instanceof GitHubIncludeRegionsTrait) {
                    GitHubIncludeRegionsTrait ghTrait = (GitHubIncludeRegionsTrait) trait;
                    GitHubIncludeRegionsTrait.Match match = ghTrait.matchFilesToIncludedRegions(commits);
                    match.setProject(owner);
                    if (match != null) {
                        return match;
                    }
                }
            }
        }
        return null;
    }

    @NotNull
    public static List<String> collectCommits(GHEventPayload.Push p) {
        List<String> changes = new ArrayList<>();
        for (GHEventPayload.Push.PushCommit commit : p.getCommits()) {
            changes.addAll(commit.getAdded());
            changes.addAll(commit.getModified());
            changes.addAll(commit.getRemoved());
        }
        return changes;
    }

    /**
     * Our descriptor.
     */
    @Symbol("gitHubIncludeRegionsDiscovery")
    @Extension
    @Discovery
    public static class DescriptorImpl extends SCMSourceTraitDescriptor {

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return "GH Include Regions";
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Class<? extends SCMSourceContext> getContextClass() {
            return GitHubSCMSourceContext.class;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Class<? extends SCMSource> getSourceClass() {
            return GitHubSCMSource.class;
        }
    }

    public static class Match<T extends Item> {
        private final String matchedFile;
        private final String matchedPattern;
        private T project;

        public Match(T project, String matchedFile, String matchedPattern) {
            super();
            this.project = project;
            this.matchedFile = matchedFile;
            this.matchedPattern = matchedPattern;
        }

        public Match(String matchedFile, String matchedPattern) {
            this(null, matchedFile, matchedPattern);
        }

        public void setProject(T project) {
            this.project = project;
        }

        public T getProject() {
            return this.project;
        }

        public String getProjectName() {
            if (this.project == null) return "";
            return this.project.getFullDisplayName();
        }

        public String getMatchedFile() {
            return this.matchedFile;
        }

        public String getMatchedPattern() {
            return this.matchedPattern;
        }

        public String toString() {
            return "\n[" +
                    "Project: " +
                    this.getProjectName() +
                    ", Changed file: " +
                    this.getMatchedFile() +
                    ", Matched pattern: " +
                    this.getMatchedPattern() +
                "]\n";
        }
    }
}
