package org.jenkinsci.plugins.github_branch_source;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Date;

import static java.util.Objects.requireNonNull;
import java.util.concurrent.TimeUnit;

class JwtHelper {

    /**
     * Somewhat less than the <a href="https://developer.github.com/apps/building-github-apps/authenticating-with-github-apps/#authenticating-as-a-github-app">maximum JWT validity</a>.
     */
    static final long VALIDITY_MS = TimeUnit.MINUTES.toMillis(8);

    /**
     * Create a JWT for authenticating to GitHub as an app installation
     * @param githubAppId the app ID
     * @param privateKey PKC#8 formatted private key
     * @return JWT for authenticating to GitHub
     */
    static String createJWT(String githubAppId, final String privateKey) {
        requireNonNull(githubAppId, privateKey);

        SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.RS256;

        long nowMillis = System.currentTimeMillis();
        Date now = new Date(nowMillis);

        Key signingKey;
        try {
            signingKey = getPrivateKeyFromString(privateKey);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Couldn't parse private key for GitHub app, make sure it's PKCS#8 format", e);
        }

        JwtBuilder builder = Jwts.builder()
                .setIssuedAt(now)
                .setIssuer(githubAppId)
                .signWith(signingKey, signatureAlgorithm);

        Date exp = new Date(nowMillis + VALIDITY_MS);
        builder.setExpiration(exp);

        return builder.compact();
    }

    /**
     * Convert a PKCS#8 formatted private key in string format into a java PrivateKey
     * @param key PCKS#8 string
     * @return private key
     * @throws GeneralSecurityException if we couldn't parse the string
     */
    private static PrivateKey getPrivateKeyFromString(final String key) throws GeneralSecurityException {
        if (key.contains("RSA")) {
            throw new InvalidPrivateKeyException(
                    "Private key must be a PKCS#8 formatted string, to convert it from PKCS#1 use: "
                    + "openssl pkcs8 -topk8 -inform PEM -outform PEM -in current-key.pem -out new-key.pem -nocrypt"
            );
        }

        String privateKeyContent = key.replaceAll("\\n", "")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "");

        KeyFactory kf = KeyFactory.getInstance("RSA");

        try {
            byte[] decode = Base64.getDecoder().decode(privateKeyContent);
            PKCS8EncodedKeySpec keySpecPKCS8 = new PKCS8EncodedKeySpec(decode);

            return kf.generatePrivate(keySpecPKCS8);
        } catch (IllegalArgumentException e) {
            throw new InvalidPrivateKeyException("Failed to decode private key: " + e.getMessage());
        }
    }

}
