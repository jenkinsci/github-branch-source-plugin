package org.jenkinsci.plugins.github_branch_source;

import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.SCMSourceObserver;
import jenkins.scm.api.trait.SCMNavigatorContext;
import jenkins.scm.api.trait.SCMNavigatorRequest;

public class GitHubSCMNavigatorRequest extends SCMNavigatorRequest {
    public GitHubSCMNavigatorRequest(@NonNull SCMNavigator source,
                                        @NonNull SCMNavigatorContext<?, ?> context,
                                        @NonNull SCMSourceObserver observer) {
        super(source, context, observer);
    }
}
