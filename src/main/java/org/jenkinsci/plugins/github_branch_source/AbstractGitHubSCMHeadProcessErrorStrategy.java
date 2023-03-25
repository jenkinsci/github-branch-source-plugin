package org.jenkinsci.plugins.github_branch_source;

import jenkins.scm.api.SCMHead;

public abstract class AbstractGitHubSCMHeadProcessErrorStrategy {
  public abstract boolean shouldIgnore(SCMHead head, Throwable t);

  /** {@inheritDoc} */
  public abstract boolean equals(Object o);

  /** {@inheritDoc} */
  public abstract int hashCode();
}
