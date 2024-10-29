package org.jenkinsci.plugins.github_branch_source;

import com.cloudbees.plugins.credentials.CredentialsScope;
import hudson.util.Secret;

public class GitHubApp {

    // PKCS8 private key (https://stackoverflow.com/a/22176759/4951015)
    private static final String PRIVATE_KEY = "-----BEGIN PRIVATE KEY-----\n"
            +
            // Windows line ending
            "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQD7vHsVwyDV8cj7\r\n"
            +
            // This should also work
            "5yR4WWl6rlgf/e5zmeBgtm0PCgnitcSbD5FU33301DPY5a7AtqVBOwEnE14L9XS7\r"
            + "ov61U+x1m4aQmqR/dPQaA2ayh2cYPszWNQMp42ArDIfg7DhSrvsRJKHsbPXlPjqe\n"
            + "c0udLqhSLVIO9frNLf+dAsLsgYk8O39PKGb33akGG7tWTe0J+akNQjgbS7vOi8sS\n"
            + "NLwHIdYfz/Am+6Xmm+J4yVs6+Xt3kOeLdFBkz8H/HGsJq854MbIAK/HuId1MOPS0\n"
            + "cDWh37tzRsM+q/HZzYRkc5bhNKw/Mj9jN9jD5GH0Lfea0QFedjppf1KvWdcXn+/W\n"
            + "M7OmyfhvAgMBAAECggEAN96H7reExRbJRWbySCeH6mthMZB46H0hODWklK7krMUs\n"
            + "okFdPtnvKXQjIaMwGqMuoACJa/O3bq4GP1KYdwPuOdfPkK5RjdwWBOP2We8FKXNe\n"
            + "oLfZQOWuxT8dtQSYJ3mgTRi1OzSfikY6Wko6YOMnBj36tUlQZVMtJNqlCjphi9Uz\n"
            + "6EyvRURlDG8sBBbC7ods5B0789qk3iGH/97ia+1QIqXAUaVFg3/BA6wkxkbNG2sN\n"
            + "tqULgVYTw32Oj/Y/H1Y250RoocTyfsUS3I3aPIlnvcgp2bugWqDyYJ58nDIt3Pku\n"
            + "fjImWrNz/pNiEs+efnb0QEk7m5hYwxmyXN4KRSv0OQKBgQD+I3Y3iNKSVr6wXjur\n"
            + "OPp45fxS2sEf5FyFYOn3u760sdJOH9fGlmf9sDozJ8Y8KCaQCN5tSe3OM+XDrmiw\n"
            + "Cu/oaqJ1+G4RG+6w1RJF+5Nfg6PkUs7eJehUgZ2Tox8Tg1mfVIV8KbMwNi5tXpug\n"
            + "MVmA2k9xjc4uMd2jSnSj9NAqrQKBgQD9lIO1tY6YKF0Eb0Qi/iLN4UqBdJfnALBR\n"
            + "MjxYxqqI8G4wZEoZEJJvT1Lm6Q3o577N95SihZoj69tb10vvbEz1pb3df7c1HEku\n"
            + "LXcyVMvjR/CZ7dOSNgLGAkFfOoPhcF/OjSm4DrGPe3GiBxhwXTBjwJ5TIgEDkVIx\n"
            + "ZVo5r7gPCwKBgQCOvsZo/Q4hql2jXNqxGuj9PVkUBNFTI4agWEYyox7ECdlxjks5\n"
            + "vUOd5/1YvG+JXJgEcSbWRh8volDdL7qXnx0P881a6/aO35ybcKK58kvd62gEGEsf\n"
            + "1jUAOmmTAp2y7SVK7EOp8RY370b2oZxSR0XZrUXQJ3F22wV98ZVAfoLqZQKBgDIr\n"
            + "PdunbezAn5aPBOX/bZdZ6UmvbZYwVrHZxIKz2214U/STAu3uj2oiQX6ZwTzBDMjn\n"
            + "IKr+z74nnaCP+eAGhztabTPzXqXNUNUn/Zshl60BwKJToTYeJXJTY+eZRhpGB05w\n"
            + "Mz7M+Wgvvg2WZcllRnuV0j0UTysLhz1qle0vzLR9AoGBAOukkFFm2RLm9N1P3gI8\n"
            + "mUadeAlYRZ5o0MvumOHaB5pDOCKhrqAhop2gnM0f5uSlapCtlhj0Js7ZyS3Giezg\n"
            + "38oqAhAYxy2LMoLD7UtsHXNp0OnZ22djcDwh+Wp2YORm7h71yOM0NsYubGbp+CmT\n"
            + "Nw9bewRvqjySBlDJ9/aNSeEY\n"
            + "-----END PRIVATE KEY-----";

    public static GitHubAppCredentials createCredentials(final String id) {
        return new GitHubAppCredentials(CredentialsScope.GLOBAL, id, "sample", "54321", Secret.fromString(PRIVATE_KEY));
    }

    public static GitHubAppCredentials createCredentials(final String id, final String owner) {
        final var credentials = createCredentials(id);
        credentials.setOwner(owner);
        return credentials;
    }
}
