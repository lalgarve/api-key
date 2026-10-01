package io.deployo.apikey.issuance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

/** Pure unit tests for the derived isRevoked/isExpired checks -- no Spring context needed. */
class ApiKeyTest {

    private final Instant now = Instant.now();

    @Test
    void neverRevokedIsNotRevoked() {
        ApiKey key = new ApiKey("jogo-acoes", "hash", now, null);
        assertThat(key.isRevoked(now)).isFalse();
    }

    @Test
    void revocationInThePastIsRevoked() {
        ApiKey key = new ApiKey("jogo-acoes", "hash", now, null);
        key.revokeAt(now.minus(1, ChronoUnit.DAYS));
        assertThat(key.isRevoked(now)).isTrue();
    }

    @Test
    void revocationExactlyNowIsRevoked() {
        ApiKey key = new ApiKey("jogo-acoes", "hash", now, null);
        key.revokeAt(now);
        assertThat(key.isRevoked(now)).isTrue();
    }

    @Test
    void revocationInTheFutureIsNotYetRevoked() {
        ApiKey key = new ApiKey("jogo-acoes", "hash", now, null);
        key.revokeAt(now.plus(1, ChronoUnit.DAYS));
        assertThat(key.isRevoked(now)).isFalse();
    }

    @Test
    void noExpirationIsNeverExpired() {
        ApiKey key = new ApiKey("jogo-acoes", "hash", now, null);
        assertThat(key.isExpired(now)).isFalse();
    }

    @Test
    void expirationInThePastIsExpired() {
        ApiKey key = new ApiKey("jogo-acoes", "hash", now, now.minus(1, ChronoUnit.DAYS));
        assertThat(key.isExpired(now)).isTrue();
    }

    @Test
    void expirationInTheFutureIsNotYetExpired() {
        ApiKey key = new ApiKey("jogo-acoes", "hash", now, now.plus(1, ChronoUnit.DAYS));
        assertThat(key.isExpired(now)).isFalse();
    }
}
