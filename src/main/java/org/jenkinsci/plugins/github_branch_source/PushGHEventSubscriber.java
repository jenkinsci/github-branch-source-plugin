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
import static org.kohsuke.github.GHEvent.PUSH;

import com.cloudbees.jenkins.GitHubRepositoryName;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.model.Item;
import hudson.scm.SCM;
import java.io.StringReader;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.plugins.git.GitTagSCMRevision;
import jenkins.scm.api.SCMEvent;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.api.trait.SCMHeadPrefilter;
import org.jenkinsci.plugins.github.extension.GHEventsSubscriber;
import org.jenkinsci.plugins.github.extension.GHSubscriberEvent;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

/** This subscriber manages {@link GHEvent} PUSH. */
@Extension
public class PushGHEventSubscriber extends GHEventsSubscriber {

    /** Our logger. */
    private static final Logger LOGGER = Logger.getLogger(PushGHEventSubscriber.class.getName());
    /** Pattern to parse github repository urls. */
    private static final Pattern REPOSITORY_NAME_PATTERN = Pattern.compile("https?://([^/]+)/([^/]+)/([^/]+)");

    /** {@inheritDoc} */
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

    /**
     * {@inheritDoc}
     *
     * @return set with only PULL_REQUEST event
     */
    @Override
    protected Set<GHEvent> events() {
        return immutableEnumSet(PUSH);
    }

    /** {@inheritDoc} */
    @Override
    protected void onEvent(GHSubscriberEvent event) {
        try {
            final GHEventPayload.Push p =
                    GitHub.offline().parseEventPayload(new StringReader(event.getPayload()), GHEventPayload.Push.class);
            String repoUrl = p.getRepository().getHtmlUrl().toExternalForm();
            LOGGER.log(Level.FINE, "Received {0} for {1} from {2}", new Object[] {
                event.getGHEvent(), repoUrl, event.getOrigin()
            });
            Matcher matcher = REPOSITORY_NAME_PATTERN.matcher(repoUrl);
            if (matcher.matches()) {
                final GitHubRepositoryName changedRepository = GitHubRepositoryName.create(repoUrl);
                if (changedRepository == null) {
                    LOGGER.log(Level.WARNING, "Malformed repository URL {0}", repoUrl);
                    return;
                }

                if (p.isCreated()) {
                    fireAfterDelay(new SCMHeadEventImpl(
                            SCMEvent.Type.CREATED, event.getTimestamp(), p, changedRepository, event.getOrigin()));
                } else if (p.isDeleted()) {
                    fireAfterDelay(new SCMHeadEventImpl(
                            SCMEvent.Type.REMOVED, event.getTimestamp(), p, changedRepository, event.getOrigin()));
                } else {
                    fireAfterDelay(new SCMHeadEventImpl(
                            SCMEvent.Type.UPDATED, event.getTimestamp(), p, changedRepository, event.getOrigin()));
                }
            } else {
                LOGGER.log(Level.WARNING, "{0} does not match expected repository name pattern", repoUrl);
            }
        } catch (Error e) {
            throw e;
        } catch (Throwable e) {
            LogRecord lr = new LogRecord(Level.WARNING, "Could not parse {0} event from {1} with payload: {2}");
            lr.setParameters(new Object[] {event.getGHEvent(), event.getOrigin(), event.getPayload()});
            lr.setThrown(e);
            LOGGER.log(lr);
        }
    }

    private void fireAfterDelay(final SCMHeadEventImpl e) {
        SCMHeadEvent.fireLater(e, GitHubSCMSource.getEventDelaySeconds(), TimeUnit.SECONDS);
    }

    private static class SCMHeadEventImpl extends SCMHeadEvent<GHEventPayload.Push> {
        private static final String R_HEADS = "refs/heads/";
        private static final String R_TAGS = "refs/tags/";
        private final String repoHost;
        private final String repoOwner;
        private final String repository;

        public SCMHeadEventImpl(
                Type type, long timestamp, GHEventPayload.Push pullRequest, GitHubRepositoryName repo, String origin) {
            super(type, timestamp, pullRequest, origin);
            this.repoHost = repo.getHost();
            this.repoOwner = pullRequest.getRepository().getOwnerName();
            this.repository = pullRequest.getRepository().getName();
        }

        private boolean isApiMatch(String apiUri) {
            return repoHost.equalsIgnoreCase(RepositoryUriResolver.hostnameFromApiUri(apiUri));
        }

        /** {@inheritDoc} */
        @Override
        public boolean isMatch(@NonNull SCMNavigator navigator) {
            return navigator instanceof GitHubSCMNavigator
                    && repoOwner.equalsIgnoreCase(((GitHubSCMNavigator) navigator).getRepoOwner());
        }

        /** {@inheritDoc} */
        @Override
        public String descriptionFor(@NonNull SCMNavigator navigator) {
            String ref = getPayload().getRef();
            if (ref.startsWith(R_TAGS)) {
                ref = ref.substring(R_TAGS.length());
                return "Push event for tag " + ref + " in repository " + repository;
            }
            if (ref.startsWith(R_HEADS)) {
                ref = ref.substring(R_HEADS.length());
            }
            return "Push event to branch " + ref + " in repository " + repository;
        }

