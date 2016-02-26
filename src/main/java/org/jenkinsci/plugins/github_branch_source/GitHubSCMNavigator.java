/*
 * The MIT License
 *
 * Copyright 2015 CloudBees, Inc.
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

import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.domains.DomainSpecification;
import com.cloudbees.plugins.credentials.domains.HostnameSpecification;
import com.cloudbees.plugins.credentials.domains.SchemeSpecification;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.Extension;
import hudson.Util;
import hudson.model.TaskListener;
import hudson.remoting.Base64;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.annotation.CheckForNull;
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.SCMNavigatorDescriptor;
import jenkins.scm.api.SCMSourceObserver;
import jenkins.scm.api.SCMSourceOwner;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.auth.AuthScope;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.DefaultHttpClient;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.github.GHMyself;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

public class GitHubSCMNavigator extends SCMNavigator {
    private static final String GITHUB_API_URL = "https://api.github.com/";


    private final String repoOwner;
    private final String scanCredentialsId;
    private final String checkoutCredentialsId;
    private final String apiUri;
    private String pattern = ".*";
    private boolean manageWebHooks = true;

    @DataBoundConstructor public GitHubSCMNavigator(String apiUri, String repoOwner, String scanCredentialsId, String checkoutCredentialsId) {
        this.repoOwner = repoOwner;
        this.scanCredentialsId = Util.fixEmpty(scanCredentialsId);
        this.checkoutCredentialsId = checkoutCredentialsId;
        this.apiUri = Util.fixEmpty(apiUri);
    }

    public String getRepoOwner() {
        return repoOwner;
    }

    @CheckForNull
    public String getScanCredentialsId() {
        return scanCredentialsId;
    }

    @CheckForNull
    public String getCheckoutCredentialsId() {
        return checkoutCredentialsId;
    }

    public String getPattern() {
        return pattern;
    }

    @CheckForNull
    public String getApiUri() {
        return apiUri;
    }

    public boolean isManageWebHooks() {
        return manageWebHooks;
    }

    @DataBoundSetter public void setManageWebHooks(boolean manageWebHooks) {
        this.manageWebHooks = manageWebHooks;
    }

    @DataBoundSetter public void setPattern(String pattern) {
        Pattern.compile(pattern);
        this.pattern = pattern;
    }

    @Override public void visitSources(SCMSourceObserver observer) throws IOException, InterruptedException {
        TaskListener listener = observer.getListener();
        if (repoOwner.isEmpty()) {
            listener.getLogger().format("Must specify user or organization%n");
            return;
        }
        StandardCredentials credentials = Connector.lookupScanCredentials(observer.getContext(), apiUri, scanCredentialsId);
        GitHub github = Connector.connect(apiUri, credentials);
        if (credentials != null && !github.isCredentialValid()) {
            listener.getLogger().format("Invalid scan credentials, skipping%n");
            return;
        }

        if (!github.isAnonymous()) {
            listener.getLogger().format("Connecting to GitHub using %s%n", CredentialsNameProvider.name(credentials));
            GHMyself myself = null;
            try {
                // Requires an authenticated access
                myself = github.getMyself();
            } catch (IOException e) {
                // Something wrong happened, maybe java.net.ConnectException?
            }
            if (myself != null && repoOwner.equals(myself.getLogin())) {
                listener.getLogger().format("Looking up repositories of myself %s%n%n", repoOwner);
                for (GHRepository repo : myself.listRepositories()) {
                    if (!repo.getOwnerName().equals(repoOwner)) {
                        continue; // ignore repos in other orgs when using GHMyself
                    }
                    add(listener, observer, repo, apiUri);
                }
                return;
            }
        } else {
            listener.getLogger().format("Connecting to GitHub using anonymous access%n");
        }

        GHOrganization org = null;
        try {
            org = github.getOrganization(repoOwner);
        } catch (RateLimitExceededException rle) {
            listener.getLogger().format("%n%s%n%n", rle.getMessage());
            throw new InterruptedException();
        } catch (IOException e) {
            // may be a user... ok to ignore
        }
        if (org != null && repoOwner.equals(org.getLogin())) {
            listener.getLogger().format("Looking up repositories of organization %s%n%n", repoOwner);
            for (GHRepository repo : org.listRepositories()) {
                add(listener, observer, repo, apiUri);
            }
            return;
        }

        GHUser user = null;
        try {
            user = github.getUser(repoOwner);
        } catch (RateLimitExceededException rle) {
            listener.getLogger().format("%n%s%n%n", rle.getMessage());
            throw new InterruptedException();
        } catch (IOException e) {
            // Something wrong happened, maybe java.net.ConnectException?
        }
        if (user != null && repoOwner.equals(user.getLogin())) {
            listener.getLogger().format("Looking up repositories of user %s%n%n", repoOwner);
            for (GHRepository repo : user.listRepositories()) {
                add(listener, observer, repo, apiUri);
            }
        }
    }

    /**
     * Get a Github instance
     */
    protected static GitHub getGithubConnection(String apiUri, StandardCredentials credentials) throws IOException {
        return Connector.connect(apiUri, credentials);
    }

    /**
     * Creates an API key for the given username/password with the correct permissions to manage webhooks
     */
    @RequirePOST
    public HttpResponse doCreateApiKey(StaplerRequest req) throws Exception {
        String username = req.getParameter("username");
        String password = req.getParameter("password");
        String apiKey = null;
        String apiUri = this.apiUri;
        String apiBaseUrl;
        if (apiUri == null) {
            apiBaseUrl = GITHUB_API_URL;
        }
        else {
            apiBaseUrl = apiUri;
            if (apiBaseUrl.endsWith("/")) { // strip trailing slash
                apiBaseUrl = apiBaseUrl.substring(0, apiBaseUrl.length()-1);
            }
        }

        String jenkinsUrl = Jenkins.getActiveInstance().getRootUrl();
        String uuid = UUID.randomUUID().toString();
        String authName = "Jenkins / " + uuid + " @ " + jenkinsUrl;

        JSONObject response = new JSONObject();

        try {
            // try as a personal access token
            HttpGet getLogin = new HttpGet(apiBaseUrl + "/user");
            if(StringUtils.isEmpty(password)) {
                JSONObject currentUser = JSONObject.fromObject(getResponse(new UsernamePasswordCredentialsImpl(null, null, null, username, ""), getLogin));
                try {
                    if(currentUser.getString("login") != null) {
                        apiKey = username;
                        username = currentUser.getString("login");
                    }
                } catch(Exception e) {
                    // ignore here, try to create an access token
                }
            }

            if(apiKey == null) {
                HttpGet getAuths = new HttpGet(apiBaseUrl + "/authorizations");
                JSONArray auths = JSONArray.fromObject(getResponse(new UsernamePasswordCredentialsImpl(null, null, null, username, password), getAuths));
                for (int i = 0; i < auths.size(); i++) {
                    JSONObject auth = auths.getJSONObject(i);
                    if (authName.equals(auth.getString("note"))) {
                        // already exists
                        response.put("error", "Already exists");
                        return respondJson(500, response.toString());
                    }
                }

                HttpPost createAuthPost = new HttpPost(apiBaseUrl + "/authorizations");

                JSONObject json = new JSONObject();
                json.put("note", authName);

                JSONArray scopes = new JSONArray();
                scopes.add("repo");
                scopes.add("public_repo");
                scopes.add("admin:org_hook");
                scopes.add("admin:repo_hook");
                json.put("scopes", scopes);

                createAuthPost.setEntity(new StringEntity(json.toString()));

                JSONObject res = JSONObject.fromObject(getResponse(new UsernamePasswordCredentialsImpl(null, null, null, username, password), createAuthPost));

                if (res.containsKey("errors")) {
                    JSONArray errors = res.getJSONArray("errors");
                    JSONObject err = errors.getJSONObject(0);
                    String code = err.getString("code");
                    response.put("error", code);
                }
                else {
                    apiKey = res.getString("token");
                }
            }

            if(apiKey != null) {
                // Would use the GitHubTokenCredentialsCreator, but the credential descriptions are not good
                // and it creates incompatible credential types
                StandardCredentials creds = createCredentials(apiUri, username, apiKey, uuid.substring(uuid.length()-4));

                response.put("id", creds.getId());

                return respondJson(200, response.toString());
            }
        } catch(final Exception e) {
            response.put("error", e.getMessage());
        }

        return respondJson(500, response.toString());
    }

    /**
     * Create a github usesrname/password credential with the apiKey
     */
    private StandardCredentials createCredentials(String apiUri, String username, String apiKey, String identifier) {
        String description = String.format("%s GitHub auth token *%s", username, identifier);
        final UsernamePasswordCredentialsImpl creds = new UsernamePasswordCredentialsImpl(
                CredentialsScope.GLOBAL,
                UUID.randomUUID().toString(),
                description,
                username,
                apiKey);

        URI serverUri = URI.create(apiUri = StringUtils.defaultIfBlank(apiUri, Connector.GITHUB_CREDENTIAL_DOMAIN_URI));

        List<DomainSpecification> specifications = Arrays.asList(
            new SchemeSpecification(serverUri.getScheme()),
            new HostnameSpecification(serverUri.getHost(), null)
        );

        final Domain domain = new Domain(serverUri.getHost(), serverUri.getHost(), specifications);
        ACL.impersonate(ACL.SYSTEM, new Runnable() { // do it with system rights
            @Override
            public void run() {
                try {
                    new SystemCredentialsProvider.StoreImpl().addDomain(domain, creds);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        return creds;

    }

    private HttpResponse respondJson(final int status, final String json) {
        return new HttpResponse() {
            @Override
            public void generateResponse(StaplerRequest req, StaplerResponse rsp, Object node) throws IOException, ServletException {
                rsp.setStatus(status);
                rsp.setContentType("application/json;charset=UTF-8");
                rsp.getWriter().println(json);
                             }
        };
    }
    /**
     * Get HttpClient response, using basic authentication
     */
    protected String getResponse(UsernamePasswordCredentials creds, HttpUriRequest request) throws Exception {
        org.apache.http.auth.UsernamePasswordCredentials httpCreds = new org.apache.http.auth.UsernamePasswordCredentials(creds.getUsername(), creds.getPassword().getPlainText());
        DefaultHttpClient httpClient = new DefaultHttpClient();
        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(AuthScope.ANY, httpCreds);
        httpClient.setCredentialsProvider(credsProvider);
        // HttpClient Basic Authentication fail; do it ourselves:
        request.setHeader("Authorization", "Basic " + Base64.encode((creds.getUsername() + ":" + creds.getPassword()).getBytes()));
        org.apache.http.HttpResponse httpRes = httpClient.execute(request);
        HttpEntity ent = httpRes.getEntity();
        String payload = IOUtils.toString(ent.getContent(), getEncoding(ent));
        if (httpRes.getStatusLine().getStatusCode() >= 400) {
            JSONObject o = JSONObject.fromObject(payload);
            if (o.containsKey("message")) {
                throw new RuntimeException(o.getString("message"));
            }
        }
        return payload;
    }

    /**
     * Get a charset encoding
     * @param ent
     * @return
     */
    private static Charset getEncoding(HttpEntity ent) {
        Header hdr = ent.getContentEncoding();
        if (hdr != null && hdr.getValue() != null) {
            return Charset.forName(hdr.getValue());
        }
        return Charsets.UTF_8;
    }

    private void add(TaskListener listener, SCMSourceObserver observer, GHRepository repo, String apiUri) throws InterruptedException {
        String name = repo.getName();
        if (!Pattern.compile(pattern).matcher(name).matches()) {
            listener.getLogger().format("Ignoring %s%n", name);
            return;
        }
        listener.getLogger().format("Proposing %s%n", name);
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        SCMSourceObserver.ProjectObserver projectObserver = observer.observe(name);
        projectObserver.addSource(new GitHubSCMSource(null, apiUri, checkoutCredentialsId, scanCredentialsId, repoOwner, name));
        projectObserver.complete();
    }

    @Extension public static class DescriptorImpl extends SCMNavigatorDescriptor {

        @Override public String getDisplayName() {
            return "GitHub Organization";
        }

        @Override public SCMNavigator newInstance(String name) {
            return new GitHubSCMNavigator("", name, "", GitHubSCMSource.DescriptorImpl.SAME);
        }

        @Restricted(NoExternalUse.class)
        public FormValidation doCheckScanCredentialsId(@AncestorInPath SCMSourceOwner context,
                @QueryParameter String scanCredentialsId, @QueryParameter String apiUri) {
            if (!scanCredentialsId.isEmpty()) {
                StandardCredentials credentials = Connector.lookupScanCredentials(context, apiUri, scanCredentialsId);
                if (credentials == null) {
                    FormValidation.error("Invalid credentials");
                } else {
                    try {
                        GitHub github = getGithubConnection(apiUri, credentials);
                        if (github.isCredentialValid()) {
                            return FormValidation.ok();
                        }
                    } catch (IOException e) {
                        // ignore, never thrown
                    }
                }
                return FormValidation.error("Invalid credentials");
            } else {
                return FormValidation.warning("Credentials are recommended");
            }
        }

        public ListBoxModel doFillScanCredentialsIdItems(@AncestorInPath SCMSourceOwner context/* TODO , @QueryParameter String apiUri*/) {
            StandardListBoxModel result = new StandardListBoxModel();
            result.withEmptySelection();
            Connector.fillScanCredentialsIdItems(result, context, null);
            return result;
        }

        public ListBoxModel doFillCheckoutCredentialsIdItems(@AncestorInPath SCMSourceOwner context/* TODO , @QueryParameter String apiUri*/) {
            StandardListBoxModel result = new StandardListBoxModel();
            result.add("- same as scan credentials -", GitHubSCMSource.DescriptorImpl.SAME);
            result.add("- anonymous -", GitHubSCMSource.DescriptorImpl.ANONYMOUS);
            Connector.fillCheckoutCredentialsIdItems(result, context, null);
            return result;
        }

        public ListBoxModel doFillApiUriItems() {
            ListBoxModel result = new ListBoxModel();
            result.add("GitHub", "");
            for (Endpoint e : GitHubConfiguration.get().getEndpoints()) {
                result.add(e.getName() == null ? e.getApiUri() : e.getName(), e.getApiUri());
            }
            return result;
        }
    }

}
