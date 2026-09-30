package io.deployo.apikey.issuance;

import static org.assertj.core.api.Assertions.assertThat;

import io.deployo.apikey.DeployoApiKeyApplication;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Own test class (own Spring context, cached separately from GenerateCommandTest by its
 * distinct @SpringBootTest properties) specifically for the pepper-not-configured scenario --
 * needs API_KEY_HMAC_PEPPER unset/blank, which would break every other test in this class if
 * shared with them.
 */
@SpringBootTest(classes = DeployoApiKeyApplication.class,
        properties = "API_KEY_HMAC_PEPPER=")
@Transactional
class GenerateCommandMissingPepperTest {

    @Autowired
    private GenerateCommand command;

    @Autowired
    private ApiKeyRepository repository;

    @Test
    void missingPepperFailsWithoutPersistingOrPrinting() {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        long before = repository.count();

        int exitCode = command.execute(
                new String[] {"generate", "--client", "jogo-acoes"},
                new PrintStream(outBytes, true, StandardCharsets.UTF_8),
                new PrintStream(errBytes, true, StandardCharsets.UTF_8));

        assertThat(exitCode).isEqualTo(2);
        assertThat(errBytes.toString(StandardCharsets.UTF_8))
                .isEqualTo("Error: HMAC pepper is not configured. Set the API_KEY_HMAC_PEPPER environment variable.\n");
        assertThat(outBytes.toByteArray()).isEmpty();
        assertThat(repository.count()).isEqualTo(before);
    }
}