        /** {@inheritDoc} */
        @Override
        public String descriptionFor(SCMSource source) {
            String ref = getPayload().getRef();
            if (ref.startsWith(R_TAGS)) {
                ref = ref.substring(R_TAGS.length());
                return "Push event for tag " + ref;
            }
            if (ref.startsWith(R_HEADS)) {
                ref = ref.substring(R_HEADS.length());
            }
            return "Push event to branch " + ref;
        }

        /** {@inheritDoc} */
        @Override
        public String description() {
            String ref = getPayload().getRef();
            if (ref.startsWith(R_TAGS)) {
                ref = ref.substring(R_TAGS.length());
                return "Push event for tag " + ref + " in repository " + repoOwner + "/" + repository;
            }
            if (ref.startsWith(R_HEADS)) {
                ref = ref.substring(R_HEADS.length());
            }
            return "Push event to branch " + ref + " in repository " + repoOwner + "/" + repository;
        }

        /** {@inheritDoc} */
        @NonNull
        @Override
        public String getSourceName() {
            return repository;
        }

        /** {@inheritDoc} */
        @NonNull
        @Override
        public Map<SCMHead, SCMRevision> heads(@NonNull SCMSource source) {
            if (!(source instanceof GitHubSCMSource
                    && isApiMatch(((GitHubSCMSource) source).getApiUri())
                    && repoOwner.equalsIgnoreCase(((GitHubSCMSource) source).getRepoOwner())
                    && repository.equalsIgnoreCase(((GitHubSCMSource) source).getRepository()))) {
                return Collections.emptyMap();
            }
            GitHubSCMSource src = (GitHubSCMSource) source;
            GHEventPayload.Push push = getPayload();
            GHRepository repo = push.getRepository();
            String repoName = repo.getName();
            if (!repoName.matches(GitHubSCMSource.VALID_GITHUB_REPO_NAME)) {
                // fake repository name
                return Collections.emptyMap();
            }
            String repoOwner = push.getRepository().getOwnerName();
            if (!repoOwner.matches(GitHubSCMSource.VALID_GITHUB_USER_NAME)) {
                // fake owner name
                return Collections.emptyMap();
            }
            if (!push.getHead().matches(GitHubSCMSource.VALID_GIT_SHA1)) {
                // fake head sha1
                return Collections.emptyMap();
            }

            /*
             * What we are looking for is to return the BranchSCMHead for this push
             *
             * Since anything we provide here is untrusted, we don't have to worry about whether this is also a PR...
             * It will be revalidated later when the event is processed
             *
             * In any case, if it is also a PR then there will be a PullRequest:synchronize event that will handle
             * things for us, so we just claim a BranchSCMHead
             */

            GitHubSCMSourceContext context =
                    new GitHubSCMSourceContext(null, SCMHeadObserver.none()).withTraits(src.getTraits());
            String ref = push.getRef();
            if (context.wantBranches() && !ref.startsWith(R_TAGS)) {
                // we only want the branch details if the branch is actually built!
                BranchSCMHead head;
                if (ref.startsWith(R_HEADS)) {
                    // GitHub is consistent in inconsistency, this ref is the full ref... other refs are not!
                    head = new BranchSCMHead(ref.substring(R_HEADS.length()));
                } else {
                    head = new BranchSCMHead(ref);
                }
                boolean excluded = false;
                for (SCMHeadPrefilter prefilter : context.prefilters()) {
                    if (prefilter.isExcluded(source, head)) {
                        excluded = true;
                        break;
                    }
                }
                if (!excluded) {
                    return Collections.singletonMap(
                            head, new AbstractGitSCMSource.SCMRevisionImpl(head, push.getHead()));
                }
            }
            if (context.wantTags() && ref.startsWith(R_TAGS)) {
                // NOTE: GitHub provides the timestamp of the head commit, but if this is an annotated tag
                // then that would be an incorrect timestamp, so we have to assume we are going to have the
                // wrong timestamp for everything except lightweight tags.
                //
                // Now in any case, this actually does not matter.
                //
                // Event consumers are supposed to *not* trust the details reported by an event, it's just a
                // hint.
                // All we really want is that we report enough of a head to provide the head.getName()
                // then the event consumer is supposed to turn around and do a fetch(..., event, ...)
                // and as GitHubSCMSourceRequest strips out the timestamp in calculating the requested
                // tag names, we have a winner.
                //
                // So let's make the assumption that tags are not pushed a long time after their creation
                // and
                // use the event timestamp. This may cause issues if anyone has a pre-filter that filters
                // out tags that are less than X seconds old, but as such a filter would be incompatible
                // with events
                // discovering tags, no harm... the key part is that a pre-filter that removes tags older
                // than X days
                // will not strip the tag *here* (because it will always be only a few seconds "old"), but
                // when
                // the fetch call actually has the real tag date the pre-filter will apply at that point in
                // time.

                GitHubTagSCMHead head = new GitHubTagSCMHead(ref.substring(R_TAGS.length()), getTimestamp());
                boolean excluded = false;
                for (SCMHeadPrefilter prefilter : context.prefilters()) {
                    if (prefilter.isExcluded(source, head)) {
                        excluded = true;
                        break;
                    }
                }
                if (!excluded) {
                    return Collections.singletonMap(head, new GitTagSCMRevision(head, push.getHead()));
                }
            }
            return Collections.emptyMap();
        }

        /** {@inheritDoc} */
        @Override
        public boolean isMatch(@NonNull SCM scm) {
            return false;
        }
    }
}
