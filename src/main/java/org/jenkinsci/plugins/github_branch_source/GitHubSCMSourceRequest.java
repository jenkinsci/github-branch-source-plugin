package org.jenkinsci.plugins.github_branch_source;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.model.TaskListener;
import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadOrigin;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import jenkins.scm.api.mixin.TagSCMHead;
import jenkins.scm.api.trait.SCMSourceRequest;
import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GitHub;

public class GitHubSCMSourceRequest extends SCMSourceRequest {

    private final boolean fetchBranches;
    private final boolean fetchTags;
    private final boolean fetchOriginPRs;
    private final boolean fetchForkPRs;
    private final Set<ChangeRequestCheckoutStrategy> originPRStrategies;
    private final Set<ChangeRequestCheckoutStrategy> forkPRStrategies;
    private final Set<Integer> requestedPullRequestNumbers;
    private final Set<String> requestedOriginBranchNames;
    private final Set<String> requestedTagNames;
    private Iterable<GHPullRequest> pullRequests;
    private Iterable<GHBranch> branches;
    private Set<String> collaboratorNames;
    private GitHub gitHub;

    GitHubSCMSourceRequest(SCMSource source, GitHubSCMSourceContext builder, TaskListener listener) {
        super(source, builder, listener);
        fetchBranches = builder.wantBranches();
        fetchTags = builder.wantTags();
        fetchOriginPRs = builder.wantOriginPRs();
        fetchForkPRs = builder.wantForkPRs();
        originPRStrategies = fetchOriginPRs && !builder.originPRStrategies().isEmpty()
                ? Collections.unmodifiableSet(EnumSet.copyOf(builder.originPRStrategies()))
                : Collections.<ChangeRequestCheckoutStrategy>emptySet();
        forkPRStrategies = fetchForkPRs && !builder.forkPRStrategies().isEmpty()
                ? Collections.unmodifiableSet(EnumSet.copyOf(builder.forkPRStrategies()))
                : Collections.<ChangeRequestCheckoutStrategy>emptySet();
        Set<SCMHead> includes = builder.observer().getIncludes();
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
                } else if (h instanceof TagSCMHead) { // TODO replace with concrete class when tag support added
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

    public boolean isFetchBranches() {
        return fetchBranches;
    }

    public boolean isFetchTags() {
        return fetchTags;
    }

    public boolean isFetchPRs() {
        return fetchOriginPRs || fetchForkPRs;
    }

    public boolean isFetchOriginPRs() {
        return fetchOriginPRs;
    }

    public boolean isFetchForkPRs() {
        return fetchForkPRs;
    }

    public Set<ChangeRequestCheckoutStrategy> getOriginPRStrategies() {
        return originPRStrategies;
    }

    public Set<ChangeRequestCheckoutStrategy> getForkPRStrategies() {
        return forkPRStrategies;
    }

    public Set<ChangeRequestCheckoutStrategy> getPRStrategies(boolean fork) {
        if (fork) {
            return fetchForkPRs ? forkPRStrategies : Collections.<ChangeRequestCheckoutStrategy>emptySet();
        }
        return fetchOriginPRs ? originPRStrategies : Collections.<ChangeRequestCheckoutStrategy>emptySet();
    }

    public Map<Boolean, Set<ChangeRequestCheckoutStrategy>> getPRStrategies() {
        Map<Boolean, Set<ChangeRequestCheckoutStrategy>> result = new HashMap<>();
        for (Boolean fork : new Boolean[]{Boolean.TRUE, Boolean.FALSE}) {
            result.put(fork, getPRStrategies(fork));
        }
        return result;
    }

    public void setPullRequests(Iterable<GHPullRequest> pullRequests) {
        this.pullRequests = pullRequests;
    }

    public Iterable<GHPullRequest> getPullRequests() {
        return pullRequests;
    }

    public void setBranches(Iterable<GHBranch> branches) {
        this.branches = branches;
    }

    public Iterable<GHBranch> getBranches() {
        return branches;
    }

    @CheckForNull
    public Set<Integer> getRequestedPullRequestNumbers() {
        return requestedPullRequestNumbers;
    }

    @CheckForNull
    public Set<String> getRequestedOriginBranchNames() {
        return requestedOriginBranchNames;
    }

    @CheckForNull
    public Set<String> getRequestedTagNames() {
        return requestedTagNames;
    }

    @Override
    public void close() throws IOException {
        if (pullRequests instanceof Closeable) {
            ((Closeable) pullRequests).close();
        }
        if (branches instanceof Closeable) {
            ((Closeable) branches).close();
        }
        super.close();
    }

    public void setCollaboratorNames(Set<String> collaboratorNames) {
        this.collaboratorNames = collaboratorNames;
    }

    public Set<String> getCollaboratorNames() {
        return collaboratorNames;
    }

    public void checkApiRateLimit() throws IOException, InterruptedException {
        if (gitHub != null) {
            Connector.checkApiRateLimit(listener(), gitHub);
        }
    }

    public GitHub getGitHub() {
        return gitHub;
    }

    public void setGitHub(GitHub gitHub) {
        this.gitHub = gitHub;
    }

}
