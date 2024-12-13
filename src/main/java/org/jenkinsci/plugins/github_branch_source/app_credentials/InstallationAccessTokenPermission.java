package org.jenkinsci.plugins.github_branch_source.app_credentials;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.ListBoxModel;
import java.io.Serializable;
import org.kohsuke.github.GHPermissionType;
import org.kohsuke.stapler.DataBoundConstructor;

public class InstallationAccessTokenPermission extends AbstractDescribableImpl<InstallationAccessTokenPermission>
        implements Serializable {
    public String name;
    public GHPermissionType type;

    @DataBoundConstructor
    public InstallationAccessTokenPermission(String name, GHPermissionType type) {
        this.name = name;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public GHPermissionType getType() {
        return type;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<InstallationAccessTokenPermission> {
        public ListBoxModel doFillTypeItems() {
            ListBoxModel model = new ListBoxModel();
            model.add("No access", GHPermissionType.NONE.name());
            model.add("Read-only", GHPermissionType.READ.name());
            model.add("Read and write", GHPermissionType.WRITE.name());
            model.add("Admin", GHPermissionType.ADMIN.name());
            return model;
        }
    }
}
