package org.jenkinsci.plugins.github_branch_source;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import java.util.Set;
import java.util.regex.Pattern;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.trait.SCMHeadPrefilter;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import jenkins.scm.impl.trait.Selection;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
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
    context.withPrefilter(
        new SCMHeadPrefilter() {
          public boolean isExcluded(@NonNull SCMSource request, @NonNull SCMHead head) {
            if (!(head instanceof PullRequestSCMHead)) {
              return false;
            }
            Set<String> prLabels = ((PullRequestSCMHead) head).getLabelNames();
            // If no labels only allow the PR when the include is "*" (include everything)
            if (prLabels.isEmpty()) {
              return !"*".equals(includes);
            }
            // If any labels match the excludes the pr build will be excluded
            if (prLabels.stream().anyMatch(getPattern(getExcludes()).asPredicate())) {
              return true;
            }
            // Otherwise if none of the labels match the includes the pr build will be excluded
            return prLabels.stream().noneMatch(getPattern(getIncludes()).asPredicate());
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
