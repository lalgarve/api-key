package io.deployo.apikey.issuance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ApiKeyHasherTest {

    @Test
    void hashIsHexEncodedSha256Length() {
        String hash = new ApiKeyHasher("some-pepper").hash("dak_plaintext");

        assertThat(hash).matches("[0-9a-f]{64}");
    }

    @Test
    void hashIsDeterministicForTheSameInputAndPepper() {
        ApiKeyHasher hasher = new ApiKeyHasher("some-pepper");

        assertThat(hasher.hash("dak_plaintext")).isEqualTo(hasher.hash("dak_plaintext"));
    }

    @Test
    void differentPlaintextKeysProduceDifferentHashes() {
        ApiKeyHasher hasher = new ApiKeyHasher("some-pepper");

        assertThat(hasher.hash("dak_one")).isNotEqualTo(hasher.hash("dak_two"));
    }

    @Test
    void differentPeppersProduceDifferentHashesForTheSameKey() {
        String hashWithPepperA = new ApiKeyHasher("pepper-a").hash("dak_plaintext");
        String hashWithPepperB = new ApiKeyHasher("pepper-b").hash("dak_plaintext");

        assertThat(hashWithPepperA).isNotEqualTo(hashWithPepperB);
    }

    @Test
    void nullPepperThrowsMissingHmacPepperException() {
        assertThatThrownBy(() -> new ApiKeyHasher(null).hash("dak_plaintext"))
                .isInstanceOf(MissingHmacPepperException.class);
    }

    @Test
    void blankPepperThrowsMissingHmacPepperException() {
        assertThatThrownBy(() -> new ApiKeyHasher("   ").hash("dak_plaintext"))
                .isInstanceOf(MissingHmacPepperException.class);
    }
}
