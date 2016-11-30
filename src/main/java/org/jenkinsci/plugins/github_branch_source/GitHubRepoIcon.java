package org.jenkinsci.plugins.github_branch_source;

import com.cloudbees.hudson.plugins.folder.FolderIcon;
import com.cloudbees.hudson.plugins.folder.FolderIconDescriptor;
import hudson.Extension;
import hudson.model.Hudson;
import org.jenkins.ui.icon.Icon;
import org.jenkins.ui.icon.IconSet;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.Stapler;

/**
 * {@link FolderIcon} that shows the github repository icon.
 *
 * @author Kohsuke Kawaguchi
 */
public class GitHubRepoIcon extends FolderIcon {
    @DataBoundConstructor
    public GitHubRepoIcon() {
    }

    @Override
    public String getIconClassName() {
        return "icon-github-repo";
    }

    @Override
    public String getImageOf(String size) {
        return iconClassNameImageOf(size);
    }

    @Override
    public String getDescription() {
        return Messages.GitHubRepoIcon_Description();
    }

    @Extension(optional = true)
    public static class DescriptorImpl extends FolderIconDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.GitHubRepoIcon_DisplayName();
        }
    }
}
