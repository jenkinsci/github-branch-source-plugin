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


/** Decorates a {@link SCMNavigatorContext} with GitHub custom properties */
public class CustomPropertiesTrait extends SCMNavigatorTrait {

    /** The custom properties */
    @NonNull
    private transient List<String> customProperties;

    private final String customPropertyList;

    /**
     * Stapler constructor.
     *
     * @param customPropertyList a comma-separated list of custom properties
     */
    @DataBoundConstructor
    public CustomPropertiesTrait(@NonNull String customPropertyList) {
        this.customPropertyList = customPropertyList;
        this.customProperties = new ArrayList<>();

        for (String customProperty : customPropertyList.split(",")) {
            this.customProperties.add(customProperty.trim());
        }
    }

    /**
     * Returns the custom properties
     *
     * @return the custom properties
     */
    @NonNull
    public List<String> getCustomProperties() {
        return customProperties;
    }

    @NonNull
    public String getCustomPropertyList() {
        return customPropertyList;
    }

    @Override
    protected void decorateContext(final SCMNavigatorContext<?, ?> context) {
        super.decorateContext(context);
        ((GitHubSCMNavigatorContext) context).setCustomProperties(customProperties);
    }

    private Object readResolve() {
        if (this.customPropertyList != null) {
            List<String> tmpCustomProperties = new ArrayList<>();
            for (String customProperty : customPropertyList.split(",")) {
                tmpCustomProperties.add(customProperty.trim());
            }
            customProperties = tmpCustomProperties;
        }

        return this;
    }

    /** Custom properties descriptor. */
    @Symbol("gitHubCustomPropertiesFilter")
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
            return Messages.CustomPropertiesTrait_displayName();
        }
    }
}
