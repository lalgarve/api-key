package dev.leilaalgarve.apikey.issuance;

import static org.assertj.core.api.Assertions.assertThat;

import dev.leilaalgarve.apikey.core.ApiKeyFormat;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ApiKeyGeneratorTest {

    private final ApiKeyGenerator generator = new ApiKeyGenerator();

    @Test
    void generatedKeyHasTheExpectedPrefix() {
        String key = generator.generate();

        assertThat(key).startsWith(ApiKeyFormat.PREFIX);
    }

    @Test
    void generatedKeyCarries256BitsOfEntropy() {
        String key = generator.generate();
        String encodedPart = key.substring(ApiKeyFormat.PREFIX.length());

        byte[] decoded = Base64.getUrlDecoder().decode(encodedPart);

        assertThat(decoded).hasSize(32);
    }

    @Test
    void generatedKeysMatchTheFormatTheValidatorChecks() {
        for (int i = 0; i < 1_000; i++) {
            assertThat(ApiKeyFormat.matches(generator.generate())).isTrue();
        }
    }

    @Test
    void generatedKeysAreUnique() {
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            keys.add(generator.generate());
        }

        assertThat(keys).hasSize(10_000);
    }
}
