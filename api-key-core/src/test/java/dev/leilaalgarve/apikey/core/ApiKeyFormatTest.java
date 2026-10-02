package dev.leilaalgarve.apikey.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class ApiKeyFormatTest {

    private static final String BODY_43 = "A".repeat(43);

    @Test
    void acceptsPrefixPlus43Base64UrlCharacters() {
        assertThat(ApiKeyFormat.matches("dak_" + BODY_43)).isTrue();
        assertThat(ApiKeyFormat.matches("dak_" + "aZ09-_".repeat(7) + "x")).isTrue();
    }

    @Test
    void acceptsTheEncodingOfEntropyBytesOfData() {
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(new byte[ApiKeyFormat.ENTROPY_BYTES]);

        assertThat(ApiKeyFormat.matches(ApiKeyFormat.PREFIX + encoded)).isTrue();
    }

    @Test
    void rejectsNull() {
        assertThat(ApiKeyFormat.matches(null)).isFalse();
    }

    @Test
    void rejectsMissingOrWrongPrefix() {
        assertThat(ApiKeyFormat.matches(BODY_43)).isFalse();
        assertThat(ApiKeyFormat.matches("sk_" + BODY_43)).isFalse();
        assertThat(ApiKeyFormat.matches("DAK_" + BODY_43)).isFalse();
    }

    @Test
    void rejectsWrongLength() {
        assertThat(ApiKeyFormat.matches("dak_" + "A".repeat(42))).isFalse();
        assertThat(ApiKeyFormat.matches("dak_" + "A".repeat(44))).isFalse();
    }

    @Test
    void rejectsCharactersOutsideBase64Url() {
        assertThat(ApiKeyFormat.matches("dak_" + "A".repeat(42) + "+")).isFalse();
        assertThat(ApiKeyFormat.matches("dak_" + "A".repeat(42) + "=")).isFalse();
        assertThat(ApiKeyFormat.matches("dak_" + BODY_43 + "\n")).isFalse();
        assertThat(ApiKeyFormat.matches(" dak_" + BODY_43)).isFalse();
    }
}
