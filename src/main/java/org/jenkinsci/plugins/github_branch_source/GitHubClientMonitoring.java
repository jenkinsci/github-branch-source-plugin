/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jenkinsci.plugins.github_branch_source;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import hudson.Extension;
import io.jenkins.plugins.opentelemetry.api.OpenTelemetryLifecycleListener;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.semconv.incubating.EnduserIncubatingAttributes;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.PostConstruct;
import jenkins.YesNoMaybe;
import org.kohsuke.github.GHRateLimit;

@Extension(dynamicLoadable = YesNoMaybe.YES, optional = true)
public class GitHubClientMonitoring implements OpenTelemetryLifecycleListener {

    public static final AttributeKey<Long> GITHUB_APP_ID = AttributeKey.longKey("github.app.id");
    public static final AttributeKey<String> GITHUB_APP_NAME = AttributeKey.stringKey("github.app.name");
    public static final AttributeKey<String> GITHUB_APP_OWNER = AttributeKey.stringKey("github.app.owner");
    public static final AttributeKey<String> GITHUB_AUTHENTICATION = AttributeKey.stringKey("github.authentication");
    public static final AttributeKey<String> GITHUB_API_URL = AttributeKey.stringKey("github.api.url");
    public static final String GITHUB_API_RATE_LIMIT_REMAINING_REQUESTS = "github.api.rate_limit.remaining_requests";

    private static final Logger logger = Logger.getLogger(GitHubClientMonitoring.class.getName());

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
                    Connector.getGitHubCredentialsMap().forEach((gitHub, credentials) -> {
                        GHRateLimit ghRateLimit = gitHub.lastRateLimit();
                        try {
                            AttributesBuilder attributesBuilder =
                                    Attributes.of(GITHUB_API_URL, gitHub.getApiUrl()).toBuilder();
                            String authentication =
                                    credentials.getClass().getSimpleName() + ":" + System.identityHashCode(credentials);
                            if (gitHub.isAnonymous()) {
                                authentication = "anonymous";
                            } else if (credentials instanceof GitHubAppCredentials) {
                                GitHubAppCredentials appCreds = (GitHubAppCredentials) credentials;
                                if (gitHub.getApp() != null) {
                                    Long appId = gitHub.getApp().getId();
                                    String appName = gitHub.getApp().getName();
                                    String appOwner = appCreds.getOwner();
                                    if (appName != null) {
                                        attributesBuilder.put(GITHUB_APP_NAME, appName);
                                        if (appId != null && appOwner != null) {
                                            attributesBuilder.put(GITHUB_APP_ID, appId);
                                            attributesBuilder.put(GITHUB_APP_OWNER, appOwner);
                                            authentication = "app:id=" + appId + "app:id=" + appId + ",name=\""
                                                    + appName + "\",owner=" + appOwner;
                                        } else {
                                            authentication = "app:id=" + appId;
                                        }
                                    }
                                }
                            } else if (credentials instanceof StandardUsernamePasswordCredentials) {
                                String gitHubLogin = ((StandardUsernamePasswordCredentials) credentials).getUsername();
                                attributesBuilder.put(EnduserIncubatingAttributes.ENDUSER_ID, gitHubLogin);
                                authentication = "login:" + gitHubLogin;
                            }
                            Attributes attributes = attributesBuilder
                                    .put(GITHUB_AUTHENTICATION, authentication)
                                    .build();
                            logger.log(
                                    Level.FINER,
                                    () -> "Collect GitHub API " + attributes + ": rateLimit.remaining:"
                                            + ghRateLimit.getRemaining());
                            gauge.record(ghRateLimit.getRemaining(), attributes);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                });
    }

    private static Meter getMeter() {
        return GlobalOpenTelemetry.getMeter("org.jenkinsci.plugins.github_branch_source");
    }
}
