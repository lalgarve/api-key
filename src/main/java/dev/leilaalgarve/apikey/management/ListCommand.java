package dev.leilaalgarve.apikey.management;

import dev.leilaalgarve.apikey.CliArgs;
import dev.leilaalgarve.apikey.CliCommand;
import dev.leilaalgarve.apikey.issuance.ApiKey;
import dev.leilaalgarve.apikey.issuance.ApiKeyRepository;
import java.io.PrintStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Orchestrates the `list` command per contracts/cli-commands.md: filter by a derived status
 * (active/expired/revoked/all -- never stored, computed from revokedAt/expiresAt at read time,
 * priority revoked > expired > active per 004-list-api-keys/plan.md), optionally by client and
 * by an upcoming-revocation window, then print a plain text table. Read-only; never exposes
 * key_hash or the plaintext key. Only called by ApiKeyCliRunner when args[0] is "list".
 */
@Component
public class ListCommand implements CliCommand {

    private static final List<String> VALID_STATUSES = List.of("active", "expired", "revoked", "all");

    private final ApiKeyRepository repository;

    public ListCommand(ApiKeyRepository repository) {
        this.repository = repository;
    }

    @Override
    public int execute(String[] args, PrintStream out, PrintStream err) {
        String status = CliArgs.extractOption(args, "--status");
        if (status == null) {
            status = "active";
        } else if (!VALID_STATUSES.contains(status)) {
            err.println("Error: --status must be one of: active, expired, revoked, all.");
            return 1;
        }

        String client = CliArgs.extractOption(args, "--client");

        String revokingWithinDaysRaw = CliArgs.extractOption(args, "--revoking-within-days");
        Integer revokingWithinDays = null;
        if (revokingWithinDaysRaw != null) {
            if (!"active".equals(status)) {
                err.println("Error: --revoking-within-days can only be used with --status active.");
                return 1;
            }
            revokingWithinDays = CliArgs.parsePositiveInt(revokingWithinDaysRaw);
            if (revokingWithinDays == null) {
                err.println("Error: --revoking-within-days must be a positive integer.");
                return 1;
            }
        }

        Instant now = Instant.now();
        List<ApiKey> candidates = client == null ? repository.findAll() : repository.findByClientName(client);

        List<ApiKey> matching = new ArrayList<>();
        for (ApiKey key : candidates) {
            Status derived = Status.of(key, now);
            if (!"all".equals(status) && !derived.label().equals(status)) {
                continue;
            }
            if (revokingWithinDays != null && !isRevokingWithin(key, now, revokingWithinDays)) {
                continue;
            }
            matching.add(key);
        }
        matching.sort(Comparator.comparing(ApiKey::getCreatedAt));

        if (matching.isEmpty()) {
            out.println("No API keys found for the given filters.");
            return 0;
        }

        printTable(out, matching, now);
        return 0;
    }

    private static boolean isRevokingWithin(ApiKey key, Instant now, int days) {
        Instant revokedAt = key.getRevokedAt();
        return revokedAt != null && revokedAt.isAfter(now) && !revokedAt.isAfter(now.plus(days, ChronoUnit.DAYS));
    }

    private static void printTable(PrintStream out, List<ApiKey> keys, Instant now) {
        String[] header = {"ID", "CLIENT", "CREATED_AT", "EXPIRES_AT", "REVOKED_AT", "STATUS"};
        List<String[]> rows = new ArrayList<>();
        rows.add(header);
        for (ApiKey key : keys) {
            rows.add(new String[] {
                    String.valueOf(key.getId()),
                    key.getClientName(),
                    String.valueOf(key.getCreatedAt()),
                    key.getExpiresAt() == null ? "-" : key.getExpiresAt().toString(),
                    key.getRevokedAt() == null ? "-" : key.getRevokedAt().toString(),
                    Status.of(key, now).label()
            });
        }

        int columns = header.length;
        int[] widths = new int[columns];
        for (String[] row : rows) {
            for (int i = 0; i < columns; i++) {
                widths[i] = Math.max(widths[i], row[i].length());
            }
        }

        for (String[] row : rows) {
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < columns; i++) {
                line.append(pad(row[i], widths[i]));
                if (i < columns - 1) {
                    line.append("  ");
                }
            }
            out.println(line.toString().stripTrailing());
        }
    }

    private static String pad(String value, int width) {
        return String.format(Locale.ROOT, "%-" + width + "s", value);
    }

    private enum Status {
        ACTIVE, EXPIRED, REVOKED;

        static Status of(ApiKey key, Instant now) {
            if (key.isRevoked(now)) {
                return REVOKED;
            }
            if (key.isExpired(now)) {
                return EXPIRED;
            }
            return ACTIVE;
        }

        String label() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
