package org.jenkinsci.plugins.github_branch_source;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import java.util.Objects;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadCategory;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.trait.SCMBuilder;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

public class GitHubSCMHeadProcessIgnoreErrorTrait extends SCMSourceTrait {
  private boolean ignoreBranch;
  private boolean ignorePR;
  private boolean ignoreTag;

  @DataBoundConstructor
  public GitHubSCMHeadProcessIgnoreErrorTrait(
      boolean ignoreBranch, boolean ignorePR, boolean ignoreTag) {
    this.ignoreBranch = ignoreBranch;
    this.ignorePR = ignorePR;
    this.ignoreTag = ignoreTag;
  }

  public boolean isIgnoreBranch() {
    return ignoreBranch;
  }

  public boolean isIgnorePR() {
    return ignorePR;
  }

  public boolean isIgnoreTag() {
    return ignoreTag;
  }

  @Override
  protected void decorateContext(SCMSourceContext<?, ?> context) {
    GitHubSCMSourceContext githubContext = (GitHubSCMSourceContext) context;
    githubContext.withHeadProcessErrorStrategy(
        new GitHubSCMHeadProcessIgnoreErrorStrategy(ignoreBranch, ignorePR, ignoreTag));
  }

  @Override
  public boolean includeCategory(@NonNull SCMHeadCategory category) {
    return category.isUncategorized();
  }

  @Extension
  public static class DescriptorImpl extends SCMSourceTraitDescriptor {

    @NonNull
    @Override
    public String getDisplayName() {
      return "Ignore Indexing Errors";
    }

    @Override
    public Class<? extends SCMBuilder> getBuilderClass() {
      return GitHubSCMBuilder.class;
    }

    @Override
    public Class<? extends SCMSourceContext> getContextClass() {
      return GitHubSCMSourceContext.class;
    }

    @Override
    public Class<? extends SCMSource> getSourceClass() {
      return GitHubSCMSource.class;
    }
  }

  private static final class GitHubSCMHeadProcessIgnoreErrorStrategy
      extends AbstractGitHubSCMHeadProcessErrorStrategy {

    private boolean ignoreBranch;
    private boolean ignorePR;
    private boolean ignoreTag;

    public GitHubSCMHeadProcessIgnoreErrorStrategy(
        boolean ignoreBranch, boolean ignorePR, boolean ignoreTag) {
      this.ignoreBranch = ignoreBranch;
      this.ignorePR = ignorePR;
      this.ignoreTag = ignoreTag;
    }

    @Override
    public boolean shouldIgnore(SCMHead head, Throwable t) {
      if (head instanceof BranchSCMHead) {
        return ignoreBranch;
      }

      if (head instanceof PullRequestSCMHead) {
        return ignorePR;
      }

      if (head instanceof GitHubTagSCMHead) {
        return ignoreTag;
      }

      return true;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      GitHubSCMHeadProcessIgnoreErrorStrategy that = (GitHubSCMHeadProcessIgnoreErrorStrategy) o;
      return ignoreBranch == that.ignoreBranch
          && ignorePR == that.ignorePR
          && ignoreTag == that.ignoreTag;
    }

    @Override
    public int hashCode() {
      return Objects.hash(ignoreBranch, ignorePR, ignoreTag);
    }
  }
}
