package dev.leilaalgarve.apikey.issuance;

import dev.leilaalgarve.apikey.CliArgs;
import dev.leilaalgarve.apikey.CliCommand;
import dev.leilaalgarve.apikey.core.ApiKey;
import dev.leilaalgarve.apikey.core.ApiKeyHasher;
import dev.leilaalgarve.apikey.core.ApiKeyRepository;
import dev.leilaalgarve.apikey.core.MissingHmacPepperException;
import java.io.PrintStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

/**
 * Orchestrates the `generate` command per contracts/cli-commands.md: generate -> hash ->
 * compute validity -> persist -> optionally schedule revocation of the client's existing
 * active keys (003-auto-revoke-on-rotation, --revoke-old-in-days) -> print the plaintext key
 * exactly once. Returns the exit code instead of calling System.exit itself, so every branch
 * (including the argument parsing) can be unit-tested without terminating the JVM -- see
 * ApiKeyCliRunner for the thin adapter that actually exits the process. Only called by
 * ApiKeyCliRunner when args[0] is "generate" -- this class no longer re-checks that itself
 * (see CliCommand's dispatch table).
 */
@Component
public class GenerateCommand implements CliCommand {

    private final ApiKeyGenerator generator;
    private final ApiKeyHasher hasher;
    private final ApiKeyRepository repository;
    private final OldKeyRotationPolicy rotationPolicy;

    public GenerateCommand(ApiKeyGenerator generator, ApiKeyHasher hasher, ApiKeyRepository repository,
            OldKeyRotationPolicy rotationPolicy) {
        this.generator = generator;
        this.hasher = hasher;
        this.repository = repository;
        this.rotationPolicy = rotationPolicy;
    }

    @Override
    @Transactional
    public int execute(String[] args, PrintStream out, PrintStream err) {
        String client = CliArgs.extractOption(args, "--client");
        if (client == null || client.isBlank()) {
            err.println(client == null
                    ? "Error: --client is required."
                    : "Error: --client must not be blank.");
            return 1;
        }

        String validityDaysRaw = CliArgs.extractOption(args, "--validity-days");
        Integer validityDays = null;
        if (validityDaysRaw != null) {
            validityDays = CliArgs.parsePositiveInt(validityDaysRaw);
            if (validityDays == null) {
                err.println("Error: --validity-days must be a positive integer.");
                return 1;
            }
        }

        String revokeOldInDaysRaw = CliArgs.extractOption(args, "--revoke-old-in-days");
        Integer revokeOldInDays = null;
        if (revokeOldInDaysRaw != null) {
            revokeOldInDays = CliArgs.parseNonNegativeInt(revokeOldInDaysRaw);
            if (revokeOldInDays == null) {
                err.println("Error: --revoke-old-in-days must be zero or a positive integer.");
                return 1;
            }
        }

        String plaintextKey = generator.generate();

        String keyHash;
        try {
            keyHash = hasher.hash(plaintextKey);
        } catch (MissingHmacPepperException e) {
            err.println("Error: HMAC pepper is not configured. Set the API_KEY_HMAC_PEPPER environment variable.");
            return 2;
        }

        // Rotates the client's *pre-existing* active keys before inserting the new one --
        // doing this the other way around would make the new key its own client_name match
        // and revoke itself the instant it's created.
        OldKeyRotationPolicy.Result rotationResult = new OldKeyRotationPolicy.Result(0, null);
        if (revokeOldInDays != null) {
            try {
                rotationResult = rotationPolicy.scheduleRevocationOfActiveKeys(client, revokeOldInDays);
            } catch (DataAccessException e) {
                // Nothing else has been persisted yet in this attempt -- a plain return is enough.
                err.println("Error: could not revoke the client's existing key(s). No key was printed.");
                return 3;
            }
        }

        Instant createdAt = Instant.now();
        Instant expiresAt = validityDays == null ? null : createdAt.plus(validityDays, ChronoUnit.DAYS);

        try {
            repository.save(new ApiKey(client, keyHash, createdAt, expiresAt));
        } catch (DataAccessException e) {
            if (rotationResult.updatedCount() > 0) {
                // The old keys above were already revoked in this same transaction -- roll
                // that back too (FR6/FR7 of 003-auto-revoke-on-rotation): the whole operation
                // is a unit.
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            }
            err.println("Error: could not save the generated key. No key was printed.");
            return 3;
        }

        String expiryPhrase = expiresAt == null ? "does not expire" : "expires in " + validityDays + " days";
        out.println("API key generated for client '" + client + "' (" + expiryPhrase + ").");
        out.println("This is the only time the plaintext key is shown — store it now:");
        out.println();
        out.println(plaintextKey);
        out.println();

        if (rotationResult.updatedCount() > 0) {
            int rotatedCount = rotationResult.updatedCount();
            String keyWord = rotatedCount == 1 ? "key" : "keys";
            if (revokeOldInDays == 0) {
                String verb = rotatedCount == 1 ? "has been revoked" : "have been revoked";
                out.println(rotatedCount + " existing " + keyWord + " for client '" + client + "' " + verb + ".");
            } else {
                out.println(rotatedCount + " existing " + keyWord + " for client '" + client
                        + "' scheduled for revocation in " + revokeOldInDays + " days (on "
                        + rotationResult.revokedAt() + ").");
            }
        }

        return 0;
    }
}
