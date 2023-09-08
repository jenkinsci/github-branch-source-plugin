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
import hudson.Util;
import hudson.model.TaskListener;
import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadOrigin;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import jenkins.scm.api.trait.SCMSourceRequest;
import net.jcip.annotations.GuardedBy;
import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHPermissionType;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRef;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

/**
 * The {@link SCMSourceRequest} for GitHub.
 *
 * @since 2.2.0
 */
public class GitHubSCMSourceRequest extends SCMSourceRequest {
    /** {@code true} if branch details need to be fetched. */
    private final boolean fetchBranches;
    /** {@code true} if tag details need to be fetched. */
    private final boolean fetchTags;
    /** {@code true} if origin pull requests need to be fetched. */
    private final boolean fetchOriginPRs;
    /** {@code true} if fork pull requests need to be fetched. */
    private final boolean fetchForkPRs;
    /** The {@link ChangeRequestCheckoutStrategy} to create for each origin pull request. */
    @NonNull
    private final Set<ChangeRequestCheckoutStrategy> originPRStrategies;
    /** The {@link ChangeRequestCheckoutStrategy} to create for each fork pull request. */
    @NonNull
    private final Set<ChangeRequestCheckoutStrategy> forkPRStrategies;
    /**
     * The set of pull request numbers that the request is scoped to or {@code null} if the request is
     * not limited.
     */
    @CheckForNull
    private final Set<Integer> requestedPullRequestNumbers;
    /**
     * The set of origin branch names that the request is scoped to or {@code null} if the request is
     * not limited.
     */
    @CheckForNull
    private final Set<String> requestedOriginBranchNames;
    /**
     * The set of tag names that the request is scoped to or {@code null} if the request is not
     * limited.
     */
    @CheckForNull
    private final Set<String> requestedTagNames;
    /** The pull request details or {@code null} if not {@link #isFetchPRs()}. */
    @CheckForNull
    private Iterable<GHPullRequest> pullRequests;
    /** The branch details or {@code null} if not {@link #isFetchBranches()}. */
    @CheckForNull
    private Iterable<GHBranch> branches;
    /** The tag details or {@code null} if not {@link #isFetchTags()}. */
    @CheckForNull
    private Iterable<GHRef> tags;
    /** The repository collaborator names or {@code null} if not provided. */
    @CheckForNull
    private Set<String> collaboratorNames;
    /** A connection to the GitHub API or {@code null} if none established yet. */
    @CheckForNull
    private GitHub gitHub;
    /** The repository. */
    @CheckForNull
    private GHRepository repository;
    /** The resolved permissions keyed by user. */
    @NonNull
    @GuardedBy("self")
    private final Map<String, GHPermissionType> permissions = new HashMap<>();
    /** A deferred lookup of the permissions. */
    @CheckForNull
    private GitHubPermissionsSource permissionsSource;

    /**
     * Constructor.
     *
     * @param source the source.
     * @param context the context.
     * @param listener the listener.
     */
    GitHubSCMSourceRequest(SCMSource source, GitHubSCMSourceContext context, TaskListener listener) {
        super(source, context, listener);
        fetchBranches = context.wantBranches();
        fetchTags = context.wantTags();
        fetchOriginPRs = context.wantOriginPRs();
        fetchForkPRs = context.wantForkPRs();
        originPRStrategies = fetchOriginPRs && !context.originPRStrategies().isEmpty()
                ? Collections.unmodifiableSet(EnumSet.copyOf(context.originPRStrategies()))
                : Collections.emptySet();
        forkPRStrategies = fetchForkPRs && !context.forkPRStrategies().isEmpty()
                ? Collections.unmodifiableSet(EnumSet.copyOf(context.forkPRStrategies()))
                : Collections.emptySet();
        Set<SCMHead> includes = context.observer().getIncludes();
        if (includes != null) {
            Set<Integer> pullRequestNumbers = new HashSet<>(includes.size());
            Set<String> branchNames = new HashSet<>(includes.size());
            Set<String> tagNames = new HashSet<>(includes.size());
            for (SCMHead h : includes) {
                if (h instanceof BranchSCMHead) {
                    branchNames.add(h.getName());
                } else if (h instanceof PullRequestSCMHead) {
                    pullRequestNumbers.add(((PullRequestSCMHead) h).getNumber());
                    if (SCMHeadOrigin.DEFAULT.equals(h.getOrigin())) {
                        branchNames.add(((PullRequestSCMHead) h).getOriginName());
                    }
                } else if (h instanceof GitHubTagSCMHead) {
                    tagNames.add(h.getName());
                }
            }
            this.requestedPullRequestNumbers = Collections.unmodifiableSet(pullRequestNumbers);
            this.requestedOriginBranchNames = Collections.unmodifiableSet(branchNames);
            this.requestedTagNames = Collections.unmodifiableSet(tagNames);
        } else {
            requestedPullRequestNumbers = null;
            requestedOriginBranchNames = null;
            requestedTagNames = null;
        }
    }

