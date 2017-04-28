package org.jenkinsci.plugins.github_branch_source;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.TaskListener;
import java.util.EnumSet;
import java.util.Set;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import jenkins.scm.api.trait.SCMSourceContext;

public class GitHubSCMSourceContext
        extends SCMSourceContext<GitHubSCMSourceContext, GitHubSCMSourceRequest> {

    private boolean wantBranches;
    private boolean wantTags;
    private boolean wantOriginPRs;
    private boolean wantForkPRs;
    private Set<ChangeRequestCheckoutStrategy> originPRStrategies = EnumSet.noneOf(ChangeRequestCheckoutStrategy.class);
    private Set<ChangeRequestCheckoutStrategy> forkPRStrategies = EnumSet.noneOf(ChangeRequestCheckoutStrategy.class);

    public GitHubSCMSourceContext(@NonNull GitHubSCMSource source, @CheckForNull SCMSourceCriteria criteria,
                                  @NonNull SCMHeadObserver observer) {
        super(criteria, observer);
    }

    @Override
    public GitHubSCMSourceRequest newRequest(@NonNull SCMSource source, @CheckForNull TaskListener listener) {
        return new GitHubSCMSourceRequest(source, this, listener);
    }

    public boolean wantBranches() {
        return wantBranches;
    }

    public boolean wantTags() {
        return wantTags;
    }

    public boolean wantPRs() {
        return wantOriginPRs || wantForkPRs;
    }

    public boolean wantOriginPRs() {
        return wantOriginPRs;
    }

    public boolean wantForkPRs() {
        return wantForkPRs;
    }

    public Set<ChangeRequestCheckoutStrategy> originPRStrategies() {
        return originPRStrategies;
    }

    public Set<ChangeRequestCheckoutStrategy> forkPRStrategies() {
        return forkPRStrategies;
    }

    public GitHubSCMSourceContext wantBranches(boolean include) {
        wantBranches = wantBranches || include;
        return this;
    }

    public GitHubSCMSourceContext wantTags(boolean include) {
        wantTags = wantTags || include;
        return this;
    }

    public GitHubSCMSourceContext wantOriginPRs(boolean include) {
        wantOriginPRs = wantOriginPRs || include;
        return this;
    }

    public GitHubSCMSourceContext wantForkPRs(boolean include) {
        wantForkPRs = wantForkPRs || include;
        return this;
    }

    public GitHubSCMSourceContext withOriginPRStrategies(Set<ChangeRequestCheckoutStrategy> strategies) {
        originPRStrategies.addAll(strategies);
        return this;
    }

    public GitHubSCMSourceContext withForkPRStrategies(Set<ChangeRequestCheckoutStrategy> strategies) {
        forkPRStrategies.addAll(strategies);
        return this;
    }
}
