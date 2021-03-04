package org.jenkinsci.plugins.github_branch_source;

import hudson.model.Cause;
import java.util.Objects;
import org.kohsuke.stapler.export.Exported;

public final class GitHubSenderCause extends Cause {
  private final long id;
  private final String login;
  private final String name;
  private final Kind kind;

  GitHubSenderCause(long id, String login, String name, Kind kind) {
    this.id = id;
    this.login = login;
    this.name = name;
    this.kind = kind;
  }

  @Exported(visibility = 3)
  public long getId() {
    return id;
  }

  @Exported(visibility = 3)
  public String getLogin() {
    return login;
  }

  @Exported(visibility = 3)
  public String getName() {
    return name;
  }

  @Exported(visibility = 3)
  public Kind getKind() {
    return kind;
  }

  @Override
  public String getShortDescription() {
    String user;
    if (name == null) {
      user = login;
    } else {
      user = String.format("%s (%s)", new Object[] {name, login});
    }
    return String.format("Caused by GitHub event \"%s\" due to user %s", new Object[] {kind, user});
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    } else if (obj == null || obj.getClass() != this.getClass()) {
      return false;
    }
    GitHubSenderCause cause = (GitHubSenderCause) obj;
    return id == cause.id
        && stringEquals(login, cause.login)
        && stringEquals(name, cause.name)
        && kind == cause.kind;
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, login, name, kind);
  }

  private boolean stringEquals(String a, String b) {
    if (a == null) {
      return b == null;
    }
    return a.equals(b);
  }

  @Override
  public String toString() {
    return String.format(
        "[id=%s] [login=%s] [name=%s] [kind=%s]", new Object[] {id, login, name, kind.name()});
  }

  public enum Kind {
    BRANCH_CREATED("branch created"),
    BRANCH_DELETED("branch deleted"),
    BRANCH_UPDATED("branch updated"),
    PULL_REQUEST_CREATED("pull request created"),
    PULL_REQUEST_UPDATED("pull request updated"),
    PULL_REQUEST_DELETED("pull request deleted"),
    PULL_REQUEST_OTHER("pull request");

    private String description;

    Kind(String description) {
      this.description = description;
    }

    public String toString() {
      return description;
    }
  }
}
