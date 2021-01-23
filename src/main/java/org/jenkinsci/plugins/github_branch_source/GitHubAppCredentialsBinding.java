package org.jenkinsci.plugins.github_branch_source;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.credentialsbinding.BindingDescriptor;
import org.jenkinsci.plugins.credentialsbinding.MultiBinding;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author <a href="mailto:naresh.rayapati@gmail.com">Naresh Rayapati</a>
 */
public class GitHubAppCredentialsBinding extends MultiBinding<GitHubAppCredentials> {

    private final static String DEFAULT_GITHUB_APP_ID_VARIABLE_NAME = "GITHUB_APP_ID";
    private final static String DEFAULT_GITHUB_TOKEN_VARIABLE_NAME = "GITHUB_TOKEN";
    private final static String DEFAULT_GITHUB_OWNER_VARIABLE_NAME = "GITHUB_OWNER";

    @NonNull
    private final String appIdVariable;

    @NonNull
    private final String tokenVariable;

    @NonNull
    private final String ownerVariable;

    private final String owner;

    /**
     *
     * @param appIdVariable if {@code null}, {@value DEFAULT_GITHUB_APP_ID_VARIABLE_NAME} will be used.
     * @param tokenVariable if {@code null}, {@value DEFAULT_GITHUB_TOKEN_VARIABLE_NAME} will be used.
     * @param owner if {@code null}, that default value configured at credentials level will be used if any.
     * @param credentialsId identifier which should be referenced when accessing the credentials from a job/pipeline.
     */
    @DataBoundConstructor
    public GitHubAppCredentialsBinding(@Nullable String appIdVariable, @Nullable String tokenVariable, @Nullable String ownerVariable, @Nullable String owner, String credentialsId) {
        super(credentialsId);
        this.appIdVariable = StringUtils.defaultIfBlank(appIdVariable, DEFAULT_GITHUB_APP_ID_VARIABLE_NAME);
        this.tokenVariable = StringUtils.defaultIfBlank(tokenVariable, DEFAULT_GITHUB_TOKEN_VARIABLE_NAME);
        this.ownerVariable = StringUtils.defaultIfBlank(ownerVariable, DEFAULT_GITHUB_OWNER_VARIABLE_NAME);
        this.owner = owner;
    }

    @NonNull
    public String getAppIdVariable() {
        return appIdVariable;
    }

    @NonNull
    public String getTokenVariable() {
        return tokenVariable;
    }

    @NonNull
    public String getOwnerVariable() {
        return ownerVariable;
    }

    public String getOwner() {
        return owner;
    }

    @Override
    protected Class<GitHubAppCredentials> type() {
        return GitHubAppCredentials.class;
    }

    @Override
    public MultiEnvironment bind(@Nonnull Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException {
        GitHubAppCredentials credentials = getCredentials(build);
        Map<String,String> m = new HashMap<String,String>();
        m.put(appIdVariable, credentials.getAppID());
        if(StringUtils.isNotEmpty(owner)) {
            m.put(tokenVariable, credentials.getPassword(owner).getPlainText());
            m.put(ownerVariable, owner);
        } else {
            m.put(tokenVariable, credentials.getPassword().getPlainText());
            m.put(ownerVariable, credentials.getOwner());
        }

        return new MultiEnvironment(m);
    }

    @Override
    public Set<String> variables() {
        return new HashSet<String>(Arrays.asList(appIdVariable, tokenVariable, ownerVariable));
    }

    @Symbol("gitHubApp")
    @Extension
    public static class DescriptorImpl extends BindingDescriptor<GitHubAppCredentials> {

        @Override protected Class<GitHubAppCredentials> type() {
            return GitHubAppCredentials.class;
        }

        @Override public String getDisplayName() {
            return "GitHub App Credentials";
        }

        @Override public boolean requiresWorkspace() {
            return false;
        }
    }
}
