package io.deployo.apikey.issuance;

import java.io.PrintStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * Orchestrates the `generate` command per contracts/cli-commands.md: generate -> hash ->
 * compute validity -> persist -> print the plaintext key exactly once. Returns the exit code
 * instead of calling System.exit itself, so every branch (including the argument parsing) can
 * be unit-tested without terminating the JVM -- see ApiKeyCliRunner for the thin adapter that
 * actually exits the process.
 */
@Component
public class GenerateCommand {

    private final ApiKeyGenerator generator;
    private final ApiKeyHasher hasher;
    private final ApiKeyRepository repository;

    public GenerateCommand(ApiKeyGenerator generator, ApiKeyHasher hasher, ApiKeyRepository repository) {
        this.generator = generator;
        this.hasher = hasher;
        this.repository = repository;
    }

    public int execute(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 0 || !"generate".equals(args[0])) {
            return 0;
        }

        String client = extractOption(args, "--client");
        if (client == null || client.isBlank()) {
            err.println(client == null
                    ? "Error: --client is required."
                    : "Error: --client must not be blank.");
            return 1;
        }

        String validityDaysRaw = extractOption(args, "--validity-days");
        Integer validityDays = null;
        if (validityDaysRaw != null) {
            validityDays = parsePositiveInt(validityDaysRaw);
            if (validityDays == null) {
                err.println("Error: --validity-days must be a positive integer.");
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

        Instant createdAt = Instant.now();
        Instant expiresAt = validityDays == null ? null : createdAt.plus(validityDays, ChronoUnit.DAYS);

        try {
            repository.save(new ApiKey(client, keyHash, createdAt, expiresAt));
        } catch (DataAccessException e) {
            err.println("Error: could not save the generated key. No key was printed.");
            return 3;
        }

        String expiryPhrase = expiresAt == null ? "does not expire" : "expires in " + validityDays + " days";
        out.println("API key generated for client '" + client + "' (" + expiryPhrase + ").");
        out.println("This is the only time the plaintext key is shown — store it now:");
        out.println();
        out.println(plaintextKey);
        out.println();

        return 0;
    }

    private static Integer parsePositiveInt(String raw) {
        try {
            int value = Integer.parseInt(raw.trim());
            return value > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String extractOption(String[] args, String flag) {
        for (int i = 1; i < args.length - 1; i++) {
            if (flag.equals(args[i])) {
                return args[i + 1];
            }
        }
        return null;
    }
}
