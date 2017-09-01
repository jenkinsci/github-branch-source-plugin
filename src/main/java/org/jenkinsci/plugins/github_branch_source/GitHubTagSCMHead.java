package org.jenkinsci.plugins.github_branch_source;

import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.mixin.TagSCMHead;

public class GitHubTagSCMHead extends SCMHead implements TagSCMHead {

    private final long timestamp;

    /**
     * Constructor.
     *
     * @param name      the name.
     * @param timestamp the tag timestamp;
     */
    public GitHubTagSCMHead(@NonNull String name, long timestamp) {
        super(name);
        this.timestamp = timestamp;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getPronoun() {
        return Messages.GitHubTagSCMHead_Pronoun();
    }

}
