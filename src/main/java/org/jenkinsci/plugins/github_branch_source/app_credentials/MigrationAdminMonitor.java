package org.jenkinsci.plugins.github_branch_source.app_credentials;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.AdministrativeMonitor;
import java.util.HashSet;
import java.util.Set;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Extension
@Restricted(NoExternalUse.class)
public class MigrationAdminMonitor extends AdministrativeMonitor {

    private final Set<String> migratedCredentialIds = new HashSet<>();

    @Override
    public boolean isActivated() {
        return !migratedCredentialIds.isEmpty();
    }

    @Override
    public String getDisplayName() {
        return Messages.MigrationAdminMonitor_displayName();
    }

    public Set<String> getMigratedCredentialIds() {
        return migratedCredentialIds;
    }

    public static void addMigratedCredentialId(String id) {
        ExtensionList.lookupSingleton(MigrationAdminMonitor.class)
                .migratedCredentialIds
                .add(id);
    }
}
