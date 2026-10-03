package dev.leilaalgarve.apikey.issuance;

import dev.leilaalgarve.apikey.core.ApiKey;
import dev.leilaalgarve.apikey.core.ApiKeyRepository;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

import dev.leilaalgarve.apikey.DeployoApiKeyApplication;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Proves FR6/FR7 of 003-auto-revoke-on-rotation for real: if persisting the new key fails
 * *after* an existing key was already revoked in the same attempt, that revocation is rolled
 * back too -- nothing is left half-done. Needs a real transaction (deliberately no
 * @Transactional on this test class -- GenerateCommand's own @Transactional is the real,
 * top-level boundary here, the same one production code gets) and a spy rather than a full
 * mock, so the existing key's revocation genuinely happens against Postgres while only the new key's
 * insert is made to fail.
 */
@SpringBootTest(classes = DeployoApiKeyApplication.class, properties = "API_KEY_HMAC_PEPPER=test-pepper")
class GenerateCommandRotationAtomicityTest {

    @Autowired
    private GenerateCommand command;

    @MockitoSpyBean
    private ApiKeyRepository repository;

    @Test
    void newKeyFailureRollsBackTheOldKeysRevocationToo() {
        ApiKey existing = repository.save(
                new ApiKey("jogo-acoes-atomicity", "hash-atomicity-old", Instant.now(), null));
        Long existingId = existing.getId();
        // Only the brand-new key's insert (no id yet) is made to fail -- the pre-existing key's
        // update (id already set) goes through the spy to the real repository.
        doThrow(new DataAccessResourceFailureException("connection refused"))
                .when(repository).save(argThat(key -> key.getId() == null));

        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();

        int exitCode = command.execute(
                new String[] {"generate", "--client", "jogo-acoes-atomicity", "--revoke-old-in-days", "7"},
                new PrintStream(outBytes, true, StandardCharsets.UTF_8),
                new PrintStream(errBytes, true, StandardCharsets.UTF_8));

        assertThat(exitCode).isEqualTo(3);
        assertThat(errBytes.toString(StandardCharsets.UTF_8))
                .isEqualTo("Error: could not save the generated key. No key was printed.\n");
        assertThat(outBytes.toByteArray()).isEmpty();

        // The existing key's revocation (tentatively applied earlier in the same transaction)
        // was rolled back along with the failed insert -- not left half-applied.
        assertThat(repository.findById(existingId).orElseThrow().getRevokedAt()).isNull();

        repository.deleteById(existingId);
    }
}
