package dev.leilaalgarve.apikey.management;

import static org.assertj.core.api.Assertions.assertThat;

import dev.leilaalgarve.apikey.DeployoApiKeyApplication;
import dev.leilaalgarve.apikey.core.ApiKey;
import dev.leilaalgarve.apikey.core.ApiKeyHasher;
import dev.leilaalgarve.apikey.core.ApiKeyRepository;
import dev.leilaalgarve.apikey.core.PermissionCatalog;
import dev.leilaalgarve.apikey.core.PermissionCatalog.Entry;
import dev.leilaalgarve.apikey.issuance.ApiKeyGenerator;
import dev.leilaalgarve.apikey.validation.ApiKeyValidator;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every row of contracts/cli-commands.md's validate exit-code table, against the real
 * generator/hasher/repository and a fixed Clock (so expiry/revocation edges are exact) --
 * never a mocked ApiKeyValidator, since the whole point of this command is to answer exactly
 * what that library would (spec.md FR3).
 */
@SpringBootTest(classes = DeployoApiKeyApplication.class, properties = "API_KEY_HMAC_PEPPER=test-pepper")
@Transactional
class ValidateCommandTest {

    private static final Instant NOW = Instant.parse("2026-10-14T12:00:00Z");

    @Autowired
    private ApiKeyRepository repository;

    @Autowired
    private ApiKeyHasher hasher;

    @Autowired
    private ApiKeyGenerator generator;

    private ValidateCommand command;

    @BeforeEach
    void wireCommandWithAFixedClock() {
        command = new ValidateCommand(new ApiKeyValidator(repository, hasher, Clock.fixed(NOW, ZoneOffset.UTC)));
    }

    @Test
    void activeKeyIsValid() {
        String rawKey = issueKey("jogo-acoes", null, null);

        Result result = run(rawKey);

        assertThat(result.exitCode).isEqualTo(0);
        assertThat(result.out).isEqualTo("API key is valid for client 'jogo-acoes'.\n");
        assertThat(result.err).isEmpty();
    }

    @Test
    void emptyStdinIsMissing() {
        Result result = run("");

        assertThat(result.exitCode).isEqualTo(1);
        assertThat(result.err).isEqualTo("Error: no API key provided on stdin.\n");
        assertThat(result.out).isEmpty();
    }

    @Test
    void blankStdinIsMissing() {
        Result result = run("   ");

        assertThat(result.exitCode).isEqualTo(1);
        assertThat(result.err).isEqualTo("Error: no API key provided on stdin.\n");
    }

    @Test
    void malformedKeyIsRejected() {
        Result result = run("not-a-key");

        assertThat(result.exitCode).isEqualTo(2);
        assertThat(result.err).isEqualTo("Invalid: the key is malformed.\n");
    }

    @Test
    void keyThatWasNeverIssuedIsNotFound() {
        String neverSaved = generator.generate();

        Result result = run(neverSaved);

        assertThat(result.exitCode).isEqualTo(3);
        assertThat(result.err).isEqualTo("Invalid: no API key matches.\n");
    }

    @Test
    void revokedKeyIsRejected() {
        String rawKey = issueKey("jogo-acoes", null, NOW.minus(1, ChronoUnit.DAYS));

        Result result = run(rawKey);

        assertThat(result.exitCode).isEqualTo(4);
        assertThat(result.err).isEqualTo("Invalid: the key was revoked.\n");
    }

    @Test
    void expiredKeyIsRejected() {
        String rawKey = issueKey("jogo-acoes", NOW.minus(1, ChronoUnit.DAYS), null);

        Result result = run(rawKey);

        assertThat(result.exitCode).isEqualTo(5);
        assertThat(result.err).isEqualTo("Invalid: the key expired.\n");
    }

    @Test
    void keyBothExpiredAndRevokedIsReportedAsRevoked() {
        String rawKey = issueKey("jogo-acoes", NOW.minus(10, ChronoUnit.DAYS), NOW.minus(1, ChronoUnit.DAYS));

        Result result = run(rawKey);

        assertThat(result.exitCode).isEqualTo(4);
        assertThat(result.err).isEqualTo("Invalid: the key was revoked.\n");
    }

    @Test
    void keyWithARevocationScheduledLaterIsStillValid() {
        String rawKey = issueKey("jogo-acoes", null, NOW.plus(14, ChronoUnit.DAYS));

        Result result = run(rawKey);

        assertThat(result.exitCode).isEqualTo(0);
    }

    @Test
    void surroundingWhitespaceIsTrimmedAndOnlyTheFirstLineIsRead() {
        String rawKey = issueKey("jogo-acoes", null, null);

        Result result = run("  " + rawKey + "  \nignored second line");

        assertThat(result.exitCode).isEqualTo(0);
        assertThat(result.out).isEqualTo("API key is valid for client 'jogo-acoes'.\n");
    }

    @Test
    void neitherStdoutNorStderrEverContainsThePresentedKey() {
        String rawKey = issueKey("jogo-acoes", null, null);

        Result valid = run(rawKey);
        Result malformed = run("not-a-key-" + rawKey);

        assertThat(valid.out).doesNotContain(rawKey);
        assertThat(malformed.err).doesNotContain(rawKey);
    }

    private String issueKey(String client, Instant expiresAt, Instant revokedAt) {
        String rawKey = generator.generate();
        ApiKey apiKey = new ApiKey(client, hasher.hash(rawKey), NOW, expiresAt);
        if (revokedAt != null) {
            apiKey.revokeAt(revokedAt);
        }
        repository.save(apiKey);
        return rawKey;
    }

    private Result run(String stdin) {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        InputStream in = new ByteArrayInputStream(stdin.getBytes(StandardCharsets.UTF_8));

        int exitCode = command.execute(new String[] {"validate"}, in, printStream(outBytes), printStream(errBytes));

        return new Result(exitCode, out(outBytes), out(errBytes));
    }

    private record Result(int exitCode, String out, String err) {
    }

    private static PrintStream printStream(ByteArrayOutputStream bytes) {
        return new PrintStream(bytes, true, StandardCharsets.UTF_8);
    }

    private static String out(ByteArrayOutputStream bytes) {
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void validKeyWithPermissionsPrintsThemOnASecondLine() {
        String rawKey = generator.generate();
        repository.save(new ApiKey("email-admin", hasher.hash(rawKey), NOW.minus(1, ChronoUnit.DAYS), null,
                Set.of("email:send", "disposable-email:admin")));
        PermissionCatalog catalog = new PermissionCatalog(List.of(
                new Entry("email:send", true), new Entry("disposable-email:admin", false)));
        command = new ValidateCommand(new ApiKeyValidator(repository, hasher, Clock.fixed(NOW, ZoneOffset.UTC), catalog));

        Result result = run(rawKey);

        assertThat(result.exitCode).isEqualTo(0);
        assertThat(result.out).isEqualTo(
                "API key is valid for client 'email-admin'.\nPermissions: disposable-email:admin, email:send.\n");
    }
}
