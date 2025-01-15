package org.jenkinsci.plugins.github_branch_source;

import hudson.Extension;
import hudson.ExtensionList;
import jenkins.model.details.DetailGroup;

// TODO - Should be moved to parent plugin
@Extension(ordinal = -1)
public class ScmDetailGroup extends DetailGroup {

    public static ScmDetailGroup get() {
        return ExtensionList.lookupSingleton(ScmDetailGroup.class);
    }
}
