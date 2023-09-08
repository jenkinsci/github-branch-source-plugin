/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc., Steven Foster
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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.TaskListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import jenkins.scm.api.trait.SCMSourceContext;

/**
 * The {@link SCMSourceContext} for GitHub.
 *
 * @since 2.2.0
 */
public class GitHubSCMSourceContext extends SCMSourceContext<GitHubSCMSourceContext, GitHubSCMSourceRequest> {
    /** {@code true} if the {@link GitHubSCMSourceRequest} will need information about branches. */
    private boolean wantBranches;
    /** {@code true} if the {@link GitHubSCMSourceRequest} will need information about tags. */
    private boolean wantTags;
    /**
     * {@code true} if the {@link GitHubSCMSourceRequest} will need information about origin pull
     * requests.
     */
    private boolean wantOriginPRs;
    /**
     * {@code true} if the {@link GitHubSCMSourceRequest} will need information about fork pull
     * requests.
     */
    private boolean wantForkPRs;
    /** Set of {@link ChangeRequestCheckoutStrategy} to create for each origin pull request. */
    @NonNull
    private Set<ChangeRequestCheckoutStrategy> originPRStrategies = EnumSet.noneOf(ChangeRequestCheckoutStrategy.class);
    /** Set of {@link ChangeRequestCheckoutStrategy} to create for each fork pull request. */
    @NonNull
    private Set<ChangeRequestCheckoutStrategy> forkPRStrategies = EnumSet.noneOf(ChangeRequestCheckoutStrategy.class);
    /** {@code true} if notifications should be disabled in this context. */
    private boolean notificationsDisabled;
    /**
     * Strategies used to notify Github of build status.
     *
     * @since 2.3.2
     */
    private final List<AbstractGitHubNotificationStrategy> notificationStrategies = new ArrayList<>();

    /**
     * Constructor.
     *
     * @param criteria (optional) criteria.
     * @param observer the {@link SCMHeadObserver}.
     */
    public GitHubSCMSourceContext(@CheckForNull SCMSourceCriteria criteria, @NonNull SCMHeadObserver observer) {
        super(criteria, observer);
    }

    /**
     * Returns {@code true} if the {@link GitHubSCMSourceRequest} will need information about
     * branches.
     *
     * @return {@code true} if the {@link GitHubSCMSourceRequest} will need information about
     *     branches.
     */
    public final boolean wantBranches() {
        return wantBranches;
    }

    /**
     * Returns {@code true} if the {@link GitHubSCMSourceRequest} will need information about tags.
     *
     * @return {@code true} if the {@link GitHubSCMSourceRequest} will need information about tags.
     */
    public final boolean wantTags() {
        return wantTags;
    }

    /**
     * Returns {@code true} if the {@link GitHubSCMSourceRequest} will need information about pull
     * requests.
     *
     * @return {@code true} if the {@link GitHubSCMSourceRequest} will need information about pull
     *     requests.
     */
    public final boolean wantPRs() {
        return wantOriginPRs || wantForkPRs;
    }

    /**
     * Returns {@code true} if the {@link GitHubSCMSourceRequest} will need information about origin
     * pull requests.
     *
     * @return {@code true} if the {@link GitHubSCMSourceRequest} will need information about origin
     *     pull requests.
     */
    public final boolean wantOriginPRs() {
        return wantOriginPRs;
    }

    /**
     * Returns {@code true} if the {@link GitHubSCMSourceRequest} will need information about fork
     * pull requests.
     *
     * @return {@code true} if the {@link GitHubSCMSourceRequest} will need information about fork
     *     pull requests.
     */
    public final boolean wantForkPRs() {
        return wantForkPRs;
    }

    /**
     * Returns the set of {@link ChangeRequestCheckoutStrategy} to create for each origin pull
     * request.
     *
     * @return the set of {@link ChangeRequestCheckoutStrategy} to create for each origin pull
     *     request.
     */
    @NonNull
    public final Set<ChangeRequestCheckoutStrategy> originPRStrategies() {
        return originPRStrategies;
    }

    /**
     * Returns the set of {@link ChangeRequestCheckoutStrategy} to create for each fork pull request.
     *
     * @return the set of {@link ChangeRequestCheckoutStrategy} to create for each fork pull request.
     */
    @NonNull
    public final Set<ChangeRequestCheckoutStrategy> forkPRStrategies() {
        return forkPRStrategies;
    }
    /**
     * Returns the strategies used to notify Github of build status.
     *
     * @return the strategies used to notify Github of build status.
     * @since 2.3.2
     */
    public final List<AbstractGitHubNotificationStrategy> notificationStrategies() {
        if (notificationStrategies.isEmpty()) {
            return Collections.singletonList(new DefaultGitHubNotificationStrategy());
        }
        return Collections.unmodifiableList(notificationStrategies);
    }
    /**
     * Returns {@code true} if notifications should be disabled.
     *
     * @return {@code true} if notifications should be disabled.
     */
    public final boolean notificationsDisabled() {
        return notificationsDisabled;
    }

