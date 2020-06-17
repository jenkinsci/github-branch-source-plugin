package org.jenkinsci.plugins.github_branch_source;

import java.util.ArrayList;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import jenkins.scm.api.trait.SCMNavigatorContext;
import jenkins.scm.api.trait.SCMNavigatorTrait;
import jenkins.scm.api.trait.SCMNavigatorTraitDescriptor;
import jenkins.scm.impl.trait.Selection;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;

/**
 * Decorates a {@link SCMNavigatorContext} with GitHub topics
 *
 */
public class TopicsTrait extends SCMNavigatorTrait {

    /**
     * The topics
     */
    @NonNull
    private final ArrayList<String> topics;
    private final String topicList;

    /**
     * Stapler constructor.
     *
     * @param topicList a comma-separated list of topics
     */
    @DataBoundConstructor
    public TopicsTrait(@NonNull String topicList) {
        this.topicList = topicList;
        this.topics = new ArrayList<String>();

        for (String topic : topicList.split(",")) {
            this.topics.add(topic.trim());
        }

    }

    /**
     * Returns the topics
     *
     * @return the topics
     */
    @NonNull
    public ArrayList<String> getTopics() {
        return topics;
    }

    @NonNull
    public String getTopicList() {
        return topicList;
    }

    @Override
    protected void decorateContext(final SCMNavigatorContext<?, ?> context) {
        super.decorateContext(context);
        ((GitHubSCMNavigatorContext) context).setTopics(topics);
    }

    /**
     * Topics descriptor.
     */
    @Symbol("gitHubTopicsFilter")
    @Extension
    @Selection
    public static class DescriptorImpl extends SCMNavigatorTraitDescriptor {

        @Override
        public Class<? extends SCMNavigatorContext> getContextClass() {
            return GitHubSCMNavigatorContext.class;
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return Messages.TopicsTrait_displayName();
        }
    }

}
