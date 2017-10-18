package org.jenkinsci.plugins.github_branch_source;

import hudson.model.Job;
import hudson.model.Run;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMSource;

/**
 * @since TODO
 */
public final class GitHubNotificationContext {
    private final Job<?, ?> job;
    private final Run<?, ?> build;
    private final SCMSource source;
    private final SCMHead head;

    /**
     * @since TODO
     */
    public GitHubNotificationContext(Job<?, ?> job, Run<?, ?> build, SCMSource source, SCMHead head) {
        this.job = job;
        this.build = build;
        this.source = source;
        this.head = head;
    }

    /**
     * @since TODO
     */
    public Job<?, ?> getJob() {
        return job;
    }

    /**
     * @since TODO
     */
    public Run<?, ?> getBuild() {
        return build;
    }

    /**
     * @since TODO
     */
    public SCMSource getSource() {
        return source;
    }

    /**
     * @since TODO
     */
    public SCMHead getHead() {
        return head;
    }

    /**
     * @since TODO
     */
    @Override
    public String toString() {
        return "GitHubNotificationContext{" +
                "job=" + job +
                ", build=" + build +
                ", source=" + source +
                ", head=" + head +
                '}';
    }

    /**
     * @since TODO
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GitHubNotificationContext that = (GitHubNotificationContext) o;

        if (job != null ? !job.equals(that.job) : that.job != null) return false;
        if (build != null ? !build.equals(that.build) : that.build != null) return false;
        if (source != null ? !source.equals(that.source) : that.source != null) return false;
        return head != null ? head.equals(that.head) : that.head == null;
    }

    /**
     * @since TODO
     */
    @Override
    public int hashCode() {
        int result = job != null ? job.hashCode() : 0;
        result = 31 * result + (build != null ? build.hashCode() : 0);
        result = 31 * result + (source != null ? source.hashCode() : 0);
        result = 31 * result + (head != null ? head.hashCode() : 0);
        return result;
    }
}
