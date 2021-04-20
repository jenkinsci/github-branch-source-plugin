package org.jenkinsci.plugins.github_branch_source;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;

import java.io.IOException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import hudson.model.Item;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.trait.SCMHeadPrefilter;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import jenkins.scm.impl.trait.Selection;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
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
            if (!(request instanceof GitHubSCMSource) || !(head instanceof PullRequestSCMHead)) {
              return false;
            }
            try {
              GitHubSCMSource githubRequest = (GitHubSCMSource) request;
              String apiUri = githubRequest.getApiUri();
              StandardCredentials credentials =
                      Connector.lookupScanCredentials((Item) request.getOwner(), apiUri, githubRequest.getCredentialsId());
              // Github client and validation
              GitHub github = Connector.connect(apiUri, credentials);
              GHRepository repository = github.getRepository(githubRequest.getRepoOwner() + '/' + githubRequest.getRepository());
              if (repository == null) {
                return false;
              }
              GHPullRequest pullRequest = repository.getPullRequest(((PullRequestSCMHead) head).getNumber());
              if (pullRequest == null) {
                return false;
              }
              Set<String> prLabels = pullRequest.getLabels().stream().map(GHLabel::getName).collect(Collectors.toSet());
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
            } catch (IOException ex) {
              // Couldn't identify labels no filtering
              return false;
            }
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
