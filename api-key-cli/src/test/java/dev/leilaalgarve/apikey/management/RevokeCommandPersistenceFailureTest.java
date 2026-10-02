package dev.leilaalgarve.apikey.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.leilaalgarve.apikey.core.ApiKey;
import dev.leilaalgarve.apikey.core.ApiKeyRepository;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

/**
 * The one scenario that needs a stub: there's no real, reliable way to make an H2/Postgres
 * save() fail on demand (memory/constitution.md, "Testes: preferir real a fake"). The looked-up
 * key is real; only the repository's save() is mocked to fail.
 */
class RevokeCommandPersistenceFailureTest {

    @Test
    void persistenceFailureExitsFourWithoutChangingAnything() {
        ApiKey apiKey = new ApiKey("jogo-acoes", "hash-persist-fail", Instant.now(), null);
        ApiKeyRepository repository = mock(ApiKeyRepository.class);
        when(repository.findById(3L)).thenReturn(Optional.of(apiKey));
        when(repository.save(any())).thenThrow(new DataAccessResourceFailureException("connection refused"));

        RevokeCommand command = new RevokeCommand(repository);

        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();

        int exitCode = command.execute(
                new String[] {"revoke", "--id", "3"},
                new PrintStream(outBytes, true, StandardCharsets.UTF_8),
                new PrintStream(errBytes, true, StandardCharsets.UTF_8));

        assertThat(exitCode).isEqualTo(4);
        assertThat(errBytes.toString(StandardCharsets.UTF_8))
                .isEqualTo("Error: could not revoke the key. No change was saved.\n");
        assertThat(outBytes.toByteArray()).isEmpty();
    }
}
