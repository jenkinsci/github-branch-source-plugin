package org.jenkinsci.plugins.github_branch_source;

import hudson.model.Action;
import java.net.URL;
import jenkins.model.Jenkins;
import org.apache.commons.jelly.JellyContext;
import org.jenkins.ui.icon.Icon;
import org.jenkins.ui.icon.IconSet;
import org.jenkins.ui.icon.IconSpec;
import org.kohsuke.stapler.Stapler;

/**
 * Link to GitHub
 *
 * @author Kohsuke Kawaguchi
 */
public class GitHubLink implements Action, IconSpec {
    /**
     * The icon class name to use.
     */
    private final String iconClassName;

    /**
     * Target of the hyperlink to take the user to.
     */
    private final String url;

    public GitHubLink(String iconClassName, String url) {
        this.iconClassName = iconClassName;
        this.url = url;
    }

    public GitHubLink(String iconClassName, URL url) {
        this(iconClassName, url.toExternalForm());
    }

    public String getUrl() {
        return url;
    }

    @Override
    public String getIconClassName() {
        return iconClassName;
    }

    @Override
    public String getIconFileName() {
        if (iconClassName != null) {
            Icon icon = IconSet.icons.getIconByClassSpec(iconClassName + " icon-md");
            if (icon != null) {
                JellyContext ctx = new JellyContext();
                ctx.setVariable("resURL", Stapler.getCurrentRequest().getContextPath() + Jenkins.RESOURCE_PATH);
                return icon.getQualifiedUrl(ctx);
            }
        }
        return null;
    }

    @Override
    public String getDisplayName() {
        return Messages.GitHubLink_DisplayName();
    }

    @Override
    public String getUrlName() {
        return url;
    }
}
