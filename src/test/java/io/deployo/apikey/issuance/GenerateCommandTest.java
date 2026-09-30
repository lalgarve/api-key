package io.deployo.apikey.issuance;

import static org.assertj.core.api.Assertions.assertThat;

import io.deployo.apikey.DeployoApiKeyApplication;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exercises GenerateCommand end to end against the real generator, hasher (real pepper via
 * property override below) and repository/H2 database -- everything except the persistence
 * failure scenario, which needs a mocked repository (GenerateCommandPersistenceFailureTest)
 * since there's no real way to force the database to fail here.
 */
@SpringBootTest(classes = DeployoApiKeyApplication.class,
        properties = "API_KEY_HMAC_PEPPER=test-pepper")
@Transactional
class GenerateCommandTest {

    @Autowired
    private GenerateCommand command;

    @Autowired
    private ApiKeyRepository repository;

    @Test
    void generatesAndPersistsAKeyWithValidity() {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();

        int exitCode = command.execute(
                new String[] {"generate", "--client", "jogo-acoes", "--validity-days", "90"},
                printStream(outBytes), printStream(errBytes));

        assertThat(exitCode).isEqualTo(0);
        assertThat(errBytes.toByteArray()).isEmpty();

        String out = out(outBytes);
        assertThat(out).contains("API key generated for client 'jogo-acoes' (expires in 90 days).");
        assertThat(out).containsPattern("dak_[A-Za-z0-9_-]+");

        List<ApiKey> saved = repository.findAll();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getClientName()).isEqualTo("jogo-acoes");
        assertThat(saved.get(0).getExpiresAt()).isNotNull();
        // the plaintext key is never persisted -- only its hash, and it isn't the printed key
        assertThat(out).doesNotContain(saved.get(0).getKeyHash());
    }

    @Test
    void generatesAndPersistsAKeyWithoutValidity() {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();

        int exitCode = command.execute(
                new String[] {"generate", "--client", "jogo-acoes"}, printStream(outBytes), printStream(new ByteArrayOutputStream()));

        assertThat(exitCode).isEqualTo(0);
        assertThat(out(outBytes)).contains("API key generated for client 'jogo-acoes' (does not expire).");

        List<ApiKey> saved = repository.findAll();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getExpiresAt()).isNull();
    }

    @Test
    void missingClientFailsWithoutPersistingOrPrinting() {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        long before = repository.count();

        int exitCode = command.execute(new String[] {"generate"}, printStream(outBytes), printStream(errBytes));

        assertThat(exitCode).isEqualTo(1);
        assertThat(out(errBytes)).isEqualTo("Error: --client is required.\n");
        assertThat(outBytes.toByteArray()).isEmpty();
        assertThat(repository.count()).isEqualTo(before);
    }

    @Test
    void blankClientFailsWithoutPersistingOrPrinting() {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        long before = repository.count();

        int exitCode = command.execute(
                new String[] {"generate", "--client", "   "}, printStream(outBytes), printStream(errBytes));

        assertThat(exitCode).isEqualTo(1);
        assertThat(out(errBytes)).isEqualTo("Error: --client must not be blank.\n");
        assertThat(outBytes.toByteArray()).isEmpty();
        assertThat(repository.count()).isEqualTo(before);
    }

    @Test
    void zeroValidityDaysFailsWithoutPersistingOrPrinting() {
        assertInvalidValidityDays("0");
    }

    @Test
    void negativeValidityDaysFailsWithoutPersistingOrPrinting() {
        assertInvalidValidityDays("-5");
    }

    @Test
    void nonNumericValidityDaysFailsWithoutPersistingOrPrinting() {
        assertInvalidValidityDays("soon");
    }

    private void assertInvalidValidityDays(String value) {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        long before = repository.count();

        int exitCode = command.execute(
                new String[] {"generate", "--client", "jogo-acoes", "--validity-days", value},
                printStream(outBytes), printStream(errBytes));

        assertThat(exitCode).isEqualTo(1);
        assertThat(out(errBytes)).isEqualTo("Error: --validity-days must be a positive integer.\n");
        assertThat(outBytes.toByteArray()).isEmpty();
        assertThat(repository.count()).isEqualTo(before);
    }

    @Test
    void withoutTheGenerateCommandWordItDoesNothing() {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        long before = repository.count();

        int exitCode = command.execute(new String[] {"--spring.datasource.url=jdbc:h2:mem:unused"},
                printStream(outBytes), printStream(new ByteArrayOutputStream()));

        assertThat(exitCode).isEqualTo(0);
        assertThat(outBytes.toByteArray()).isEmpty();
        assertThat(repository.count()).isEqualTo(before);
    }

    private static PrintStream printStream(ByteArrayOutputStream bytes) {
        return new PrintStream(bytes, true, StandardCharsets.UTF_8);
    }

    private static String out(ByteArrayOutputStream bytes) {
        return bytes.toString(StandardCharsets.UTF_8);
    }
}
