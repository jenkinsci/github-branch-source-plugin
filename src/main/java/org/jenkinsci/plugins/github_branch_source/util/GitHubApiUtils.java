package org.jenkinsci.plugins.github_branch_source.util;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHPullRequest;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

@Restricted(NoExternalUse.class)
public class GitHubApiUtils {

    private static final Logger LOGGER = Logger.getLogger(GitHubApiUtils.class.getName());

    private GitHubApiUtils() {}

    public static Set<String> getLabels(@NonNull GHPullRequest pr) {
        final Collection<GHLabel> labels;
        try {
            labels = pr.getLabels();
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Cannot retrieve labels from " + pr, ex);
            return Collections.emptySet();
        }

        if (labels == null || labels.isEmpty()) {
            return Collections.emptySet();
        }

        HashSet<String> res = new HashSet<>(labels.size());
        for (GHLabel label : labels) {
            res.add(label.getName());
        }
        return res;
    }
}
