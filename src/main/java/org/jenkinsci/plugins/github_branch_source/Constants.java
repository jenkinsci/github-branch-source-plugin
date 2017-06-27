package org.jenkinsci.plugins.github_branch_source;

import java.util.regex.Pattern;

class Constants {
    static final String REFS_HEADS = "refs/heads/";
    static final Pattern REPOSITORY_NAME_PATTERN = Pattern.compile("https?://([^/]+)/([^/]+)/([^/]+)");
}
