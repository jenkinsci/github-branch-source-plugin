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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.github.config.GitHubServerConfig;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.apache.commons.lang.StringUtils.isBlank;
import static org.apache.commons.lang.StringUtils.isEmpty;
import static org.apache.commons.lang.StringUtils.removeEnd;

@Restricted(NoExternalUse.class)
public class GitHubSCMSourceHelper {

    @NonNull
    String apiURI = "";

    @NonNull
    String repo;

    @NonNull
    String owner;

    @NonNull
    String repoName;

    @NonNull
    URL url;

    private static final Logger LOGGER = Logger.getLogger(GitHubSCMSourceHelper.class.getName());

    @SuppressFBWarnings
    private GitHubSCMSourceHelper(){}

    @NonNull
    public static GitHubSCMSourceHelper build(GitHubSCMSource source) {
        GitHubSCMSourceHelper helper = new GitHubSCMSourceHelper();
        if (source == null) return helper;

        if (isBlank(source.rawUrl) && isBlank(source.getRepoOwnerInternal()) && isBlank(source.getRepositoryInternal())) {
            throw new IllegalArgumentException("Repository URL must not be empty");
        }

        try {
            helper.apiURI = getUri(source.rawUrl, source.getApiUri());
            helper.repo = getRepoFullName(source);
            String[] split = helper.repo.split("/");
            if (split != null && split.length == 2) {
                helper.owner = split[0];
                helper.repoName = split[1];
            }else
                throw new IllegalArgumentException("Repository URL not valid");
            helper.url = getUrl(source);

        } catch (MalformedURLException e) {
            LOGGER.log(Level.WARNING, "Malformed repository URL {0}", source.rawUrl);
            throw new IllegalArgumentException("Repository URL not valid, e");
        }
        return helper;

    }
    @NonNull
    private static URL getUrl(GitHubSCMSource source) throws MalformedURLException {
        if(StringUtils.isNotBlank(source.getRawUrl()) ){
            return new URL (removeEnd(source.getRawUrl(),".git"));
        }else{
            if(isBlank(source.getApiUri())){
                return new URL("https://github.com/" + source.repoOwner+"/"+source.repository);
            }else{
                String baseURL = removeEnd(source.getApiUri(), "/api/v3");
                return new URL(baseURL+ "/" + source.repoOwner+"/"+source.repository);
            }

        }
    }

    @NonNull
    private static String getUri(String rawUrl, String apiUri) {
        //RawURL
        if (StringUtils.isNotBlank(rawUrl)) {
            return getRawUrlUri(rawUrl);
        } else if (isBlank(apiUri)) { //GitHub Repo
            return GitHubServerConfig.GITHUB_URL;
        }else {
            // GitHub Enterprise or rawUrl
            return apiUri;
        }
    }

    @CheckForNull
    private static String getRawUrlUri(String rawUrl) {
        String uri = rawUrl;
        URL url;
        try {
            url = new URL(rawUrl);
            if ("github.com".equals(url.getHost())) {
                uri = "https://api." + url.getHost();
            } else {
                String port = "";
                boolean httpOk = "http".equals(url.getProtocol()) && url.getPort() == 80;
                boolean httpsOk = "https".equals(url.getProtocol()) && url.getPort() == 443;
                if (!httpOk && !httpsOk && url.getPort()!= -1){
                    port = ":"+url.getPort();
                }
                uri = url.getProtocol()+"://" + url.getHost() + port +  "/api/v3";
            }
        } catch (MalformedURLException e) {
            LOGGER.log(Level.WARNING, "Malformed repository URL {0}", rawUrl);
            throw new IllegalArgumentException("Repository URL not valid:" + rawUrl, e);

        }
        return GitHubConfiguration.normalizeApiUri(uri);
    }

    @SuppressFBWarnings
    private static String getRepoFullName(GitHubSCMSource source) {
        String url = source.repoOwner + '/' + source.repository;
        if ( isEmpty(source.getApiUri())  ) {
            if (isBlank(source.repoOwner) && isBlank(source.repository)) {
                try {
                    url = StringUtils.removeStart(new URL(source.getRawUrl()).getPath(), "/");
                } catch (MalformedURLException e) {
                    LOGGER.log(Level.WARNING, "Malformed repository URL {0}", source.rawUrl);
                }
            }
        } else if (source.getApiUri().contains("/api/v3")) {
            String uri = source.getApiUri().substring(0, source.getApiUri().indexOf("/api/v3"));
            if (source.getRawUrl() != null && source.getRawUrl().indexOf(uri) != -1) {
                url = source.getRawUrl().substring(uri.length() + 1);// plus '/'
            }
        }
        return removeEnd(url, ".git");
    }

}
