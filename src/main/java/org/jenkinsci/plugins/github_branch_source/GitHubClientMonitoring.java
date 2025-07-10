/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jenkinsci.plugins.github_branch_source;

import com.google.common.base.Preconditions;
import hudson.Extension;
import io.jenkins.plugins.opentelemetry.api.OpenTelemetryLifecycleListener;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.semconv.incubating.EnduserIncubatingAttributes;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.PostConstruct;
import jenkins.YesNoMaybe;
import org.kohsuke.github.GHApp;
import org.kohsuke.github.GHRateLimit;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.authorization.AuthorizationProvider;
import org.kohsuke.github.authorization.UserAuthorizationProvider;

@Extension(dynamicLoadable = YesNoMaybe.YES, optional = true)
public class GitHubClientMonitoring implements OpenTelemetryLifecycleListener {

    public static final AttributeKey<Long> GITHUB_APP_ID = AttributeKey.longKey("github.app.id");
    public static final AttributeKey<String> GITHUB_APP_NAME = AttributeKey.stringKey("github.app.name");
    public static final AttributeKey<String> GITHUB_APP_OWNER = AttributeKey.stringKey("github.app.owner");
    public static final AttributeKey<String> GITHUB_AUTHENTICATION = AttributeKey.stringKey("github.authentication");
    public static final AttributeKey<String> GITHUB_API_URL = AttributeKey.stringKey("github.api.url");
    public static final String GITHUB_API_RATE_LIMIT_REMAINING_REQUESTS = "github.api.rate_limit.remaining_requests";

    private static final Logger logger = Logger.getLogger(GitHubClientMonitoring.class.getName());

    private final Field gitHub_clientField;
    private final Class<?> gitHubClientClass;
    private final Field gitHubClient_authorizationProviderField;
    private final Class<?> credentialsTokenProviderClass;
    private final Field credentialsTokenProvider_credentialsField;

    private final Field dependentAuthorizationProvider_gitHubField;

    private final Class<?> authorizationRefreshGitHubWrapperClass;

    private final Map<GitHub, ?> reverseLookup;

    public GitHubClientMonitoring() {
        try {
            Field connector_reverseLookupField = Connector.class.getDeclaredField("reverseLookup");
            connector_reverseLookupField.setAccessible(true);
            Preconditions.checkState(
                    Modifier.isStatic(connector_reverseLookupField.getModifiers()),
                    "Connector#reverseLookup is NOT a static field: %s",
                    connector_reverseLookupField);

            gitHub_clientField = GitHub.class.getDeclaredField("client");
            gitHub_clientField.setAccessible(true);

            gitHubClientClass = Class.forName("org.kohsuke.github.GitHubClient");
            gitHubClient_authorizationProviderField = gitHubClientClass.getDeclaredField("authorizationProvider");
            gitHubClient_authorizationProviderField.setAccessible(true);

            credentialsTokenProviderClass = Class.forName(
                    "org.jenkinsci.plugins.github_branch_source.GitHubAppCredentials$CredentialsTokenProvider");
            credentialsTokenProvider_credentialsField = credentialsTokenProviderClass.getDeclaredField("credentials");
            credentialsTokenProvider_credentialsField.setAccessible(true);
            Preconditions.checkState(
                    GitHubAppCredentials.class.isAssignableFrom(credentialsTokenProvider_credentialsField.getType()),
                    "Unsupported type for credentialsTokenProvider.credentials. Expected GitHubAppCredentials, current %s",
                    credentialsTokenProvider_credentialsField);

            dependentAuthorizationProvider_gitHubField =
                    GitHub.DependentAuthorizationProvider.class.getDeclaredField("gitHub");
            dependentAuthorizationProvider_gitHubField.setAccessible(true);

            authorizationRefreshGitHubWrapperClass =
                    Class.forName("org.kohsuke.github.GitHub$AuthorizationRefreshGitHubWrapper");

            reverseLookup = (Map<GitHub, ?>) connector_reverseLookupField.get(null);
        } catch (IllegalAccessException | NoSuchFieldException | ClassNotFoundException e) {
            throw new RuntimeException("Unsupported version of the Github Branch Source Plugin", e);
        } catch (SecurityException e) {
            throw new RuntimeException(
                    "SecurityManager is activated, cannot monitor the GitHub Client as it requires Java reflection permissions",
                    e);
        }
    }

