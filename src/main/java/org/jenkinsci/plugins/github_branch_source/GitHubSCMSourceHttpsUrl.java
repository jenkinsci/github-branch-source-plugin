/*
 * The MIT License
 *
 * Copyright 2015-2017 CloudBees, Inc.
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
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.interceptor.RequirePOST;

import java.net.MalformedURLException;
import java.net.URL;

public class GitHubSCMSourceHttpsUrl extends GitHubSCMSourceAbstract {

    /**
     * Constructor, defaults to {@link #GITHUB_URL} as the end-point, and anonymous access, does not default any
     * {@link SCMSourceTrait} behaviours.
     *
     * @param repoOwner the repository owner.
     * @param repository the repository name.
     * @since 2.2.0
     */
    @DataBoundConstructor
    public GitHubSCMSourceHttpsUrl(@NonNull String repositoryURL) {
        super(getOwner(repositoryURL,1), getOwner(repositoryURL,2));
        setApiUri(getUrl(repositoryURL));
    }

    private static String getUrl(String repositoryURL) {
        try {
            URL url = new URL(repositoryURL);
            String host = url.getHost();
            String protocol = url.getProtocol();
            if(StringUtils.equals("github.com", host)){
                return String.format("%s://%s", protocol, "api.github.com");
            }else{
                return String.format("%s://%s/api/v3", protocol, host);
            }

        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Illegal URL "+ repositoryURL, e);
        }
    }

    /**
     * Legacy constructor.
     * @param id the source id.
     * @param apiUri the GitHub endpoint.
     * @param checkoutCredentialsId the checkout credentials id or {@link DescriptorImpl#SAME} or
     * {@link DescriptorImpl#ANONYMOUS}.
     * @param scanCredentialsId the scan credentials id or {@code null}.
     * @param repoOwner the repository owner.
     * @param repository the repository name.
     */
    @Deprecated
    public GitHubSCMSourceHttpsUrl(@CheckForNull String id, @CheckForNull String apiUri, @NonNull String checkoutCredentialsId,
                                   @CheckForNull String scanCredentialsId, @NonNull String repoOwner,
                                   @NonNull String repository) {
        super(id, apiUri, checkoutCredentialsId, scanCredentialsId, repoOwner, repository);
    }

    public static String getOwner(String repo, int pos){
        try {
            URL url = new URL(repo);
            String[] split = url.getPath().split("/");
            return split[pos];
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Illegal URL "+ repo, e);
        }
    }

    public String getRepositoryURL(){
        return getUrlBase(this.getApiUri()) +"/"+this.getRepoOwner()+"/"+ this.getRepository();
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
            return "https url";
        }

        @RequirePOST
        @Restricted(NoExternalUse.class)
        public FormValidation doCheckCredentialsId(@CheckForNull @AncestorInPath Item context,
                                                   @QueryParameter String repositoryURL,
                                                   @QueryParameter String value) {
            return Connector.checkScanCredentials(context, GitHubSCMSourceHttpsUrl.getUrl(repositoryURL), value);
        }
    }
}
