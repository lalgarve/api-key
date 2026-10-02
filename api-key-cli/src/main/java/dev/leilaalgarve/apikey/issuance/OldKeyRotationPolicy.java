package dev.leilaalgarve.apikey.issuance;

import dev.leilaalgarve.apikey.core.ApiKey;
import dev.leilaalgarve.apikey.core.ApiKeyRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

/**
 * Schedules revocation of a client's currently-active keys when GenerateCommand is asked to
 * rotate (003-auto-revoke-on-rotation, --revoke-old-in-days). "Active" here means the same
 * thing as in 002-revoke-api-key: revokedAt null or still in the future. Never pushes back a
 * revocation that's already scheduled sooner than the new candidate (FR5).
 *
 * <p>Not a JPQL "clientName AND (revokedAt IS NULL OR revokedAt > now)" derived query on
 * purpose -- Spring Data parses "And"/"Or" left to right without grouping, so a method name
 * combining them that way would actually mean "(clientName AND revokedAtIsNull) OR
 * revokedAtGreaterThan", matching other clients' keys too. Fetching by client and filtering in
 * memory avoids that trap, and is cheap at this project's expected scale (plan.md).
 */
@Component
public class OldKeyRotationPolicy {

    private final ApiKeyRepository repository;

    public OldKeyRotationPolicy(ApiKeyRepository repository) {
        this.repository = repository;
    }

    /** How many of the client's active keys were actually rescheduled, and to which instant. */
    public record Result(int updatedCount, Instant revokedAt) {
    }

    public Result scheduleRevocationOfActiveKeys(String clientName, int revokeOldInDays) {
        Instant now = Instant.now();
        Instant candidate = now.plus(revokeOldInDays, ChronoUnit.DAYS);

        int updated = 0;
        for (ApiKey key : repository.findByClientName(clientName)) {
            if (key.isRevoked(now)) {
                continue;
            }
            if (key.getRevokedAt() == null || key.getRevokedAt().isAfter(candidate)) {
                key.revokeAt(candidate);
                repository.save(key);
                updated++;
            }
        }
        return new Result(updated, candidate);
    }
}
