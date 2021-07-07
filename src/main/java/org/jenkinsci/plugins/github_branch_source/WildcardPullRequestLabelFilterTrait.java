package org.jenkinsci.plugins.github_branch_source;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.trait.SCMHeadFilter;
import jenkins.scm.api.trait.SCMHeadPrefilter;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceRequest;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import jenkins.scm.impl.trait.Selection;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Decorates a {@link SCMSource} with a {@link SCMHeadPrefilter} that filters {@link SCMHead}
 * instances based on matching wildcard include/exclude rules for any corresponding pull request
 * labels.
 *
 * @since 2.11.0
 */
public class WildcardPullRequestLabelFilterTrait extends SCMSourceTrait {

  /** The include rules. */
  @NonNull private final String includes;

  /** The exclude rules. */
  @NonNull private final String excludes;

  @DataBoundConstructor
  public WildcardPullRequestLabelFilterTrait(@CheckForNull String includes, String excludes) {
    this.includes = StringUtils.defaultIfBlank(includes, "*");
    this.excludes = StringUtils.defaultIfBlank(excludes, "");
  }

  /**
   * Returns the include rules.
   *
   * @return the include rules.
   */
  public String getIncludes() {
    return includes;
  }

  /**
   * Returns the exclude rules.
   *
   * @return the exclude rules.
   */
  public String getExcludes() {
    return excludes;
  }

  protected void decorateContext(SCMSourceContext<?, ?> context) {
    context.withFilter(
        new SCMHeadFilter() {
          public boolean isExcluded(@NonNull SCMSourceRequest request, @NonNull SCMHead head) {
            if (!(request instanceof GitHubSCMSourceRequest)
                || !(head instanceof PullRequestSCMHead)) {
              return false;
            }
            GitHubSCMSourceRequest githubRequest = (GitHubSCMSourceRequest) request;
            PullRequestSCMHead prHead = (PullRequestSCMHead) head;
            for (GHPullRequest pullRequest : githubRequest.getPullRequests()) {
              if (pullRequest.getNumber() != prHead.getNumber()) {
                continue;
              }
              boolean allLabelsIncluded = "*".equals(includes);
              Set<String> prLabels =
                  pullRequest.getLabels().stream()
                      .map(GHLabel::getName)
                      .collect(Collectors.toSet());
              // If no labels only allow the PR when the include is "*" (include everything)
              if (prLabels.isEmpty()) {
                if (!allLabelsIncluded) {
                  request
                      .listener()
                      .getLogger()
                      .format(
                          "%n    Won't Build PR %s. PR has no labels and inclusion does not match '*'.%n",
                          "#" + prHead.getNumber());
                  return true;
                }
                return false;
              }
              // If any labels match the excludes the pr build will be excluded
              if (!excludes.isEmpty()) {
                Predicate<String> exclusionPredicate = getPattern(getExcludes()).asPredicate();
                for (String prLabel : prLabels) {
                  if (exclusionPredicate.test(prLabel)) {
                    request
                        .listener()
                        .getLogger()
                        .format(
                            "%n    Won't Build PR %s. Label %s is marked for exclusion.%n",
                            "#" + prHead.getNumber(), prLabel);
                    return true;
                  }
                }
              }
              // If all labels are included the don't exclude the PR
              if (allLabelsIncluded) {
                return false;
              }
              // Otherwise if any of the labels match the includes the pr build will be included
              if (prLabels.stream().anyMatch(getPattern(getIncludes()).asPredicate())) {
                return false;
              }
              // no labels match the includes therefore the PR will be excluded
              request
                  .listener()
                  .getLogger()
                  .format(
                      "%n    Won't Build PR %s. No labels match inclusion [%s].%n",
                      "#" + prHead.getNumber(), prLabels);
              return true;
            }
            return false;
          }
        });
  }

  /**
   * Returns the pattern corresponding to the labels containing wildcards.
   *
   * @param labels the names of labels to create a pattern for
   * @return pattern corresponding to the labels containing wildcards
   */
  private Pattern getPattern(String labels) {
    StringBuilder quotedLabels = new StringBuilder();
    for (String wildcard : labels.split(" ")) {
      StringBuilder quotedLabel = new StringBuilder();
      for (String label : wildcard.split("(?=[*])|(?<=[*])")) {
        if (label.equals("*")) {
          quotedLabel.append(".*");
        } else if (!label.isEmpty()) {
          quotedLabel.append(Pattern.quote(label));
        }
      }
      if (quotedLabels.length() > 0) {
        quotedLabels.append("|");
      }
      quotedLabels.append(quotedLabel);
    }
    return Pattern.compile(quotedLabels.toString());
  }

  @Symbol({"labelPullRequestFilter"})
  @Extension
  @Selection
  public static class DescriptorImpl extends SCMSourceTraitDescriptor {
    public DescriptorImpl() {}

    public String getDisplayName() {
      return Messages.WildcardPullRequestLabelFilterTrait_DisplayName();
    }
  }
}
