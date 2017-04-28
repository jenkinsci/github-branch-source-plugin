package org.jenkinsci.plugins.github_branch_source;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.Queue;
import hudson.model.queue.Tasks;
import hudson.plugins.git.GitSCM;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import jenkins.scm.api.trait.SCMBuilder;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public class SSHCheckoutTrait extends SCMSourceTrait {

    /**
     * Credentials for actual clone; may be SSH private key.
     */
    private final String credentialsId;

    @DataBoundConstructor
    public SSHCheckoutTrait(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    @Override
    protected <B extends SCMBuilder<B, S>, S extends SCM> void decorateBuilder(B builder) {

        super.decorateBuilder(builder);
    }

    @Extension
    public static class DescriptorImpl extends SCMSourceTraitDescriptor {

        @Override
        public String getDisplayName() {
            return "Checkout over SSH";
        }

        @Override
        public boolean isApplicableToContext(Class<? extends SCMSourceContext> contextClass) {
            return GitHubSCMSourceRequest.class.isAssignableFrom(contextClass);
        }

        @Override
        public boolean isApplicableToSCM(SCMDescriptor<?> scm) {
            return scm instanceof GitSCM.DescriptorImpl;
        }

        public ListBoxModel doFillCredentialsIdItems(@CheckForNull @AncestorInPath Item context,
                                                     @QueryParameter String apiUri) {
            StandardListBoxModel result = new StandardListBoxModel();
            result.add("- use build agent's key -", GitHubSCMSource.DescriptorImpl.ANONYMOUS);
            return result.includeMatchingAs(
                    context instanceof Queue.Task
                            ? Tasks.getDefaultAuthenticationOf((Queue.Task) context)
                            : ACL.SYSTEM,
                    context,
                    StandardUsernameCredentials.class,
                    Connector.githubDomainRequirements(apiUri),
                    CredentialsMatchers.instanceOf(SSHUserPrivateKey.class)
            );
        }


    }
}
