package dev.leilaalgarve.apikey.issuance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;

import dev.leilaalgarve.apikey.DeployoApiKeyApplication;
import dev.leilaalgarve.apikey.core.ApiKeyRepository;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * The key's row is inserted at save (IDENTITY), before the flush that writes its permissions.
 * If that flush fails, the whole transaction must roll back -- with or without a rotation --
 * so no key is left in the database that was never printed (specs/013-key-permissions FR8).
 * Real transaction (no @Transactional here), spy repository: same setup and reasons as
 * GenerateCommandRotationAtomicityTest.
 */
@SpringBootTest(classes = DeployoApiKeyApplication.class, properties = "API_KEY_HMAC_PEPPER=test-pepper")
class GenerateCommandFlushFailureTest {

    private static final String CLIENT = "jogo-acoes-flush-failure";

    @Autowired
    private GenerateCommand command;

    @MockitoSpyBean
    private ApiKeyRepository repository;

    @Test
    void aFailedFlushRollsBackTheAlreadyInsertedKey() {
        doThrow(new DataAccessResourceFailureException("connection refused")).when(repository).flush();

        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        int exitCode = command.execute(new String[] {"generate", "--client", CLIENT},
                new PrintStream(outBytes, true, StandardCharsets.UTF_8),
                new PrintStream(errBytes, true, StandardCharsets.UTF_8));

        try {
            assertThat(exitCode).isEqualTo(3);
            assertThat(errBytes.toString(StandardCharsets.UTF_8))
                    .isEqualTo("Error: could not save the generated key. No key was printed.\n");
            assertThat(outBytes.toByteArray()).isEmpty();
            assertThat(repository.findByClientName(CLIENT)).isEmpty();
        } finally {
            repository.deleteAll(repository.findByClientName(CLIENT));
        }
    }
}
