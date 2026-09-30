package io.deployo.apikey.issuance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

/**
 * The one scenario that needs a stub: there's no real, reliable way to make an H2/Postgres
 * save() fail on demand (memory/constitution.md, "Testes: preferir real a fake" -- this is
 * exactly the "alternativa real não existe" exception). Generator and hasher stay real; only
 * the repository is mocked.
 */
class GenerateCommandPersistenceFailureTest {

    @Test
    void persistenceFailureExitsThreeWithoutPrintingTheKey() {
        ApiKeyRepository repository = mock(ApiKeyRepository.class);
        when(repository.save(any())).thenThrow(new DataAccessResourceFailureException("connection refused"));

        GenerateCommand command = new GenerateCommand(new ApiKeyGenerator(), new ApiKeyHasher("test-pepper"), repository);

        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();

        int exitCode = command.execute(
                new String[] {"generate", "--client", "jogo-acoes"},
                new PrintStream(outBytes, true, StandardCharsets.UTF_8),
                new PrintStream(errBytes, true, StandardCharsets.UTF_8));

        assertThat(exitCode).isEqualTo(3);
        assertThat(errBytes.toString(StandardCharsets.UTF_8))
                .isEqualTo("Error: could not save the generated key. No key was printed.\n");
        assertThat(outBytes.toByteArray()).isEmpty();
    }
}
