package org.jenkinsci.plugins.github_branch_source;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.apache.commons.lang.StringUtils.removeEnd;

@Restricted(NoExternalUse.class)
public class GitHubSCMSourceHelper {

    @NonNull
    String apiUri = "";

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
        try {
            helper.apiUri = getUri(source.rawUrl, source.getApiUri());
            helper.repo = getRepoFullName(source);
            String[] split = helper.repo.split("/");
            if (split != null && split.length == 2) {
                helper.owner = split[0];
                helper.repoName = split[1];
            }
            helper.url = getUrl(source);

        } catch (MalformedURLException e) {
            LOGGER.log(Level.WARNING, "Malformed repository URL {0}", source.rawUrl);
        }
        return helper;

    }
    @NonNull
    private static URL getUrl(GitHubSCMSource source) throws MalformedURLException {
        if(StringUtils.isNotBlank(source.getRawUrl()) ){
            return new URL (removeEnd(source.getRawUrl(),".git"));
        }else{
            if( StringUtils.isBlank(source.getApiUri())){
                return new URL("https://github.com/" + source.getRepoOwner()+"/"+source.getRepository());
            }else{
                String baseURL = removeEnd(source.getApiUri(), "/api/v3");
                return new URL(baseURL+ "/" + source.getRepoOwner()+"/"+source.getRepository());
            }

        }
    }

    @NonNull
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

    @NonNull
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

        }
        return uri;
    }

    @SuppressFBWarnings
    private static String getRepoFullName(GitHubSCMSource source) {
        String url = source.getRepoOwner() + '/' + source.getRepository();
        if (source.getApiUri() == null) {
            if ("/".equals(url)) {
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
