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
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;

import hudson.model.Item;
import jenkins.scm.api.SCMHeadCategory;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import jenkins.scm.api.trait.SCMTrait;
import jenkins.scm.impl.trait.Discovery;
import org.antlr.v4.runtime.misc.NotNull;
import org.apache.tools.ant.types.selectors.SelectorUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
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
    private Map<String, String> lastMatchedShas;
    private String matchedShas;

    /**
     * Constructor for stapler.
     *
     * @param includeRegions the strategy id.
     */
    @DataBoundConstructor
    public GitHubIncludeRegionsTrait(String includeRegions) {
        this.includeRegions = includeRegions;
        this.lastMatchedShas = new HashMap<>();
        this.matchedShas = "";
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

    @CheckForNull
    public String getLastMatchedShaForBranch(String branch) {
        return this.getMatchedShaMap().get(branch);// .getOrDefault(branch, "");
    }

    public void setLastMatchedShaForBranch(String branch, String lastMatchedSHA) {
        Map<String, String> map = this.getMatchedShaMap();
        map.put(branch, lastMatchedSHA);
        this.setMatchedShaString();
    }

    public String getMatchedShas() {
        return this.matchedShas;
    }

    @DataBoundSetter
    public void setLastMatchedShas(Map<String, String> lastMatched) {
        this.lastMatchedShas = lastMatched;
    }

    @DataBoundSetter
    public void setMatchedShas(String shas) {
        this.matchedShas = shas;
    }

    public Map<String, String> getMatchedShaMap() {
        LOGGER.info("building sha map");
        long start_time = System.nanoTime();

        this.lastMatchedShas = new HashMap<>();
        if (this.matchedShas == null) {
            this.matchedShas = "";
            return this.lastMatchedShas;
        }

        Arrays.stream(this.matchedShas.split("\n"))
            .forEach(e -> {
                String[] parts = e.split(":");
                if (parts.length == 2) {
                    this.lastMatchedShas.put(parts[0], parts[1]);
                }
            });

        long end_time = System.nanoTime();
        LOGGER.info("built map in {} ms", (end_time - start_time) / 1e6);
        return this.lastMatchedShas;
    }

    public void setMatchedShaString() {
        LOGGER.info("building matched sha string from map");
        long start_time = System.nanoTime();

        StringBuilder collectedMatches = new StringBuilder();
        for (Map.Entry match : this.lastMatchedShas.entrySet()) {
            collectedMatches.append(match.getKey());
            collectedMatches.append(":");
            collectedMatches.append(match.getValue());
            collectedMatches.append("\n");
        }

        this.matchedShas = collectedMatches.toString();
        long end_time = System.nanoTime();
        LOGGER.info("built string in {} ms", (end_time - start_time) / 1e6);
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

    @NotNull
    public boolean matchFilesToIncludedRegions(HashMap<String, List<String>> changedFiles) {
        List<String> includedRegions = this.getIncludeRegionsList();
        for (Map.Entry<String, List<String>> entry : changedFiles.entrySet()) {
            for (String includedRegionPattern : includedRegions) {
                for (String filePath : entry.getValue()) {
                    if (SelectorUtils.matchPath(includedRegionPattern, filePath)) {
                        LOGGER.info("Found commit {} with changed file {} matching pattern {}", entry.getKey(), filePath, includedRegionPattern);
                        return true;
                    }
                }
            }
        }

        LOGGER.info("No commits had matching files changed");
        return false;
    }

    public static String isBuildableSCMEvent(@NotNull SCMSourceOwner owner, @NotNull SCMHeadEvent event) {
        String logPrefix = "[" + owner.getFullName() + "]:";

        GHEventPayload.Push payload = getGHEventPayload(event);
        String branch = getBranchFromEvent(event);
        if (payload == null || branch == null) {
            LOGGER.info("{} Could not parse payload: {} or branch: {}", logPrefix, payload, branch);
            return "";
        }

        HashMap<String, List<String>> commits = collectCommits(payload);
        List<GitHubIncludeRegionsTrait> traits = collectTraitsFromOwner(owner);

        // No GithubIncludedRegionTraits were defined for this job owner
        if (traits.isEmpty()) {
            return payload.getHead();
        } else if (traits.size() > 1) {
            LOGGER.warn("{} Found multiple GithubIncludedRegionTrait -- please only specify one per job. Continuing with the first one.", logPrefix);
        }

        GitHubIncludeRegionsTrait ghTrait = traits.get(0);
        boolean matched = ghTrait.matchFilesToIncludedRegions(commits);

        // No initial match against included regions
        if (!matched) {
            LOGGER.info("{} No files in commits {} matched any included regions for build {}", logPrefix, commits.keySet(), ghTrait.getIncludeRegionsList());
            LOGGER.info("{} Attempting to get previously set matching commit", logPrefix);
            String lastMatchedSha = ghTrait.getLastMatchedShaForBranch(branch);

            // Found previously set match
            if (lastMatchedSha != null) {
                LOGGER.info("{} Found previously set match ({}) for branch ({})", logPrefix, lastMatchedSha, branch);
                return lastMatchedSha;
            }

            ghTrait.setLastMatchedShaForBranch(branch, payload.getHead());
            return payload.getHead();
        }

        // We found a match against included regions
        LOGGER.info("{} Found a commit which matched our included regions -- setting last matched sha for branch {} to current head of {}", logPrefix, branch, payload.getHead());
        ghTrait.setLastMatchedShaForBranch(branch, payload.getHead());
        return payload.getHead();
    }

    public static List<GitHubIncludeRegionsTrait> collectTraitsFromOwner(@NotNull SCMSourceOwner owner) {
        ArrayList<GitHubIncludeRegionsTrait> filtered = new ArrayList<>();
        for (SCMSource src : owner.getSCMSources()) {
            List<SCMSourceTrait> traits = src.getTraits() != null ? src.getTraits() : new ArrayList<>();

            for (SCMSourceTrait trait : traits) {
                if (trait instanceof GitHubIncludeRegionsTrait) {
                    filtered.add((GitHubIncludeRegionsTrait) trait);
                }
            }
        }

        return filtered;
    }

    public static String getOrSetLastBuiltCommit(@NotNull SCMSourceOwner owner, @NotNull GHBranch branch) {
        List<GitHubIncludeRegionsTrait> traits = collectTraitsFromOwner(owner);
        if (traits.isEmpty()) {
            LOGGER.info("[{}]: No GithubIncludedRegionTrait for owner", owner.getFullName());
            return branch.getSHA1();
        }

        GitHubIncludeRegionsTrait ghTrait = traits.get(0);
        String sha = ghTrait.getLastMatchedShaForBranch(branch.getName());
        if (sha != null) {
            return sha;
        }

        LOGGER.info("[{}]: No sha was set for branch {} - setting value to {}", owner.getFullName(), branch.getName(), branch.getSHA1());
        ghTrait.setLastMatchedShaForBranch(branch.getName(), branch.getSHA1());
        return branch.getSHA1();
    }

    public static GHEventPayload.Push getGHEventPayload(@Nullable SCMHeadEvent event) {
        if (event == null) {
            return null;
        }

        GHEventPayload.Push payload;

        try {
            payload = (GHEventPayload.Push) event.getPayload();
        } catch (Exception e) {
            LOGGER.error("Unable to cash event to GHEventPayload: " + e);
            return null;
        }

        return payload;
    }

    @CheckForNull
    public static String getBranchFromEvent(@Nullable SCMHeadEvent event) {
        GHEventPayload.Push payload = getGHEventPayload(event);
        return getBranchFromPayload(payload);
    }

    @CheckForNull
    public static String getBranchFromPayload(@Nullable GHEventPayload.Push payload) {
        if (payload == null) {
            return null;
        }

        String[] parts = payload.getRef().split("/");
        if (parts.length == 0) {
            LOGGER.info("Could not parse branch from parts {}", parts.toString());
            return null;
        }

        String branch = parts[parts.length - 1];
        LOGGER.info("Got branch {}", branch);
        return branch;
    }

    @NotNull
    public static HashMap<String, List<String>> collectCommits(GHEventPayload.Push p) {
        HashMap<String, List<String>> changesBySha = new HashMap<>();
        for (GHEventPayload.Push.PushCommit commit : p.getCommits()) {
            List<String> changes = new ArrayList<>();
            changes.addAll(commit.getAdded());
            changes.addAll(commit.getModified());
            changes.addAll(commit.getRemoved());
            changesBySha.put(commit.getSha(), changes);
        }
        return changesBySha;
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

    enum MatchType {
        MATCH, // when there was gh included region and we found a matching file
        MISMATCH, // when there was a gh included region and we didnt find a matching file
        INVALID // an issue processing the event or payload, allowing SCMSource to move forward
    }

    public static class Match<T extends Item> {
        private final String matchedCommit;
        private final String matchedFile;
        private final String matchedPattern;
        private T project;
        private MatchType matchType;

        public Match(MatchType matchType) {
            this(null, null, null, null, matchType);
        }

        public Match(String matchedFile, String matchedPattern, String matchedCommit, MatchType matchType) {
            this(null, matchedFile, matchedPattern, matchedCommit, matchType);
        }

        public Match(T project, String matchedFile, String matchedPattern, String matchedCommit, MatchType matchType) {
            super();
            this.project = project;
            this.matchedFile = matchedFile;
            this.matchedPattern = matchedPattern;
            this.matchedCommit = matchedCommit;
            this.matchType = matchType;
        }

        public void setProject(@NotNull T project) {
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

        public MatchType getMatchType() {
            return this.matchType;
        }

        public String getMatchedCommit() {
            return this.matchedCommit;
        }

        public String toString() {
            return "\n[" +
                    "Project: " +
                    this.getProjectName() +
                    ", Changed file: " +
                    this.getMatchedFile() +
                    ", Matched pattern: " +
                    this.getMatchedPattern() +
                    ", Matched commit: " +
                    this.getMatchedCommit() +
                "]\n";
        }
    }
}
