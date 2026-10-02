package dev.leilaalgarve.apikey.validation;

import dev.leilaalgarve.apikey.core.ApiKey;
import dev.leilaalgarve.apikey.core.ApiKeyFormat;
import dev.leilaalgarve.apikey.core.ApiKeyHasher;
import dev.leilaalgarve.apikey.core.ApiKeyRepository;
import dev.leilaalgarve.apikey.validation.ApiKeyValidationResult.Invalid;
import dev.leilaalgarve.apikey.validation.ApiKeyValidationResult.Valid;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Checks a plaintext API key presented by a caller against the keys issued by the CLI
 * (specs/008-validate-api-key/contracts/validation-api.md). Rejections are results, not
 * exceptions; only infrastructure failures (database down, missing pepper) throw.
 *
 * <p>Deliberately logs nothing: the plaintext key must never reach a log (spec.md FR6), and
 * what to log or answer for each outcome is the consuming service's call (FR7).
 */
@Component
public class ApiKeyValidator {

    private final ApiKeyRepository repository;
    private final ApiKeyHasher hasher;
    private final Clock clock;

    public ApiKeyValidator(ApiKeyRepository repository, ApiKeyHasher hasher, Clock clock) {
        this.repository = repository;
        this.hasher = hasher;
        this.clock = clock;
    }

    /**
     * Uses the consuming service's {@link Clock} bean when it has one, else the system UTC
     * clock -- the library registers no Clock bean itself, so it never clashes with the
     * consumer's (plan.md, "Fonte do 'agora' para revogação/expiração").
     */
    @Autowired
    public ApiKeyValidator(ApiKeyRepository repository, ApiKeyHasher hasher, ObjectProvider<Clock> clock) {
        this(repository, hasher, clock.getIfAvailable(Clock::systemUTC));
    }

    public ApiKeyValidationResult validate(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return new Invalid(ApiKeyFailureReason.MISSING);
        }
        if (!ApiKeyFormat.matches(rawKey)) {
            return new Invalid(ApiKeyFailureReason.MALFORMED);
        }

        Optional<ApiKey> found = repository.findByKeyHash(hasher.hash(rawKey));
        if (found.isEmpty()) {
            return new Invalid(ApiKeyFailureReason.NOT_FOUND);
        }

        ApiKey key = found.get();
        Instant now = clock.instant();
        // Revoked before expired: same precedence as specs/004-list-api-keys FR1.
        if (key.isRevoked(now)) {
            return new Invalid(ApiKeyFailureReason.REVOKED);
        }
        if (key.isExpired(now)) {
            return new Invalid(ApiKeyFailureReason.EXPIRED);
        }
        return new Valid(key.getClientName());
    }
}
