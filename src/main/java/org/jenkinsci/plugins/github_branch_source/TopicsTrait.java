package org.jenkinsci.plugins.github_branch_source;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import java.util.ArrayList;
import java.util.List;
import jenkins.scm.api.trait.SCMNavigatorContext;
import jenkins.scm.api.trait.SCMNavigatorTrait;
import jenkins.scm.api.trait.SCMNavigatorTraitDescriptor;
import jenkins.scm.impl.trait.Selection;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

/** Decorates a {@link SCMNavigatorContext} with GitHub topics */
public class TopicsTrait extends SCMNavigatorTrait {

    /** The topics */
    @NonNull
    private transient List<String> topics;

    private final String topicList;

    /**
     * Stapler constructor.
     *
     * @param topicList a comma-separated list of topics
     */
    @DataBoundConstructor
    public TopicsTrait(@NonNull String topicList) {
        this.topicList = topicList;
        this.topics = new ArrayList<>();

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
    public List<String> getTopics() {
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

    private Object readResolve() {
        if (this.topicList != null) {
            List<String> tmpTopics = new ArrayList<>();
            for (String topic : topicList.split(",")) {
                tmpTopics.add(topic.trim());
            }
            topics = tmpTopics;
        }

        return this;
    }

    /** Topics descriptor. */
    @Symbol("gitHubTopicsFilter")
    @Extension
    @Selection
    public static class DescriptorImpl extends SCMNavigatorTraitDescriptor {

        @Override
        public Class<? extends SCMNavigatorContext> getContextClass() {
            return GitHubSCMNavigatorContext.class;
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.TopicsTrait_displayName();
        }
    }
}
