package org.jenkinsci.plugins.github_branch_source;

import com.squareup.okhttp.OkUrlFactory;
import org.kohsuke.github.HttpConnector;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Copy-paste due to class loading issues
 *
 * @see org.kohsuke.github.extras.OkHttpConnector
 */
class OkHttpConnector implements HttpConnector {
    private final OkUrlFactory urlFactory;

    OkHttpConnector(OkUrlFactory urlFactory) {
        this.urlFactory = urlFactory;
    }

    @Override
    public HttpURLConnection connect(URL url) throws IOException {
        return urlFactory.open(url);
    }
}

