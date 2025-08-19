package org.jenkinsci.plugins.github_branch_source.app_credentials;

import java.util.Collections;
import java.util.Map;
import org.kohsuke.github.GHPermissionType;

public enum DefaultPermissionsStrategy {
    CONTENTS_READ(Collections.singletonMap("contents", GHPermissionType.READ)),
    CONTENTS_WRITE(Collections.singletonMap("contents", GHPermissionType.WRITE)),
    INHERIT_ALL(Collections.emptyMap());
    // TODO: Would it make sense to add a NO_PERMISSIONS mode, which would effectively prevent these
    // credentials from being used in generic contexts?

    private final Map<String, GHPermissionType> permissions;

    private DefaultPermissionsStrategy(Map<String, GHPermissionType> permissions) {
        this.permissions = permissions;
    }

    public Map<String, GHPermissionType> getPermissions() {
        return permissions;
    }

    public String getDisplayName() {
        switch (this) {
            case CONTENTS_READ:
                return Messages.DefaultPermissionsStrategy_contentsRead();
            case CONTENTS_WRITE:
                return Messages.DefaultPermissionsStrategy_contentsWrite();
            case INHERIT_ALL:
                return Messages.DefaultPermissionsStrategy_inheritAll();
            default:
                throw new AssertionError("Unsupported enum variant " + this);
        }
    }
}
