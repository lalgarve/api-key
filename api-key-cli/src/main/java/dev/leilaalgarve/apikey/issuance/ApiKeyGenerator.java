package dev.leilaalgarve.apikey.issuance;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * Generates plaintext API keys: a fixed prefix (memory/constitution.md, plan.md) followed by
 * 256 bits of random entropy, base64url-encoded without padding so the result is safe to use
 * as-is in headers, URLs and logs (well, it never belongs in a log -- see spec.md FR "a chave
 * em texto puro nunca aparece em log de aplicação").
 */
@Component
public class ApiKeyGenerator {

    static final String PREFIX = "dak_";
    private static final int KEY_ENTROPY_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        byte[] randomBytes = new byte[KEY_ENTROPY_BYTES];
        secureRandom.nextBytes(randomBytes);
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