    /**
     * Returns {@code true} if branch details need to be fetched.
     *
     * @return {@code true} if branch details need to be fetched.
     */
    public final boolean isFetchBranches() {
        return fetchBranches;
    }

    /**
     * Returns {@code true} if tag details need to be fetched.
     *
     * @return {@code true} if tag details need to be fetched.
     */
    public final boolean isFetchTags() {
        return fetchTags;
    }

    /**
     * Returns {@code true} if pull request details need to be fetched.
     *
     * @return {@code true} if pull request details need to be fetched.
     */
    public final boolean isFetchPRs() {
        return isFetchOriginPRs() || isFetchForkPRs();
    }

    /**
     * Returns {@code true} if origin pull request details need to be fetched.
     *
     * @return {@code true} if origin pull request details need to be fetched.
     */
    public final boolean isFetchOriginPRs() {
        return fetchOriginPRs;
    }

    /**
     * Returns {@code true} if fork pull request details need to be fetched.
     *
     * @return {@code true} if fork pull request details need to be fetched.
     */
    public final boolean isFetchForkPRs() {
        return fetchForkPRs;
    }

    /**
     * Returns the {@link ChangeRequestCheckoutStrategy} to create for each origin pull request.
     *
     * @return the {@link ChangeRequestCheckoutStrategy} to create for each origin pull request.
     */
    @NonNull
    public final Set<ChangeRequestCheckoutStrategy> getOriginPRStrategies() {
        return originPRStrategies;
    }

    /**
     * Returns the {@link ChangeRequestCheckoutStrategy} to create for each fork pull request.
     *
     * @return the {@link ChangeRequestCheckoutStrategy} to create for each fork pull request.
     */
    @NonNull
    public final Set<ChangeRequestCheckoutStrategy> getForkPRStrategies() {
        return forkPRStrategies;
    }

    /**
     * Returns the {@link ChangeRequestCheckoutStrategy} to create for pull requests of the specified
     * type.
     *
     * @param fork {@code true} to return strategies for the fork pull requests, {@code false} for
     *     origin pull requests.
     * @return the {@link ChangeRequestCheckoutStrategy} to create for each pull request.
     */
    @NonNull
    public final Set<ChangeRequestCheckoutStrategy> getPRStrategies(boolean fork) {
        if (fork) {
            return fetchForkPRs ? getForkPRStrategies() : Collections.emptySet();
        }
        return fetchOriginPRs ? getOriginPRStrategies() : Collections.emptySet();
    }

    /**
     * Returns the {@link ChangeRequestCheckoutStrategy} to create for each pull request.
     *
     * @return a map of the {@link ChangeRequestCheckoutStrategy} to create for each pull request
     *     keyed by whether the strategy applies to forks or not ({@link Boolean#FALSE} is the key for
     *     origin pull requests)
     */
    public final Map<Boolean, Set<ChangeRequestCheckoutStrategy>> getPRStrategies() {
        Map<Boolean, Set<ChangeRequestCheckoutStrategy>> result = new HashMap<>();
        for (Boolean fork : new Boolean[] {Boolean.TRUE, Boolean.FALSE}) {
            result.put(fork, getPRStrategies(fork));
        }
        return result;
    }

    /**
     * Returns requested pull request numbers.
     *
     * @return the requested pull request numbers or {@code null} if the request was not scoped to a
     *     subset of pull requests.
     */
    @CheckForNull
    public final Set<Integer> getRequestedPullRequestNumbers() {
        return requestedPullRequestNumbers;
    }

    /**
     * Gets requested origin branch names.
     *
     * @return the requested origin branch names or {@code null} if the request was not scoped to a
     *     subset of branches.
     */
    @CheckForNull
    public final Set<String> getRequestedOriginBranchNames() {
        return requestedOriginBranchNames;
    }

    /**
     * Gets requested tag names.
     *
     * @return the requested tag names or {@code null} if the request was not scoped to a subset of
     *     tags.
     */
    @CheckForNull
    public final Set<String> getRequestedTagNames() {
        return requestedTagNames;
    }

    /**
     * Provides the requests with the pull request details.
     *
     * @param pullRequests the pull request details.
     */
    public void setPullRequests(@CheckForNull Iterable<GHPullRequest> pullRequests) {
        this.pullRequests = pullRequests;
    }

    /**
     * Returns the pull request details or an empty list if either the request did not specify to
     * {@link #isFetchPRs()} or if the pull request details have not been provided by {@link
     * #setPullRequests(Iterable)} yet.
     *
     * @return the details of pull requests, may be limited by {@link
     *     #getRequestedPullRequestNumbers()} or may be empty if not {@link #isFetchPRs()}
     */
    @NonNull
    public Iterable<GHPullRequest> getPullRequests() {
        return Util.fixNull(pullRequests);
    }