    /**
     * Adds a requirement for branch details to any {@link GitHubSCMSourceRequest} for this context.
     *
     * @param include {@code true} to add the requirement or {@code false} to leave the requirement as
     *     is (makes simpler with method chaining)
     * @return {@code this} for method chaining.
     */
    @NonNull
    public GitHubSCMSourceContext wantBranches(boolean include) {
        wantBranches = wantBranches || include;
        return this;
    }

    /**
     * Adds a requirement for tag details to any {@link GitHubSCMSourceRequest} for this context.
     *
     * @param include {@code true} to add the requirement or {@code false} to leave the requirement as
     *     is (makes simpler with method chaining)
     * @return {@code this} for method chaining.
     */
    @NonNull
    public GitHubSCMSourceContext wantTags(boolean include) {
        wantTags = wantTags || include;
        return this;
    }

    /**
     * Adds a requirement for origin pull request details to any {@link GitHubSCMSourceRequest} for
     * this context.
     *
     * @param include {@code true} to add the requirement or {@code false} to leave the requirement as
     *     is (makes simpler with method chaining)
     * @return {@code this} for method chaining.
     */
    @NonNull
    public GitHubSCMSourceContext wantOriginPRs(boolean include) {
        wantOriginPRs = wantOriginPRs || include;
        return this;
    }

    /**
     * Adds a requirement for fork pull request details to any {@link GitHubSCMSourceRequest} for this
     * context.
     *
     * @param include {@code true} to add the requirement or {@code false} to leave the requirement as
     *     is (makes simpler with method chaining)
     * @return {@code this} for method chaining.
     */
    @NonNull
    public GitHubSCMSourceContext wantForkPRs(boolean include) {
        wantForkPRs = wantForkPRs || include;
        return this;
    }

    /**
     * Defines the {@link ChangeRequestCheckoutStrategy} instances to create for each origin pull
     * request.
     *
     * @param strategies the strategies.
     * @return {@code this} for method chaining.
     */
    @NonNull
    public GitHubSCMSourceContext withOriginPRStrategies(Set<ChangeRequestCheckoutStrategy> strategies) {
        originPRStrategies.addAll(strategies);
        return this;
    }

    /**
     * Defines the {@link ChangeRequestCheckoutStrategy} instances to create for each fork pull
     * request.
     *
     * @param strategies the strategies.
     * @return {@code this} for method chaining.
     */
    @NonNull
    public GitHubSCMSourceContext withForkPRStrategies(Set<ChangeRequestCheckoutStrategy> strategies) {
        forkPRStrategies.addAll(strategies);
        return this;
    }
    /**
     * Replaces the list of strategies used to notify Github of build status.
     *
     * @param strategies the strategies used to notify Github of build status.
     * @return {@code this} for method chaining.
     * @since 2.3.2
     */
    @NonNull
    public final GitHubSCMSourceContext withNotificationStrategies(
            List<AbstractGitHubNotificationStrategy> strategies) {
        notificationStrategies.clear();
        for (AbstractGitHubNotificationStrategy strategy : strategies) {
            if (!notificationStrategies.contains(strategy)) {
                notificationStrategies.add(strategy);
            }
        }
        return this;
    }

    /**
     * Add a strategy used to notify Github of build status.
     *
     * @param strategy a strategy used to notify Github of build status.
     * @return {@code this} for method chaining.
     * @since 2.3.2
     */
    @NonNull
    public final GitHubSCMSourceContext withNotificationStrategy(AbstractGitHubNotificationStrategy strategy) {
        if (!notificationStrategies.contains(strategy)) {
            notificationStrategies.add(strategy);
        }
        return this;
    }

    /**
     * Defines the notification mode to use in this context.
     *
     * @param disabled {@code true} to disable automatic notifications.
     * @return {@code this} for method chaining.
     */
    @NonNull
    public final GitHubSCMSourceContext withNotificationsDisabled(boolean disabled) {
        notificationsDisabled = disabled;
        return this;
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public GitHubSCMSourceRequest newRequest(@NonNull SCMSource source, @CheckForNull TaskListener listener) {
        return new GitHubSCMSourceRequest(source, this, listener);
    }
}
