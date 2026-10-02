package dev.leilaalgarve.apikey.core;

import java.util.regex.Pattern;

/**
 * Single source of truth for the plaintext key's shape: a fixed prefix followed by 256 bits of
 * entropy, base64url-encoded without padding. ApiKeyGenerator builds keys from it; the
 * validation library rejects anything that doesn't match it before hashing or querying
 * (specs/008-validate-api-key/spec.md FR4).
 */
public final class ApiKeyFormat {

    public static final String PREFIX = "dak_";
    public static final int ENTROPY_BYTES = 32;

    // 32 bytes -> ceil(32 * 4 / 3) = 43 base64url characters without padding.
    private static final int ENCODED_LENGTH = 43;
    private static final Pattern PATTERN =
            Pattern.compile(Pattern.quote(PREFIX) + "[A-Za-z0-9_-]{" + ENCODED_LENGTH + "}");

    private ApiKeyFormat() {
    }

    public static boolean matches(String candidate) {
        return candidate != null && PATTERN.matcher(candidate).matches();
    }
}