    /**
     * Provides the requests with the branch details.
     *
     * @param branches the branch details.
     */
    public final void setBranches(@CheckForNull Iterable<GHBranch> branches) {
        this.branches = branches;
    }

    /**
     * Returns the branch details or an empty list if either the request did not specify to {@link
     * #isFetchBranches()} or if the branch details have not been provided by {@link
     * #setBranches(Iterable)} yet.
     *
     * @return the branch details (may be empty)
     */
    @NonNull
    public final Iterable<GHBranch> getBranches() {
        return Util.fixNull(branches);
    }

    /**
     * Provides the requests with the tag details.
     *
     * @param tags the tag details.
     */
    public final void setTags(@CheckForNull Iterable<GHRef> tags) {
        this.tags = tags;
    }

    /**
     * Returns the branch details or an empty list if either the request did not specify to {@link
     * #isFetchBranches()} or if the branch details have not been provided by {@link
     * #setBranches(Iterable)} yet.
     *
     * @return the branch details (may be empty)
     */
    @NonNull
    public final Iterable<GHRef> getTags() {
        return Util.fixNull(tags);
    }

    // TODO Iterable<GHTag> getTags() and setTags(...)

    /**
     * Provides the request with the names of the repository collaborators.
     *
     * @param collaboratorNames the names of the repository collaborators.
     */
    public final void setCollaboratorNames(@CheckForNull Set<String> collaboratorNames) {
        this.collaboratorNames = collaboratorNames;
    }

    /**
     * Returns the names of the repository collaborators or {@code null} if those details have not
     * been provided yet.
     *
     * @return the names of the repository collaborators or {@code null} if those details have not
     *     been provided yet.
     */
    public final Set<String> getCollaboratorNames() {
        return collaboratorNames;
    }

    /**
     * Checks the API rate limit and sleeps if over-used until the remaining limit is on-target for
     * expected usage.
     *
     * @throws IOException if the rate limit could not be obtained.
     * @throws InterruptedException if interrupted while waiting.
     * @deprecated rate limit checking is done automatically
     */
    @Deprecated
    public final void checkApiRateLimit() throws IOException, InterruptedException {
        if (gitHub != null) {
            Connector.configureLocalRateLimitChecker(listener(), Objects.requireNonNull(gitHub));
        }
    }

    /**
     * Returns the {@link GitHub} API connector to use for the request.
     *
     * @return the {@link GitHub} API connector to use for the request or {@code null} if caller
     *     should establish their own.
     */
    @CheckForNull
    public GitHub getGitHub() {
        return gitHub;
    }

    /**
     * Provides the {@link GitHub} API connector to use for the request.
     *
     * @param gitHub {@link GitHub} API connector to use for the request.
     */
    public void setGitHub(@CheckForNull GitHub gitHub) {
        this.gitHub = gitHub;
    }

    /**
     * Returns the {@link GHRepository}.
     *
     * @return the {@link GHRepository}.
     */
    public GHRepository getRepository() {
        return repository;
    }

    /**
     * Sets the {@link GHRepository}.
     *
     * @param repository the {@link GHRepository}.
     */
    public void setRepository(GHRepository repository) {
        this.repository = repository;
    }

    /** {@inheritDoc} */
    @Override
    public void close() throws IOException {
        if (pullRequests instanceof Closeable) {
            ((Closeable) pullRequests).close();
        }
        if (branches instanceof Closeable) {
            ((Closeable) branches).close();
        }
        if (permissionsSource instanceof Closeable) {
            ((Closeable) permissionsSource).close();
        }
        super.close();
    }

    /**
     * Returns the permissions of the supplied user.
     *
     * @param username the user.
     * @return the permissions of the supplied user.
     * @throws IOException if the permissions could not be retrieved.
     * @throws InterruptedException if interrupted while retrieving the permissions.
     */
    public GHPermissionType getPermissions(String username) throws IOException, InterruptedException {
        synchronized (permissions) {
            if (permissions.containsKey(username)) {
                return permissions.get(username);
            }
        }
        if (permissionsSource != null) {
            GHPermissionType result = permissionsSource.fetch(username);
            synchronized (permissions) {
                permissions.put(username, result);
            }
            return result;
        }
        if (repository != null && username.equalsIgnoreCase(repository.getOwnerName())) {
            return GHPermissionType.ADMIN;
        }
        if (collaboratorNames != null && collaboratorNames.contains(username)) {
            return GHPermissionType.WRITE;
        }
        return GHPermissionType.NONE;
    }

    /**
     * Returns the permission source.
     *
     * @return the permission source.
     */
    @CheckForNull
    public GitHubPermissionsSource getPermissionsSource() {
        return permissionsSource;
    }

    /**
     * Sets the permission source.
     *
     * @param permissionsSource the permission source.
     */
    public void setPermissionsSource(@CheckForNull GitHubPermissionsSource permissionsSource) {
        this.permissionsSource = permissionsSource;
    }
}
