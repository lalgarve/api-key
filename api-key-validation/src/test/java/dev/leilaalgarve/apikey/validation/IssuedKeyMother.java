package dev.leilaalgarve.apikey.validation;

import dev.leilaalgarve.apikey.core.ApiKey;
import dev.leilaalgarve.apikey.core.ApiKeyFormat;
import dev.leilaalgarve.apikey.core.ApiKeyHasher;
import dev.leilaalgarve.apikey.core.ApiKeyRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

/**
 * Object Mother + Test Data Builder (memory/constitution.md) for keys as the CLI would have
 * issued them: a well-formed plaintext key plus its row in api_keys. {@link #activeKey} is valid
 * by default -- active, issued a day before {@code now}, expiring in 30 days -- and each test
 * overrides only the field it is about.
 */
public final class IssuedKeyMother {

    private static final SecureRandom RANDOM = new SecureRandom();

    private IssuedKeyMother() {
    }

    public static Builder activeKey(Instant now) {
        return new Builder(now);
    }

    /** A plaintext key in the exact generated shape, never persisted. */
    public static String wellFormedRawKey() {
        byte[] bytes = new byte[ApiKeyFormat.ENTROPY_BYTES];
        RANDOM.nextBytes(bytes);
        return ApiKeyFormat.PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** The plaintext key a caller would present, and the row it was issued as. */
    public record IssuedKey(String rawKey, ApiKey row) {
    }

    public static final class Builder {

        private String clientName = "jogo-acoes";
        private Instant createdAt;
        private Instant expiresAt;
        private Instant revokedAt;

        private Builder(Instant now) {
            this.createdAt = now.minus(1, ChronoUnit.DAYS);
            this.expiresAt = now.plus(30, ChronoUnit.DAYS);
        }

        public Builder forClient(String clientName) {
            this.clientName = clientName;
            return this;
        }

        public Builder expiringAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public Builder neverExpiring() {
            return expiringAt(null);
        }

        public Builder revokedAt(Instant revokedAt) {
            this.revokedAt = revokedAt;
            return this;
        }

        public IssuedKey saveWith(ApiKeyRepository repository, ApiKeyHasher hasher) {
            String rawKey = wellFormedRawKey();
            ApiKey row = new ApiKey(clientName, hasher.hash(rawKey), createdAt, expiresAt);
            if (revokedAt != null) {
                row.revokeAt(revokedAt);
            }
            return new IssuedKey(rawKey, repository.save(row));
        }
    }
}
