/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkinsci.plugins.github_branch_source;

import static com.google.common.collect.Sets.immutableEnumSet;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.WARNING;
import static org.kohsuke.github.GHEvent.REPOSITORY;

import com.cloudbees.jenkins.GitHubRepositoryName;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.model.Item;
import java.io.IOException;
import java.io.StringReader;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.SCMNavigatorOwner;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceEvent;
import org.jenkinsci.plugins.github.extension.GHEventsSubscriber;
import org.jenkinsci.plugins.github.extension.GHSubscriberEvent;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GitHub;

/** This subscriber manages {@link org.kohsuke.github.GHEvent} REPOSITORY. */
@Extension
public class GitHubRepositoryEventSubscriber extends GHEventsSubscriber {

    private static final Logger LOGGER = Logger.getLogger(GitHubRepositoryEventSubscriber.class.getName());
    private static final Pattern REPOSITORY_NAME_PATTERN = Pattern.compile("https?://([^/]+)/([^/]+)/([^/]+)");

    @Override
    protected boolean isApplicable(@Nullable Item item) {
        if (item instanceof SCMNavigatorOwner) {
            for (SCMNavigator navigator : ((SCMNavigatorOwner) item).getSCMNavigators()) {
                if (navigator instanceof GitHubSCMNavigator) {
                    return true; // TODO allow navigators to opt-out
                }
            }
        }
        return false;
    }

    /** @return set with only REPOSITORY event */
    @Override
    protected Set<GHEvent> events() {
        return immutableEnumSet(REPOSITORY);
    }

    @Override
    protected void onEvent(GHSubscriberEvent event) {
        try {
            final GHEventPayload.Repository p = GitHub.offline()
                    .parseEventPayload(new StringReader(event.getPayload()), GHEventPayload.Repository.class);
            String action = p.getAction();
            String repoUrl = p.getRepository().getHtmlUrl().toExternalForm();
            LOGGER.log(Level.FINE, "Received {0} for {1} from {2}", new Object[] {
                event.getGHEvent(), repoUrl, event.getOrigin()
            });
            boolean fork = p.getRepository().isFork();
            Matcher matcher = REPOSITORY_NAME_PATTERN.matcher(repoUrl);
            if (matcher.matches()) {
                final GitHubRepositoryName repo = GitHubRepositoryName.create(repoUrl);
                if (repo == null) {
                    LOGGER.log(WARNING, "Malformed repository URL {0}", repoUrl);
                    return;
                }
                if (!"created".equals(action)) {
                    LOGGER.log(FINE, "Repository {0} was {1} not created, will be ignored", new Object[] {
                        repo.getRepositoryName(), action
                    });
                    return;
                }
                if (!fork) {
                    LOGGER.log(
                            FINE,
                            "Repository {0} was created but it is empty, will be ignored",
                            repo.getRepositoryName());
                    return;
                }
                final NewSCMSourceEvent e = new NewSCMSourceEvent(event.getTimestamp(), event.getOrigin(), p, repo);
                // Delaying the indexing for some seconds to avoid GitHub cache
                SCMSourceEvent.fireLater(e, GitHubSCMSource.getEventDelaySeconds(), TimeUnit.SECONDS);
            } else {
                LOGGER.log(WARNING, "Malformed repository URL {0}", repoUrl);
            }
        } catch (IOException e) {
            LogRecord lr = new LogRecord(Level.WARNING, "Could not parse {0} event from {1} with payload: {2}");
            lr.setParameters(new Object[] {event.getGHEvent(), event.getOrigin(), event.getPayload()});
            lr.setThrown(e);
            LOGGER.log(lr);
        }
    }

    private static class NewSCMSourceEvent extends SCMSourceEvent<GHEventPayload.Repository> {
        private final String repoHost;
        private final String repoOwner;
        private final String repository;

        public NewSCMSourceEvent(
                long timestamp, String origin, GHEventPayload.Repository event, GitHubRepositoryName repo) {
            super(Type.CREATED, timestamp, event, origin);
            this.repoHost = repo.getHost();
            this.repoOwner = event.getRepository().getOwnerName();
            this.repository = event.getRepository().getName();
        }

        private boolean isApiMatch(String apiUri) {
            return repoHost.equalsIgnoreCase(RepositoryUriResolver.hostnameFromApiUri(apiUri));
        }

        @Override
        public boolean isMatch(@NonNull SCMNavigator navigator) {
            return navigator instanceof GitHubSCMNavigator
                    && isApiMatch(((GitHubSCMNavigator) navigator).getApiUri())
                    && repoOwner.equalsIgnoreCase(((GitHubSCMNavigator) navigator).getRepoOwner());
        }

        @Override
        public boolean isMatch(@NonNull SCMSource source) {
            return source instanceof GitHubSCMSource
                    && isApiMatch(((GitHubSCMSource) source).getApiUri())
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
