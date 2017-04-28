package org.jenkinsci.plugins.github_branch_source;

import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.SCMSourceObserver;
import jenkins.scm.api.trait.SCMNavigatorContext;

public class GitHubSCMNavigatorContext extends SCMNavigatorContext<GitHubSCMNavigatorContext, GitHubSCMNavigatorRequest> {
    @Override
    public GitHubSCMNavigatorRequest newRequest(@NonNull SCMNavigator navigator, @NonNull SCMSourceObserver observer) {
        return new GitHubSCMNavigatorRequest(navigator, this, observer);
    }
}
