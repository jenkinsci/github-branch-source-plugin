package org.jenkinsci.plugins.github_branch_source;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.jenkinsci.plugins.github_branch_source.JwtHelper.createJWT;
import static org.mockito.ArgumentMatchers.contains;

public class JwtHelperTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    // https://stackoverflow.com/a/22176759/4951015
    private static final String PKCS8_PRIVATE_KEY = "-----BEGIN PRIVATE KEY-----\n" +
            "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQD7vHsVwyDV8cj7\n" +
            "5yR4WWl6rlgf/e5zmeBgtm0PCgnitcSbD5FU33301DPY5a7AtqVBOwEnE14L9XS7\n" +
            "ov61U+x1m4aQmqR/dPQaA2ayh2cYPszWNQMp42ArDIfg7DhSrvsRJKHsbPXlPjqe\n" +
            "c0udLqhSLVIO9frNLf+dAsLsgYk8O39PKGb33akGG7tWTe0J+akNQjgbS7vOi8sS\n" +
            "NLwHIdYfz/Am+6Xmm+J4yVs6+Xt3kOeLdFBkz8H/HGsJq854MbIAK/HuId1MOPS0\n" +
            "cDWh37tzRsM+q/HZzYRkc5bhNKw/Mj9jN9jD5GH0Lfea0QFedjppf1KvWdcXn+/W\n" +
            "M7OmyfhvAgMBAAECggEAN96H7reExRbJRWbySCeH6mthMZB46H0hODWklK7krMUs\n" +
            "okFdPtnvKXQjIaMwGqMuoACJa/O3bq4GP1KYdwPuOdfPkK5RjdwWBOP2We8FKXNe\n" +
            "oLfZQOWuxT8dtQSYJ3mgTRi1OzSfikY6Wko6YOMnBj36tUlQZVMtJNqlCjphi9Uz\n" +
            "6EyvRURlDG8sBBbC7ods5B0789qk3iGH/97ia+1QIqXAUaVFg3/BA6wkxkbNG2sN\n" +
            "tqULgVYTw32Oj/Y/H1Y250RoocTyfsUS3I3aPIlnvcgp2bugWqDyYJ58nDIt3Pku\n" +
            "fjImWrNz/pNiEs+efnb0QEk7m5hYwxmyXN4KRSv0OQKBgQD+I3Y3iNKSVr6wXjur\n" +
            "OPp45fxS2sEf5FyFYOn3u760sdJOH9fGlmf9sDozJ8Y8KCaQCN5tSe3OM+XDrmiw\n" +
            "Cu/oaqJ1+G4RG+6w1RJF+5Nfg6PkUs7eJehUgZ2Tox8Tg1mfVIV8KbMwNi5tXpug\n" +
            "MVmA2k9xjc4uMd2jSnSj9NAqrQKBgQD9lIO1tY6YKF0Eb0Qi/iLN4UqBdJfnALBR\n" +
            "MjxYxqqI8G4wZEoZEJJvT1Lm6Q3o577N95SihZoj69tb10vvbEz1pb3df7c1HEku\n" +
            "LXcyVMvjR/CZ7dOSNgLGAkFfOoPhcF/OjSm4DrGPe3GiBxhwXTBjwJ5TIgEDkVIx\n" +
            "ZVo5r7gPCwKBgQCOvsZo/Q4hql2jXNqxGuj9PVkUBNFTI4agWEYyox7ECdlxjks5\n" +
            "vUOd5/1YvG+JXJgEcSbWRh8volDdL7qXnx0P881a6/aO35ybcKK58kvd62gEGEsf\n" +
            "1jUAOmmTAp2y7SVK7EOp8RY370b2oZxSR0XZrUXQJ3F22wV98ZVAfoLqZQKBgDIr\n" +
            "PdunbezAn5aPBOX/bZdZ6UmvbZYwVrHZxIKz2214U/STAu3uj2oiQX6ZwTzBDMjn\n" +
            "IKr+z74nnaCP+eAGhztabTPzXqXNUNUn/Zshl60BwKJToTYeJXJTY+eZRhpGB05w\n" +
            "Mz7M+Wgvvg2WZcllRnuV0j0UTysLhz1qle0vzLR9AoGBAOukkFFm2RLm9N1P3gI8\n" +
            "mUadeAlYRZ5o0MvumOHaB5pDOCKhrqAhop2gnM0f5uSlapCtlhj0Js7ZyS3Giezg\n" +
            "38oqAhAYxy2LMoLD7UtsHXNp0OnZ22djcDwh+Wp2YORm7h71yOM0NsYubGbp+CmT\n" +
            "Nw9bewRvqjySBlDJ9/aNSeEY\n" +
            "-----END PRIVATE KEY-----";

    private static final String PKCS8_PUBLIC_KEY = "-----BEGIN PUBLIC KEY-----\n" +
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA+7x7FcMg1fHI++ckeFlp\n" +
            "eq5YH/3uc5ngYLZtDwoJ4rXEmw+RVN999NQz2OWuwLalQTsBJxNeC/V0u6L+tVPs\n" +
            "dZuGkJqkf3T0GgNmsodnGD7M1jUDKeNgKwyH4Ow4Uq77ESSh7Gz15T46nnNLnS6o\n" +
            "Ui1SDvX6zS3/nQLC7IGJPDt/Tyhm992pBhu7Vk3tCfmpDUI4G0u7zovLEjS8ByHW\n" +
            "H8/wJvul5pvieMlbOvl7d5Dni3RQZM/B/xxrCavOeDGyACvx7iHdTDj0tHA1od+7\n" +
            "c0bDPqvx2c2EZHOW4TSsPzI/YzfYw+Rh9C33mtEBXnY6aX9Sr1nXF5/v1jOzpsn4\n" +
            "bwIDAQAB\n" +
            "-----END PUBLIC KEY-----";


    private static final String PKCS1_PRIVATE_KEY = "-----BEGIN RSA PRIVATE KEY-----\n" +
            "MIIEpAIBAAKCAQEA26y2ZLYaNKHYg1FehH/WmXZ+SXG9ofLCf7+tR0j/BHbQy1Ck\n" +
            "u6Pqxn10nKPrAZSFakNDKI1vf92+Ny8LFitBucs2JaDSm1kUHjZaoCbp2FQmbr28\n" +
            "eO+q0oIaJ67WaIF9o1DzCiBBgqCOqZpDdZY1peRPQ7ttBfBvPOi9zEiWplrn2IlL\n" +
            "tlndlYtV+KHlIy7odaCaSHjzawTBxLe82lpX5+YHy0doNlI5l/epJMtjcE/l2jEj\n" +
            "xMZxWz4ZAiXd8hLYonUzxaup8IMKm4K8eh++4UcXAs0tjA0CGaieeyQZyBLPwFyf\n" +
            "k3JStqbBgwaKLzV0D1ayokQNvc0cm4tdgk6gVwIDAQABAoIBAGlZzSdDhhHTxIhF\n" +
            "z7RvsrVqdGo4mB9A0zJ89FcJlPPJH51CEZ7Dn+aNaA1vN1dMqScrFtwt6FlEOOMy\n" +
            "NnjtSdoWsOMe26IQ+Gr82j2QK/nJcZ0OdYLyPdQy/OQnH0CDSYO3YLdsfL5uzbxc\n" +
            "9RlBbn0enzz2d/SvOEnXvJ5p+YXRk3Y8Toccu66nPUKkeWDzZ3Ql/mf2Piw1VwvF\n" +
            "/5pvZRiH5Lh5MCc7AxHlDFXRq5jQKxSdJrtHhB/GFRfHg6EOAKfCGbPHwYIMb5BW\n" +
            "KNxRRyfpAPhUP9a+GgH4mHXkv+wSR87zE3hbCf7Fg/4mB25Cx4r/34E5W0F0XuCN\n" +
            "HzSwXHECgYEA8pdeT6R2mlWDgD7IfhyeYoUcJ0oXvd6dKlGOETlkzkGi4QvP3BsM\n" +
            "wg0sELPhuYCOG53SzSW9d5QkqDYJRY4/xg15QV2LYOMpP5b9cjJZRE3Uo9BVIBum\n" +
            "EFVZvuGzZaFUO6Zx3xQiQgHuCP8Tx676vTk36ka3fVQV5FdY8tP0HyUCgYEA59ET\n" +
            "v6eE2s10T9JeO3htK5TjwioMYpp3j+HUZX78anyqWw17OUityWi/dRnCoyfpPuIi\n" +
            "qBGNjMk3JZYz3MmoR9pPGKgzI43EQKBay6+CjZfcQ4Vw7qzW0bUKD2xfLU+ZOeR+\n" +
            "jJn5wdBvZHooX8e1en/aLj5h9h9FzhAy3/Sd1ssCgYB1S8tGJvdR2FclAzZeA+hx\n" +
            "KntaY/Dm1WSYuaY/ncioEgR3XAa9Hjck/Ml5qgBSeV487CqpFr5tuyueScJh503e\n" +
            "rVUbzec+iZfAL3mMZdvTsu5F5s3CIJxC+YHTUb40PbVEwk381vdZgyVdJDikLG8A\n" +
            "X1Ix7M97wdRz++f+QY2gIQKBgQCeHaiHt95RU4O7EjT+AVUNPd/fxsht1QgqFpHF\n" +
            "rMjEZUXZFyfuWZlX4F9+otR0bruUDbAvzNEsru4zb/Dt7ooegFQk8Ez5OjAbGIT1\n" +
            "mz/EDknJsFHoKfHYVdCH1pZQlJNhvm1mv3twbBgeg4fYVKJ+7IfHtPsiYhA9ziS1\n" +
            "RucF4wKBgQDJfd1BxBdkeSRIJ/C75iZ4vWWsM/JvMI1L68ZJEWdTqUvyyy9xLWEe\n" +
            "8wIGZTv/mnuQhOGSaUUk0fTup7ZwTfmg+hahhCBe5kSh4bav5+knu6yQ7nhwccs8\n" +
            "WXeajzno43UHZksae1LP1B3J1+0adxpykCMzWl19XZkxtVkYVi0Q3g==\n" +
            "-----END RSA PRIVATE KEY-----";

    @Test
    public void createJWT_is_valid() throws Exception {
        String jwt = createJWT("123", PKCS8_PRIVATE_KEY);
        Jws<Claims> parsedJwt = Jwts.parser()
                .setSigningKey(getPublicKeyFromString(PKCS8_PUBLIC_KEY))
                .parseClaimsJws(jwt);
        assertThat(parsedJwt.getBody().getIssuer(), is("123"));
    }

    @Test
    public void createJWT_with_pkcs1_is_invalid() {
        expectedException.expect(InvalidPrivateKeyException.class);
        expectedException.expectMessage(contains("openssl pkcs8 -topk8"));
        createJWT("123", PKCS1_PRIVATE_KEY);
    }

    @Test
    public void createJWT_with_not_base64_is_invalid() {
        expectedException.expect(InvalidPrivateKeyException.class);
        expectedException.expectMessage(contains("Failed to decode private key"));
        createJWT("123", "d£!@!@£!@£");
    }

    private static PublicKey getPublicKeyFromString(final String key) throws GeneralSecurityException {
        String publicKeyContent = key.replaceAll("\\n", "")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "");

        KeyFactory kf = KeyFactory.getInstance("RSA");

        X509EncodedKeySpec keySpecPKCS8 = new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyContent));

        return kf.generatePublic(keySpecPKCS8);
    }
}
