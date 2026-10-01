package io.deployo.apikey.management;

import static org.assertj.core.api.Assertions.assertThat;

import io.deployo.apikey.DeployoApiKeyApplication;
import io.deployo.apikey.issuance.ApiKey;
import io.deployo.apikey.issuance.ApiKeyRepository;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = DeployoApiKeyApplication.class)
@Transactional
class ListCommandTest {

    @Autowired
    private ListCommand command;

    @Autowired
    private ApiKeyRepository repository;

    @Test
    void defaultsToActiveOnly() {
        ApiKey active = repository.save(new ApiKey("jogo-acoes-list-1", "hash-list-active", Instant.now(), null));
        ApiKey revoked = repository.save(new ApiKey("jogo-acoes-list-1", "hash-list-revoked", Instant.now(), null));
        revoked.revokeAt(Instant.now().minus(1, ChronoUnit.DAYS));
        repository.save(revoked);

        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        int exitCode = command.execute(
                new String[] {"list", "--client", "jogo-acoes-list-1"}, printStream(outBytes), printStream(new ByteArrayOutputStream()));

        assertThat(exitCode).isEqualTo(0);
        String[] lines = out(outBytes).split("\n");
        assertThat(lines).hasSize(2); // header + exactly the active row, not the revoked one too
        findRowWithId(lines, active.getId());
    }

    @Test
    void statusAllShowsEverything() {
        ApiKey active = repository.save(new ApiKey("jogo-acoes-list-2", "hash-all-active", Instant.now(), null));
        ApiKey revoked = repository.save(new ApiKey("jogo-acoes-list-2", "hash-all-revoked", Instant.now(), null));
        revoked.revokeAt(Instant.now().minus(1, ChronoUnit.DAYS));
        repository.save(revoked);

        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        int exitCode = command.execute(
                new String[] {"list", "--client", "jogo-acoes-list-2", "--status", "all"},
                printStream(outBytes), printStream(new ByteArrayOutputStream()));

        assertThat(exitCode).isEqualTo(0);
        String out = out(outBytes);
        assertThat(out).contains(active.getId().toString());
        assertThat(out).contains(revoked.getId().toString());
    }

    @Test
    void statusRevokedShowsOnlyRevoked() {
        repository.save(new ApiKey("jogo-acoes-list-3", "hash-revoked-filter-active", Instant.now(), null));
        ApiKey revoked = repository.save(new ApiKey("jogo-acoes-list-3", "hash-revoked-filter-revoked", Instant.now(), null));
        revoked.revokeAt(Instant.now().minus(1, ChronoUnit.DAYS));
        repository.save(revoked);

        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        int exitCode = command.execute(
                new String[] {"list", "--client", "jogo-acoes-list-3", "--status", "revoked"},
                printStream(outBytes), printStream(new ByteArrayOutputStream()));

        assertThat(exitCode).isEqualTo(0);
        String[] lines = out(outBytes).split("\n");
        assertThat(lines).hasSize(2); // header + exactly the revoked row, not the active one too
        assertThat(findRowWithId(lines, revoked.getId())).contains("revoked");
    }

    @Test
    void statusExpiredShowsOnlyExpiredAndNeverRevoked() {
        Instant createdAt = Instant.now().minus(100, ChronoUnit.DAYS);
        Instant expiresAt = Instant.now().minus(10, ChronoUnit.DAYS);
        ApiKey expired = repository.save(new ApiKey("jogo-acoes-list-4", "hash-expired", createdAt, expiresAt));
        repository.save(new ApiKey("jogo-acoes-list-4", "hash-expired-filter-active", Instant.now(), null));

        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        int exitCode = command.execute(
                new String[] {"list", "--client", "jogo-acoes-list-4", "--status", "expired"},
                printStream(outBytes), printStream(new ByteArrayOutputStream()));

        assertThat(exitCode).isEqualTo(0);
        String[] lines = out(outBytes).split("\n");
        assertThat(lines).hasSize(2); // header + exactly the expired row, not the active one too
        assertThat(findRowWithId(lines, expired.getId())).contains("expired");
    }