    @PostConstruct
    public void postConstruct() {
        logger.log(Level.FINE, () -> "Start monitoring Jenkins controller GitHub client...");

        Meter meter = getMeter();
        meter.gaugeBuilder(GITHUB_API_RATE_LIMIT_REMAINING_REQUESTS)
                .ofLongs()
                .setDescription("GitHub Repository API rate limit remaining requests")
                .setUnit("{requests}")
                .buildWithCallback(gauge -> {
                    logger.log(Level.FINE, () -> "Collect GitHub client API rate limit metrics");
                    reverseLookup.keySet().forEach(gitHub -> {
                        GHRateLimit ghRateLimit = gitHub.lastRateLimit();
                        try {
                            AttributesBuilder attributesBuilder =
                                    Attributes.of(GITHUB_API_URL, gitHub.getApiUrl()).toBuilder();
                            final String authentication;
                            if (gitHub.isAnonymous()) {
                                authentication = "anonymous";
                            } else {
                                Object gitHubClient = gitHub_clientField.get(gitHub);
                                Preconditions.checkState(gitHubClientClass.isAssignableFrom(gitHubClient.getClass()));
                                AuthorizationProvider authorizationProvider = (AuthorizationProvider)
                                        gitHubClient_authorizationProviderField.get(gitHubClient);
                                if (authorizationProvider instanceof UserAuthorizationProvider) {
                                    String gitHubLogin = ((UserAuthorizationProvider) authorizationProvider).getLogin();
                                    if (gitHubLogin == null) {
                                        gitHubLogin = gitHub.getMyself().getLogin();
                                    }
                                    attributesBuilder.put(EnduserIncubatingAttributes.ENDUSER_ID, gitHubLogin);
                                    authentication = "login:" + gitHubLogin;
                                } else if (credentialsTokenProviderClass.isAssignableFrom(
                                        authorizationProvider.getClass())) {
                                    GitHub jwtTokenBasedGitHub = (GitHub)
                                            dependentAuthorizationProvider_gitHubField.get(authorizationProvider);
                                    if (authorizationRefreshGitHubWrapperClass.isAssignableFrom(
                                            jwtTokenBasedGitHub.getClass())) {
                                        // The GitHub client lib uses a caching mechanism specified in
                                        // org.jenkinsci.plugins.github_branch_source.Connector.connect()
                                        GHApp gitHubApp = jwtTokenBasedGitHub.getApp();
                                        attributesBuilder.put(GITHUB_APP_NAME, gitHubApp.getName());
                                        attributesBuilder.put(GITHUB_APP_ID, gitHubApp.getId());
                                        attributesBuilder.put(GITHUB_APP_OWNER, gitHubApp.getName());
                                        authentication = "app:id=" + gitHubApp.getId() + ",name=\""
                                                + gitHubApp.getName() + "\",owner=" + gitHubApp.getName();
                                    } else {
                                        GitHubAppCredentials credentials = (GitHubAppCredentials)
                                                credentialsTokenProvider_credentialsField.get(authorizationProvider);
                                        attributesBuilder.put(GITHUB_APP_ID, Long.valueOf(credentials.getAppID()));
                                        authentication = "app:id=" + credentials.getAppID();
                                        logger.log(
                                                Level.INFO,
                                                "Unexpected credentialsTokenProvider with internal GitHub of type "
                                                        + jwtTokenBasedGitHub);
                                    }
                                } else {
                                    authentication = authorizationProvider.getClass() + ":"
                                            + System.identityHashCode(authorizationProvider);
                                }
                            }
                            Attributes attributes = attributesBuilder
                                    .put(GITHUB_AUTHENTICATION, authentication)
                                    .build();
                            logger.log(
                                    Level.FINER,
                                    () -> "Collect GitHub API " + attributes + ": rateLimit.remaining:"
                                            + ghRateLimit.getRemaining());
                            gauge.record(ghRateLimit.getRemaining(), attributes);
                        } catch (IllegalAccessException | IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                });
    }

    private static Meter getMeter() {
        return GlobalOpenTelemetry.getMeter("org.jenkinsci.plugins.github_branch_source");
    }
}
