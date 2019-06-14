package org.jenkinsci.plugins.github_branch_source;

import org.apache.commons.lang.StringUtils;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GitHubSCMSourceHelper {

    String uri;
    String repo;
    private static final Logger LOGGER = Logger.getLogger(GitHubSCMSourceHelper.class.getName());

    private GitHubSCMSourceHelper(){}

    public static GitHubSCMSourceHelper build(GitHubSCMSource source) {
        GitHubSCMSourceHelper helper = new GitHubSCMSourceHelper();
        helper.uri = getUri(source.rawUrl, source.getApiUri());
        helper.repo = getRepoFullName(source);
        return helper;

    }

    private static String getUri(String rawUrl, String apiUri) {
        //RawURL
        if (StringUtils.isNotBlank(rawUrl)) {
            return getRawUrlUri(rawUrl);
        } else if (StringUtils.isBlank(apiUri)) { //GitHub Repo
            return "https://api.github.com";
        }else {
            // GitHub Enterprise or rawUrl
            return apiUri;
        }
    }

    private static String getRawUrlUri(String rawUrl) {
        String uri = null;
        URL url = null;
        try {
            url = new URL(rawUrl);
            if ("github.com".equals(url.getHost())) {
                uri = "https://api." + url.getHost();
            } else {
                uri = "https://" + url.getHost() + "/api/v3";
            }
        } catch (MalformedURLException e) {
            LOGGER.log(Level.WARNING, "Malformed repository URL {0}", rawUrl);

        }
        return uri;
    }

    private static String getRepoFullName(GitHubSCMSource source) {
        String url = source.getRepoOwner() + '/' + source.getRepository();
        // GitHub
        if (source.getApiUri() == null) {
            if ("/".equals(url)) {
                try {
                    url = StringUtils.removeStart(new URL(source.getRawUrl()).getPath(), "/");
                } catch (MalformedURLException e) {
                    LOGGER.log(Level.WARNING, "Malformed repository URL {0}", source.rawUrl);
                }
            }
            // GitHub Enterprise
        } else if (source.getApiUri().contains("/api/v3")) {
            String uri = source.getApiUri().substring(0, source.getApiUri().indexOf("/api/v3"));
            if (source.getRawUrl() != null && source.getRawUrl().indexOf(uri) != -1) {
                url = source.getRawUrl().substring(uri.length() + 1);// plus '/'
            }
        }
        return StringUtils.removeEnd(url, ".git");
    }

}
