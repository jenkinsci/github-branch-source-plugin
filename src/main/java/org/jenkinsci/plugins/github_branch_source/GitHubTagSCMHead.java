package org.jenkinsci.plugins.github_branch_source;

import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.plugins.git.GitTagSCMHead;
import jenkins.scm.api.mixin.TagSCMHead;

public class GitHubTagSCMHead extends GitTagSCMHead implements TagSCMHead {

    /**
     * Constructor.
     *
     * @param name the name.
     * @param timestamp the tag timestamp;
     */
    public GitHubTagSCMHead(@NonNull String name, long timestamp) {
        super(name, timestamp);
    }

    /** {@inheritDoc} */
    @Override
    public String getPronoun() {
        return Messages.GitHubTagSCMHead_Pronoun();
    }
}
