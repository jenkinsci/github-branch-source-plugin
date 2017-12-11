package org.jenkinsci.plugins.github_branch_source;

import com.cloudbees.jenkins.GitHubRepositoryName;
import com.cloudbees.jenkins.GitHubRepositoryNameContributor;
import hudson.Extension;
import hudson.model.Item;
import jenkins.branch.MultiBranchProject;

import java.util.Collection;

@Extension
public class GitHubSCMSourceRepositoryNameContributor extends GitHubRepositoryNameContributor {

    @Override
    public void parseAssociatedNames(Item item, Collection<GitHubRepositoryName> result) {
        if (item instanceof MultiBranchProject) {
            MultiBranchProject mp = (MultiBranchProject) item;
            for (Object o : mp.getSCMSources()) {
                if (o instanceof GitHubSCMSource) {
                    GitHubSCMSource gitHubSCMSource = (GitHubSCMSource) o;
                    result.add(new GitHubRepositoryName(
                            RepositoryUriResolver.hostnameFromApiUri(gitHubSCMSource.getApiUri()),
                            gitHubSCMSource.getRepoOwner(),
                            gitHubSCMSource.getRepository()));

                }
            }
        }
    }
}
