package io.deployo.apikey.issuance;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * HMAC-SHA256 over the plaintext key, using a pepper kept out of the database and the
 * source tree (plan.md, "Algoritmo de hash da chave" / "Onde fica o pepper do HMAC").
 */
@Component
public class ApiKeyHasher {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final String pepper;

    public ApiKeyHasher(@Value("${API_KEY_HMAC_PEPPER:}") String pepper) {
        this.pepper = pepper;
    }

    public String hash(String plaintextKey) {
        if (pepper == null || pepper.isBlank()) {
            throw new MissingHmacPepperException();
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] digest = mac.doFinal(plaintextKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 is not available in this JVM", e);
        }
    }
}
