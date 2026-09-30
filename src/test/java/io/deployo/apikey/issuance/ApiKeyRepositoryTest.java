package io.deployo.apikey.issuance;

import static org.assertj.core.api.Assertions.assertThat;

import io.deployo.apikey.DeployoApiKeyApplication;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves the ApiKey entity mapping matches the real Flyway-managed schema (Hibernate
 * ddl-auto=validate would already fail context startup on a mismatch) and that a full
 * round-trip through Spring Data JPA works, not just direct JDBC (ApiKeysMigrationTest).
 */
@SpringBootTest(classes = DeployoApiKeyApplication.class)
@Transactional
class ApiKeyRepositoryTest {

    @Autowired
    private ApiKeyRepository repository;

    @Test
    void savesAndReloadsAnApiKeyWithValidity() {
        Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant expiresAt = createdAt.plus(90, ChronoUnit.DAYS);

        ApiKey saved = repository.save(new ApiKey("jogo-acoes", "hash-1", createdAt, expiresAt));

        ApiKey reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getClientName()).isEqualTo("jogo-acoes");
        assertThat(reloaded.getKeyHash()).isEqualTo("hash-1");
        assertThat(reloaded.getCreatedAt()).isEqualTo(createdAt);
        assertThat(reloaded.getExpiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void savesAnApiKeyWithoutValidity() {
        ApiKey saved = repository.save(new ApiKey(
                "jogo-acoes", "hash-2", Instant.now().truncatedTo(ChronoUnit.MICROS), null));

        ApiKey reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getExpiresAt()).isNull();
    }
}
