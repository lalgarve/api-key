package dev.leilaalgarve.apikey.core;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves the ApiKey entity mapping matches the real Flyway-managed schema (Hibernate
 * ddl-auto=validate would already fail context startup on a mismatch) and that a full
 * round-trip through Spring Data JPA works, not just direct JDBC (ApiKeysMigrationTest).
 */
@SpringBootTest
@Transactional
class ApiKeyRepositoryTest {

    @Autowired
    private ApiKeyRepository repository;

    @Autowired
    private EntityManager entityManager;

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

    @Test
    void revokedAtDefaultsToNullAndCanBeUpdated() {
        ApiKey saved = repository.save(new ApiKey(
                "jogo-acoes", "hash-3", Instant.now().truncatedTo(ChronoUnit.MICROS), null));
        assertThat(saved.getRevokedAt()).isNull();

        Instant revokedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        saved.revokeAt(revokedAt);
        repository.save(saved);

        ApiKey reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getRevokedAt()).isEqualTo(revokedAt);
    }

    @Test
    void findsAKeyByItsHash() {
        ApiKey saved = repository.save(new ApiKey(
                "jogo-acoes", "hash-lookup", Instant.now().truncatedTo(ChronoUnit.MICROS), null));
        repository.save(new ApiKey(
                "billing", "hash-other", Instant.now().truncatedTo(ChronoUnit.MICROS), null));

        assertThat(repository.findByKeyHash("hash-lookup"))
                .get()
                .extracting(ApiKey::getId, ApiKey::getClientName)
                .containsExactly(saved.getId(), "jogo-acoes");
    }

    @Test
    void findByKeyHashIsEmptyForAnUnknownHash() {
        assertThat(repository.findByKeyHash("never-issued")).isEmpty();
    }

    @Test
    void savesAndReloadsTheKeysPermissions() {
        ApiKey saved = repository.save(new ApiKey("email-admin", "hash-permissions",
                Instant.now().truncatedTo(ChronoUnit.MICROS), null, Set.of("email:send", "disposable-email:admin")));
        entityManager.flush();
        entityManager.clear();

        ApiKey reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getPermissions()).containsExactly("disposable-email:admin", "email:send");
    }

    @Test
    void aKeyCreatedWithoutPermissionsHasNone() {
        ApiKey saved = repository.save(new ApiKey(
                "jogo-acoes", "hash-no-permissions", Instant.now().truncatedTo(ChronoUnit.MICROS), null));
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findByKeyHash("hash-no-permissions").orElseThrow().getPermissions()).isEmpty();
        assertThat(saved.getPermissions()).isEmpty();
    }

    @Test
    void deletingAKeyDeletesItsPermissions() {
        ApiKey saved = repository.save(new ApiKey("email-admin", "hash-delete-permissions",
                Instant.now().truncatedTo(ChronoUnit.MICROS), null, Set.of("email:send")));
        entityManager.flush();

        repository.delete(saved);
        entityManager.flush();

        Number remaining = (Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM api_key_permissions WHERE api_key_id = :id")
                .setParameter("id", saved.getId())
                .getSingleResult();
        assertThat(remaining.intValue()).isZero();
    }

    @Test
    void permissionsCannotBeChangedThroughTheReturnedSet() {
        ApiKey key = new ApiKey("email-admin", "hash", Instant.now(), null, Set.of("email:send"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> key.getPermissions().add("disposable-email:admin"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
