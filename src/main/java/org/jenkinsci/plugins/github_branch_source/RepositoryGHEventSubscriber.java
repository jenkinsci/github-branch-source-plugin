package org.jenkinsci.plugins.github_branch_source;

import com.cloudbees.jenkins.GitHubRepositoryName;
import hudson.Extension;
import hudson.model.Job;
import hudson.security.ACL;
import jenkins.branch.OrganizationFolder;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.api.SCMSourceOwners;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.github.extension.GHEventsSubscriber;
import org.kohsuke.github.GHEvent;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.collect.Sets.immutableEnumSet;
import static org.kohsuke.github.GHEvent.REPOSITORY;

/**
 * This subscriber manages {@link org.kohsuke.github.GHEvent} REPOSITORY.
 */
@Extension
public class RepositoryGHEventSubscriber extends GHEventsSubscriber {

    private static final Logger LOGGER = Logger.getLogger(RepositoryGHEventSubscriber.class.getName());
    private static final Pattern REPOSITORY_NAME_PATTERN = Pattern.compile("https?://([^/]+)/([^/]+)/([^/]+)");


    @Override
    protected boolean isApplicable(@Nullable Job<?, ?> project) {
        return true;
    }

    /**
     * @return set with only REPOSITORY event
     */
    @Override
    protected Set<GHEvent> events() {
        return immutableEnumSet(REPOSITORY);
    }

    /**
     * @param event only REPOSITORY event
     * @param payload payload of gh-event. Never blank
     */
    @Override
    protected void onEvent(GHEvent event, String payload) {
        JSONObject json = JSONObject.fromObject(payload);
        String repoUrl = json.getJSONObject("repository").getString("html_url");

        LOGGER.log(Level.FINE, "Received POST for {0}", repoUrl);
        Matcher matcher = REPOSITORY_NAME_PATTERN.matcher(repoUrl);
        if (matcher.matches()) {
            final GitHubRepositoryName changedRepository = GitHubRepositoryName.create(repoUrl);
            if (changedRepository == null) {
                LOGGER.log(Level.WARNING, "Malformed repository URL {0}", repoUrl);
                return;
            }
            ACL.impersonate(ACL.SYSTEM, new Runnable() {
                @Override public void run() {
                    for (final SCMSourceOwner owner : SCMSourceOwners.all()) {
                        if (owner instanceof OrganizationFolder) {
                            OrganizationFolder orgFolder = (OrganizationFolder) owner;
                            for (GitHubSCMNavigator navigator : orgFolder.getNavigators().getAll(GitHubSCMNavigator.class)) {
                                if (Pattern.matches(navigator.getPattern(), changedRepository.getRepositoryName())) {
                                    orgFolder.scheduleBuild();
                                }
                            }
                        }
                    }
                }
            });
        } else {
            LOGGER.log(Level.WARNING, "Malformed repository URL {0}", repoUrl);
        }
    }
}
