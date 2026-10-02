package dev.leilaalgarve.apikey.management;

import dev.leilaalgarve.apikey.CliArgs;
import dev.leilaalgarve.apikey.CliCommand;
import dev.leilaalgarve.apikey.CliOutput;
import dev.leilaalgarve.apikey.core.ApiKey;
import dev.leilaalgarve.apikey.core.ApiKeyRepository;
import java.io.PrintStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * Orchestrates the `revoke` command per contracts/cli-commands.md: look up by --id, reject an
 * already-revoked or already-expired key, compute the revocation moment (now, or now + N days
 * via --in-days) rejecting one that would land after the key's own expiration, persist, and
 * report it. Only called by ApiKeyCliRunner when args[0] is "revoke" -- see GenerateCommand for
 * why this returns an exit code instead of calling System.exit itself.
 */
@Component
public class RevokeCommand implements CliCommand {

    private final ApiKeyRepository repository;

    public RevokeCommand(ApiKeyRepository repository) {
        this.repository = repository;
    }

    @Override
    public int execute(String[] args, PrintStream out, PrintStream err) {
        String idRaw = CliArgs.extractOption(args, "--id");
        if (idRaw == null) {
            CliOutput.println(err, "Error: --id is required.");
            return 1;
        }
        Long id = CliArgs.parseLong(idRaw);
        if (id == null) {
            CliOutput.println(err, "Error: --id must be a number.");
            return 1;
        }

        String inDaysRaw = CliArgs.extractOption(args, "--in-days");
        Integer inDays = null;
        if (inDaysRaw != null) {
            inDays = CliArgs.parsePositiveInt(inDaysRaw);
            if (inDays == null) {
                CliOutput.println(err, "Error: --in-days must be a positive integer.");
                return 1;
            }
        }

        Optional<ApiKey> found = repository.findById(id);
        if (found.isEmpty()) {
            CliOutput.println(err, "Error: no API key found with id " + id + ".");
            return 2;
        }
        ApiKey apiKey = found.get();

        Instant now = Instant.now();
        if (apiKey.isRevoked(now)) {
            CliOutput.println(err, "Error: API key " + id + " is already revoked.");
            return 3;
        }
        if (apiKey.isExpired(now)) {
            CliOutput.println(err, "Error: API key " + id + " already expired on " + apiKey.getExpiresAt()
                    + "; nothing to revoke.");
            return 5;
        }

        Instant revokedAt = inDays == null ? now : now.plus(inDays, ChronoUnit.DAYS);
        Instant expiresAt = apiKey.getExpiresAt();
        if (expiresAt != null && revokedAt.isAfter(expiresAt)) {
            CliOutput.println(err, "Error: --in-days would schedule the revocation after the key already expires (expires at "
                    + expiresAt + ").");
            return 1;
        }

        apiKey.revokeAt(revokedAt);
        try {
            repository.save(apiKey);
        } catch (DataAccessException e) {
            CliOutput.println(err, "Error: could not revoke the key. No change was saved.");
            return 4;
        }

        if (inDays == null) {
            CliOutput.println(out, "API key " + id + " for client '" + apiKey.getClientName() + "' has been revoked.");
        } else {
            CliOutput.println(out, "API key " + id + " for client '" + apiKey.getClientName() + "' will be revoked in "
                    + inDays + " days (on " + revokedAt + ").");
        }
        return 0;
    }
}
