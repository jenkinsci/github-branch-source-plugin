package org.jenkinsci.plugins.github_branch_source;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import hudson.Extension;
import hudson.model.Item;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import jenkins.plugins.git.GitToolChooser;

import java.io.IOException;

public class GitHubRepoSizeEstimator {
    /**
     * This extension intends to perform a GET request without any credentials on the provided repository URL
     * to return the size of repository.
     */
    @Extension
    public static class RepositorySizeGithubAPI extends GitToolChooser.RepositorySizeAPI {

        @Override
        public boolean isApplicableTo(String repoUrl, Item context, String credentialsId) {
            StandardCredentials credentials = Connector.lookupScanCredentials(context, repoUrl, credentialsId);
            try {
                GitHubRepositoryInfo info = GitHubRepositoryInfo.forRepositoryUrl(repoUrl);
                GitHub gitHub = Connector.connect(info.getApiUri(), credentials);
                gitHub.checkApiUrlValidity();
            } catch (Exception e) {
                return false;
            }
            return true;
        }

        @Override
        public Long getSizeOfRepository(String repoUrl, Item context, String credentialsId) throws Exception {
            StandardCredentials credentials = Connector.lookupScanCredentials(context, repoUrl, credentialsId);
            GitHubRepositoryInfo info = GitHubRepositoryInfo.forRepositoryUrl(repoUrl);
            GitHub github = Connector.connect(info.getApiUri(), credentials);
            GHRepository ghRepository = github.getRepository(info.getRepoOwner() + '/' + info.getRepository());
            return (long) ghRepository.getSize();
        }
    }
}
