package org.jenkinsci.plugins.github_branch_source.app_credentials;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Specify a set of repositories that should be accessible.
 */
public class AccessibleRepositories {

    private final String owner;
    private final List<String> repositories;

    /**
     * Constructor.
     *
     * @param owner if null, then the credential may only be used if the app is installed in a single
     *     organization, otherwise only the specified owner can be accessed
     * @param repositories the names of the repositories that should be accessible, or empty list to
     *     access all repositories
     */
    public AccessibleRepositories(@CheckForNull String owner, @NonNull List<String> repositories) {
        this.owner = owner;
        this.repositories = new ArrayList<>(repositories);
    }

    /**
     * Constructor.
     *
     * @param owner if null, then the credential may only be used if the app is installed in a single
     *     organization, otherwise only the specified owner can be accessed
     */
    public AccessibleRepositories(@CheckForNull String owner) {
        this(owner, Collections.emptyList());
    }

    /**
     * Constructor.
     *
     * @param owner if null, then the credential may only be used if the app is installed in a single
     *     organization, otherwise only the specified owner can be accessed
     * @param repository the name of the repository that should be accessible
     */
    public AccessibleRepositories(@CheckForNull String owner, @NonNull String repository) {
        this(owner, Collections.singletonList(repository));
    }

    public @CheckForNull String getOwner() {
        return owner;
    }

    public @NonNull List<String> getRepositories() {
        return Collections.unmodifiableList(repositories);
    }

    @Override
    public int hashCode() {
        return Objects.hash(owner, repositories);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final AccessibleRepositories other = (AccessibleRepositories) obj;
        return Objects.equals(owner, other.owner) && Objects.equals(repositories, other.repositories);
    }

    @Override
    public String toString() {
        return owner + "/" + repositories;
    }
}
