package org.jenkinsci.plugins.github_branch_source;

import com.cloudbees.jenkins.GitHubRepositoryName;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Item;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceEvent;
import jenkins.scm.api.SCMSourceOwner;
import org.jenkinsci.plugins.github.extension.GHEventsSubscriber;
import org.jenkinsci.plugins.github.extension.GHSubscriberEvent;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GitHub;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.StringReader;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static com.google.common.collect.Sets.immutableEnumSet;

/**
 * This subscriber manages {@link org.kohsuke.github.GHEvent} DEPLOYMENT.
 */
@Extension
public class DeploymentGHEventSubscriber extends GHEventsSubscriber {
    private static final Logger LOGGER = Logger.getLogger(DeploymentGHEventSubscriber.class.getName());

    @Override
    protected boolean isApplicable(@Nullable Item project) {
        if (project != null) {
            if (project instanceof SCMSourceOwner) {
                SCMSourceOwner owner = (SCMSourceOwner) project;
                for (SCMSource source : owner.getSCMSources()) {
                    if (source instanceof GitHubSCMSource) {
                        return true;
                    }
                }
            }
            if (project.getParent() instanceof SCMSourceOwner) {
                SCMSourceOwner owner = (SCMSourceOwner) project.getParent();
                for (SCMSource source : owner.getSCMSources()) {
                    if (source instanceof GitHubSCMSource) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    protected Set<GHEvent> events() {
        return immutableEnumSet(GHEvent.DEPLOYMENT);
    }

    @Override
    protected void onEvent(GHSubscriberEvent event) {
        try {
            final GHEventPayload.Deployment d = GitHub.offline()
                    .parseEventPayload(new StringReader(event.getPayload()), GHEventPayload.Deployment.class);
            String repoUrl = d.getRepository().getHtmlUrl().toExternalForm();
            LOGGER.log(Level.INFO, "Received {0} for {1} from {2}",
                    new Object[]{event.getGHEvent(), repoUrl, event.getOrigin()}
            );

            final GitHubRepositoryName repo = GitHubRepositoryName.create(repoUrl);
            if (repo == null) {
                LOGGER.log(Level.WARNING, "Malformed repository URL {0}", repoUrl);
                return;
            }

            final SCMDeploymentEvent e = new SCMDeploymentEvent(event.getType(), event.getTimestamp(), d, repo, event.getOrigin());
            SCMSourceEvent.fireLater(e, GitHubSCMSource.getEventDelaySeconds(), TimeUnit.SECONDS);
        } catch (IOException e) {
            LogRecord lr = new LogRecord(Level.WARNING, "Could not parse {0} event from {1} with payload: {2}");
            lr.setParameters(new Object[]{event.getGHEvent(), event.getOrigin(), event.getPayload()});
            lr.setThrown(e);
            LOGGER.log(lr);
        }
    }

    private static class SCMDeploymentEvent extends SCMSourceEvent<GHEventPayload.Deployment> {
        private final String repoHost;
        private final String repoOwner;
        private final String repository;

        public SCMDeploymentEvent(Type type, long timestamp, GHEventPayload.Deployment deployment, GitHubRepositoryName repo,
                                  String origin) {
            super(type, timestamp, deployment, origin);
            this.repoHost = repo.getHost();
            this.repoOwner = deployment.getRepository().getOwnerName();
            this.repository = deployment.getRepository().getName();
        }

        @Override
        public boolean isMatch(@NonNull SCMNavigator navigator) {
            return navigator instanceof GitHubSCMNavigator
                    && repoOwner.equalsIgnoreCase(((GitHubSCMNavigator) navigator).getRepoOwner());
        }

        @Override
        public boolean isMatch(@NonNull SCMSource source) {
            return source instanceof GitHubSCMSource
                    && repoOwner.equalsIgnoreCase(((GitHubSCMSource) source).getRepoOwner())
                    && repository.equalsIgnoreCase(((GitHubSCMSource) source).getRepository());
        }

        @NonNull
        @Override
        public String getSourceName() {
            return repository;
        }
    }

}
