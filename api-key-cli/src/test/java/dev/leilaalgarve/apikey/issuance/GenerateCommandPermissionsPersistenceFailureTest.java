package dev.leilaalgarve.apikey.issuance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.leilaalgarve.apikey.core.ApiKeyHasher;
import dev.leilaalgarve.apikey.core.ApiKeyRepository;
import dev.leilaalgarve.apikey.core.PermissionCatalog;
import dev.leilaalgarve.apikey.core.PermissionCatalog.Entry;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;
import org.springframework.dao.DataAccessResourceFailureException;

/**
 * The permission rows are written at flush, not at save -- a failure there must still exit 3
 * without printing the key (specs/013-key-permissions/spec.md FR8). Mocked for the same reason
 * as GenerateCommandPersistenceFailureTest: Postgres can't be made to fail on demand.
 */
class GenerateCommandPermissionsPersistenceFailureTest {

    @Test
    void aFailureWritingThePermissionsExitsThreeWithoutPrintingTheKey() {
        ApiKeyRepository repository = mock(ApiKeyRepository.class);
        when(repository.save(any())).then(AdditionalAnswers.returnsFirstArg());
        doThrow(new DataAccessResourceFailureException("connection refused")).when(repository).flush();
        PermissionCatalog catalog = new PermissionCatalog(List.of(new Entry("email:send", true)));

        GenerateCommand command = new GenerateCommand(new ApiKeyGenerator(), new ApiKeyHasher("test-pepper"),
                repository, new OldKeyRotationPolicy(repository), catalog);
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();

        int exitCode = command.execute(new String[] {"generate", "--client", "jogo-acoes"},
                new PrintStream(outBytes, true, StandardCharsets.UTF_8),
                new PrintStream(errBytes, true, StandardCharsets.UTF_8));

        assertThat(exitCode).isEqualTo(3);
        assertThat(errBytes.toString(StandardCharsets.UTF_8))
                .isEqualTo("Error: could not save the generated key. No key was printed.\n");
        assertThat(outBytes.toByteArray()).isEmpty();
    }
}
