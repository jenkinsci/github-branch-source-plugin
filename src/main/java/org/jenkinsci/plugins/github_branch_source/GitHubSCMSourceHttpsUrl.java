/*
 * The MIT License
 *
 * Copyright 2019 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.github_branch_source;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Item;
import hudson.util.FormValidation;
import jenkins.scm.api.trait.SCMSourceTrait;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.github.GitHub;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.interceptor.RequirePOST;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import static org.apache.commons.lang.StringUtils.*;

public class GitHubSCMSourceHttpsUrl extends GitHubSCMSourceAbstract {

    /**
     * Constructor, defaults to {@link #GITHUB_URL} as the end-point, and anonymous access, does not default any
     * {@link SCMSourceTrait} behaviours.
     *
     * @param repositoryURL the repository URL.
     * @since 2.2.0
     */
    @DataBoundConstructor
    public GitHubSCMSourceHttpsUrl(@NonNull String repositoryURL) {
        super( Helper.getOwner(repositoryURL), Helper.getRepository(repositoryURL));
        setApiUri(Helper.getUrl(repositoryURL));
    }

    public String getRepositoryURL(){
        return Helper.getUrlBase(this.getApiUri()) +"/"+this.getRepoOwner()+"/"+ this.getRepository();
    }

    static String getUrlBase(String apiUri) {
        if(StringUtils.equals("https://api.github.com", apiUri)){
            return "https://github.com";
        }else {
            return StringUtils.removeEnd(apiUri, "/api/v3");
        }
    }

    @Symbol("https url")
    @Extension
    public static class DescriptorImpl extends DescriptorAbstract {
        @Override
        public String getDisplayName() {
            return Messages.GitHubSCMSourceHttpsUrl_DisplayName();
        }

        /*
        Credentials will be checked with validagte button
        As the UI cannot be changed, will return no message.
         */
        @RequirePOST
        @Restricted(NoExternalUse.class)
        public FormValidation doCheckCredentialsId(@CheckForNull @AncestorInPath Item context,
                                                   @QueryParameter String repositoryURL,
                                                   @QueryParameter String value) {
            return FormValidation.ok();
        }

        @RequirePOST
        @Restricted(NoExternalUse.class)
        public FormValidation doCheckCredentials(@CheckForNull @AncestorInPath Item context,
                                                 @QueryParameter String repositoryURL,
                                                 @QueryParameter String credentialsId) {
            if( !StringUtils.startsWith(repositoryURL, "https://")){
                return FormValidation.error("HTTPS URL should be 'https://...'");
            }

            StandardCredentials credentials = Connector.lookupScanCredentials(context, repositoryURL, credentialsId);
            StringBuilder sb = new StringBuilder();
            try {

                URL url = new URL(repositoryURL);
                String apiUri;
                if ("github.com".equals(url.getHost())){
                    apiUri = "https://api." + url.getHost();//if github.com => api.github.com, otherwise github enterprise
                }else {
                    apiUri = "https://" + url.getHost() + "/api/v3";
                }
                GitHub github = Connector.connect(apiUri, credentials);
                github.checkApiUrlValidity();

                if (github.isCredentialValid()){
                    sb.append("User "+ github.getMyself().getLogin()+ " ok. ");
                }
                String path = removeStart(url.getPath(), "/");
                checkRepository(path);
                github.getRepository(removeEnd(path, ".git"));
                sb.append("Connection Valid. ");
            } catch (IOException e) {
                return FormValidation.error("Error accessing the server. "+ sb.toString());
            }
            return FormValidation.ok(sb.toString());
        }

        private void checkRepository(String path) throws IOException {
            if (isBlank(path))
                throw new IOException("Illegal repository: "+ path);
            String[] split = path.split("/");
            if( split == null || split.length != 2){
                throw new IOException("Illegal repository: "+ path);
            }else if( isBlank(split[0])|| isBlank(split[1])){
                throw new IOException("Illegal repository: "+ path);
            }
        }
    }

    private static class Helper {

        static final int OWNER = 1;
        static final int REPOSITORY = 2;

        static String getOwner(String repo){
            return Helper. getElementAt(repo, 1);
        }

        static String getRepository(String repo){
            return Helper.getElementAt(repo, 2);
        }

        static String getUrl(String repositoryURL) {
            return Helper.getElementAt(repositoryURL, 0);
        }

        static String getElementAt(String repo, int pos){
            try {
                URL url = new URL(repo);
                if (pos == OWNER || pos == REPOSITORY) {
                    String[] split = url.getPath().split("/");
                    // format: owner/repository
                    return split[pos];
                }else { // URL
                    String host = url.getHost();
                    String protocol = url.getProtocol();
                    if(StringUtils.equals("github.com", host)){
                        return String.format("%s://%s", protocol, "api.github.com");
                    }else{
                        return String.format("%s://%s/api/v3", protocol, host);
                    }
                }
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException("Illegal URL "+ repo, e);
            }
        }

        static String getUrlBase(String apiUri) {
            if(StringUtils.equals("https://api.github.com", apiUri)){
                return "https://github.com";
            }else {
                return removeEnd(apiUri, "/api/v3");
            }
        }


    }
}