    @Test
    void aKeyThatIsBothExpiredAndRevokedShowsAsRevoked() {
        Instant createdAt = Instant.now().minus(100, ChronoUnit.DAYS);
        Instant expiresAt = Instant.now().minus(10, ChronoUnit.DAYS);
        ApiKey key = repository.save(new ApiKey("jogo-acoes-list-5", "hash-both", createdAt, expiresAt));
        key.revokeAt(expiresAt);
        repository.save(key);

        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        int exitCode = command.execute(
                new String[] {"list", "--client", "jogo-acoes-list-5", "--status", "all"},
                printStream(outBytes), printStream(new ByteArrayOutputStream()));

        assertThat(exitCode).isEqualTo(0);
        String[] lines = out(outBytes).split("\n");
        String row = findRowWithId(lines, key.getId());
        assertThat(row).contains("revoked");
        assertThat(row).doesNotContain(" expired");
    }

    @Test
    void revokingWithinDaysShowsOnlyKeysScheduledSoon() {
        ApiKey soon = repository.save(new ApiKey("jogo-acoes-list-6", "hash-soon", Instant.now(), null));
        soon.revokeAt(Instant.now().plus(10, ChronoUnit.DAYS));
        repository.save(soon);
        ApiKey later = repository.save(new ApiKey("jogo-acoes-list-6", "hash-later", Instant.now(), null));
        later.revokeAt(Instant.now().plus(90, ChronoUnit.DAYS));
        repository.save(later);

        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        int exitCode = command.execute(
                new String[] {"list", "--client", "jogo-acoes-list-6", "--revoking-within-days", "30"},
                printStream(outBytes), printStream(new ByteArrayOutputStream()));

        assertThat(exitCode).isEqualTo(0);
        String[] lines = out(outBytes).split("\n");
        assertThat(lines).hasSize(2); // header + exactly the "soon" row, not "later" too
        findRowWithId(lines, soon.getId());
    }

    @Test
    void revokingWithinDaysCombinedWithNonActiveStatusFails() {
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();

        int exitCode = command.execute(
                new String[] {"list", "--status", "revoked", "--revoking-within-days", "30"},
                printStream(new ByteArrayOutputStream()), printStream(errBytes));

        assertThat(exitCode).isEqualTo(1);
        assertThat(out(errBytes)).isEqualTo("Error: --revoking-within-days can only be used with --status active.\n");
    }

    @Test
    void invalidStatusFails() {
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();

        int exitCode = command.execute(
                new String[] {"list", "--status", "bogus"}, printStream(new ByteArrayOutputStream()), printStream(errBytes));

        assertThat(exitCode).isEqualTo(1);
        assertThat(out(errBytes)).isEqualTo("Error: --status must be one of: active, expired, revoked, all.\n");
    }

    @Test
    void invalidRevokingWithinDaysFails() {
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();

        int exitCode = command.execute(
                new String[] {"list", "--revoking-within-days", "0"}, printStream(new ByteArrayOutputStream()), printStream(errBytes));

        assertThat(exitCode).isEqualTo(1);
        assertThat(out(errBytes)).isEqualTo("Error: --revoking-within-days must be a positive integer.\n");
    }

    @Test
    void noMatchesPrintsAFriendlyMessageAndSucceeds() {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();

        int exitCode = command.execute(
                new String[] {"list", "--client", "no-such-client-at-all"},
                printStream(outBytes), printStream(new ByteArrayOutputStream()));

        assertThat(exitCode).isEqualTo(0);
        assertThat(out(outBytes)).isEqualTo("No API keys found for the given filters.\n");
    }

    @Test
    void outputNeverContainsTheKeyHash() {
        repository.save(new ApiKey("jogo-acoes-list-7", "super-secret-hash-value", Instant.now(), null));

        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        int exitCode = command.execute(
                new String[] {"list", "--client", "jogo-acoes-list-7", "--status", "all"},
                printStream(outBytes), printStream(new ByteArrayOutputStream()));

        assertThat(exitCode).isEqualTo(0);
        assertThat(out(outBytes)).doesNotContain("super-secret-hash-value");
    }

    private static String findRowWithId(String[] lines, Long id) {
        for (String line : lines) {
            if (line.trim().startsWith(id.toString() + " ")) {
                return line;
            }
        }
        throw new AssertionError("no row found for id " + id + " in: " + String.join("\n", lines));
    }

    private static PrintStream printStream(ByteArrayOutputStream bytes) {
        return new PrintStream(bytes, true, StandardCharsets.UTF_8);
    }

    private static String out(ByteArrayOutputStream bytes) {
        return bytes.toString(StandardCharsets.UTF_8);
    }
}
