package dev.leilaalgarve.apikey.management;

import static org.assertj.core.api.Assertions.assertThat;

import dev.leilaalgarve.apikey.DeployoApiKeyApplication;
import dev.leilaalgarve.apikey.issuance.ApiKey;
import dev.leilaalgarve.apikey.issuance.ApiKeyRepository;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exercises RevokeCommand end to end against the real repository/H2 database -- everything
 * except the persistence failure scenario, which needs a mocked repository
 * (RevokeCommandPersistenceFailureTest) since there's no real way to force the database to
 * fail here (same reasoning as GenerateCommandPersistenceFailureTest).
 */
@SpringBootTest(classes = DeployoApiKeyApplication.class)
@Transactional
class RevokeCommandTest {

    @Autowired
    private RevokeCommand command;

    @Autowired
    private ApiKeyRepository repository;

    @Test
    void revokesAKeyImmediately() {
        ApiKey saved = repository.save(new ApiKey("jogo-acoes", "hash-immediate", Instant.now(), null));
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();

        int exitCode = command.execute(
                new String[] {"revoke", "--id", saved.getId().toString()}, printStream(outBytes), printStream(new ByteArrayOutputStream()));

        assertThat(exitCode).isEqualTo(0);
        assertThat(out(outBytes)).isEqualTo("API key " + saved.getId() + " for client 'jogo-acoes' has been revoked.\n");

        ApiKey reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.isRevoked(Instant.now())).isTrue();
    }

    @Test
    void revokesAKeyWithAGracePeriod() {
        ApiKey saved = repository.save(new ApiKey("jogo-acoes", "hash-scheduled", Instant.now(), null));
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();

        int exitCode = command.execute(
                new String[] {"revoke", "--id", saved.getId().toString(), "--in-days", "14"},
                printStream(outBytes), printStream(new ByteArrayOutputStream()));

        assertThat(exitCode).isEqualTo(0);
        assertThat(out(outBytes)).contains("will be revoked in 14 days");

        ApiKey reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.isRevoked(Instant.now())).isFalse();
        assertThat(reloaded.isRevoked(Instant.now().plus(15, ChronoUnit.DAYS))).isTrue();
    }

    @Test
    void idIsRequired() {
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();

        int exitCode = command.execute(new String[] {"revoke"}, printStream(new ByteArrayOutputStream()), printStream(errBytes));

        assertThat(exitCode).isEqualTo(1);
        assertThat(out(errBytes)).isEqualTo("Error: --id is required.\n");
    }

    @Test
    void idMustBeANumber() {
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();

        int exitCode = command.execute(
                new String[] {"revoke", "--id", "not-a-number"}, printStream(new ByteArrayOutputStream()), printStream(errBytes));

        assertThat(exitCode).isEqualTo(1);
        assertThat(out(errBytes)).isEqualTo("Error: --id must be a number.\n");
    }

    @Test
    void keyNotFound() {
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();

        int exitCode = command.execute(
                new String[] {"revoke", "--id", "999999"}, printStream(new ByteArrayOutputStream()), printStream(errBytes));

        assertThat(exitCode).isEqualTo(2);
        assertThat(out(errBytes)).isEqualTo("Error: no API key found with id 999999.\n");
    }

    @Test
    void zeroInDaysFails() {
        assertInvalidInDays("0");
    }

    @Test
    void negativeInDaysFails() {
        assertInvalidInDays("-5");
    }

    @Test
    void nonNumericInDaysFails() {
        assertInvalidInDays("soon");
    }

    private void assertInvalidInDays(String value) {
        ApiKey saved = repository.save(new ApiKey("jogo-acoes", "hash-" + value + "-invalid", Instant.now(), null));
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();

        int exitCode = command.execute(
                new String[] {"revoke", "--id", saved.getId().toString(), "--in-days", value},
                printStream(new ByteArrayOutputStream()), printStream(errBytes));

        assertThat(exitCode).isEqualTo(1);
        assertThat(out(errBytes)).isEqualTo("Error: --in-days must be a positive integer.\n");
        assertThat(repository.findById(saved.getId()).orElseThrow().getRevokedAt()).isNull();
    }

    @Test
    void keyAlreadyRevokedFails() {
        ApiKey saved = repository.save(new ApiKey("jogo-acoes", "hash-already-revoked", Instant.now(), null));
        saved.revokeAt(Instant.now().minus(1, ChronoUnit.DAYS));
        repository.save(saved);
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();

        int exitCode = command.execute(
                new String[] {"revoke", "--id", saved.getId().toString()}, printStream(new ByteArrayOutputStream()), printStream(errBytes));

        assertThat(exitCode).isEqualTo(3);
        assertThat(out(errBytes)).isEqualTo("Error: API key " + saved.getId() + " is already revoked.\n");
    }

    @Test
    void keyAlreadyExpiredFails() {
        Instant createdAt = Instant.now().minus(100, ChronoUnit.DAYS);
        Instant expiresAt = Instant.now().minus(10, ChronoUnit.DAYS);
        ApiKey saved = repository.save(new ApiKey("jogo-acoes", "hash-already-expired", createdAt, expiresAt));
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();

        int exitCode = command.execute(
                new String[] {"revoke", "--id", saved.getId().toString()}, printStream(new ByteArrayOutputStream()), printStream(errBytes));

        assertThat(exitCode).isEqualTo(5);
        assertThat(out(errBytes)).contains("already expired");
        assertThat(repository.findById(saved.getId()).orElseThrow().getRevokedAt()).isNull();
    }

    @Test
    void inDaysPastExpirationFails() {
        Instant expiresAt = Instant.now().plus(10, ChronoUnit.DAYS);
        ApiKey saved = repository.save(new ApiKey("jogo-acoes", "hash-exceeds-expiry", Instant.now(), expiresAt));
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();

        int exitCode = command.execute(
                new String[] {"revoke", "--id", saved.getId().toString(), "--in-days", "20"},
                printStream(new ByteArrayOutputStream()), printStream(errBytes));

        assertThat(exitCode).isEqualTo(1);
        assertThat(out(errBytes)).contains("would schedule the revocation after the key already expires");
        assertThat(repository.findById(saved.getId()).orElseThrow().getRevokedAt()).isNull();
    }

    @Test
    void reschedulesAnExistingFutureRevocation() {
        ApiKey saved = repository.save(new ApiKey("jogo-acoes", "hash-reschedule", Instant.now(), null));
        saved.revokeAt(Instant.now().plus(30, ChronoUnit.DAYS));
        repository.save(saved);

        int exitCode = command.execute(
                new String[] {"revoke", "--id", saved.getId().toString(), "--in-days", "5"},
                printStream(new ByteArrayOutputStream()), printStream(new ByteArrayOutputStream()));

        assertThat(exitCode).isEqualTo(0);
        ApiKey reloaded = repository.findById(saved.getId()).orElseThrow();
        // rescheduled to ~5 days from now, not left at the original 30
        assertThat(reloaded.isRevoked(Instant.now().plus(4, ChronoUnit.DAYS))).isFalse();
        assertThat(reloaded.isRevoked(Instant.now().plus(6, ChronoUnit.DAYS))).isTrue();
    }

    private static PrintStream printStream(ByteArrayOutputStream bytes) {
        return new PrintStream(bytes, true, StandardCharsets.UTF_8);
    }

    private static String out(ByteArrayOutputStream bytes) {
        return bytes.toString(StandardCharsets.UTF_8);
    }
}
