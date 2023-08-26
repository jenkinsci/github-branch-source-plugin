package org.jenkinsci.plugins.github_branch_source;

public class InvalidPrivateKeyException extends RuntimeException {

    public InvalidPrivateKeyException(String message) {
        super(message);
    }
}
