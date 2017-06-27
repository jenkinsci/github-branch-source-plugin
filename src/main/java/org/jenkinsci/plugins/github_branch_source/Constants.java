package org.jenkinsci.plugins.github_branch_source;

import java.util.regex.Pattern;

class Constants {
    static final String REFS_HEADS = "refs/heads/";
    static final String GITHUB_API_URL = "api.github.com";
    static final String GITHUB_RAW_URL = "https://raw.githubusercontent.com/";
    static final String LOCALHOST = "http://localhost:";
    static final Pattern REPOSITORY_NAME_PATTERN = Pattern.compile("https?://([^/]+)/([^/]+)/([^/]+)");
}
