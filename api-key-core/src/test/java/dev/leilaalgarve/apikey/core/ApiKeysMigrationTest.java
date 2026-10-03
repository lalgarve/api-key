package dev.leilaalgarve.apikey.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves the Flyway migration in data-model.md actually applies and enforces its invariants
 * -- run against real PostgreSQL (see src/test/resources/application.yml). No ApiKey JPA
 * entity exists yet (that's T005); this only exercises the raw table via JdbcTemplate.
 *
 * Each test runs in its own transaction, rolled back afterwards -- no manual cleanup needed
 * between tests even though the Spring context (and its database) is reused across the class.
 */
@SpringBootTest
@Transactional
class ApiKeysMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void insertsARowWithAllColumns() {
        Timestamp createdAt = Timestamp.from(Instant.now());
        Timestamp expiresAt = Timestamp.from(Instant.now().plusSeconds(90L * 24 * 60 * 60));

        jdbcTemplate.update(
                "INSERT INTO api_keys (client_name, key_hash, created_at, expires_at) VALUES (?, ?, ?, ?)",
                "jogo-acoes", "hash-with-validity", createdAt, expiresAt);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM api_keys WHERE client_name = ? AND key_hash = ?",
                Integer.class, "jogo-acoes", "hash-with-validity");
        assertThat(count).isEqualTo(1);
    }

    @Test
    void expiresAtIsNullableForKeysWithIndeterminateValidity() {
        jdbcTemplate.update(
                "INSERT INTO api_keys (client_name, key_hash, created_at, expires_at) VALUES (?, ?, ?, NULL)",
                "jogo-acoes", "hash-without-validity", Timestamp.from(Instant.now()));

        Timestamp expiresAt = jdbcTemplate.queryForObject(
                "SELECT expires_at FROM api_keys WHERE key_hash = ?",
                Timestamp.class, "hash-without-validity");
        assertThat(expiresAt).isNull();
    }

    @Test
    void clientNameCannotBeNull() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO api_keys (client_name, key_hash, created_at) VALUES (NULL, ?, ?)",
                "some-hash", Timestamp.from(Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void keyHashCannotBeNull() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO api_keys (client_name, key_hash, created_at) VALUES (?, NULL, ?)",
                "jogo-acoes", Timestamp.from(Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void createdAtCannotBeNull() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO api_keys (client_name, key_hash, created_at) VALUES (?, ?, NULL)",
                "jogo-acoes", "some-hash"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void keyHashMustBeUnique() {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "INSERT INTO api_keys (client_name, key_hash, created_at) VALUES (?, ?, ?)",
                "jogo-acoes", "duplicate-hash", now);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO api_keys (client_name, key_hash, created_at) VALUES (?, ?, ?)",
                "billing", "duplicate-hash", now))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void revokedAtIsNullableAndDefaultsToNull() {
        jdbcTemplate.update(
                "INSERT INTO api_keys (client_name, key_hash, created_at) VALUES (?, ?, ?)",
                "jogo-acoes", "hash-no-revocation", Timestamp.from(Instant.now()));

        Timestamp revokedAt = jdbcTemplate.queryForObject(
                "SELECT revoked_at FROM api_keys WHERE key_hash = ?", Timestamp.class, "hash-no-revocation");
        assertThat(revokedAt).isNull();
    }

    @Test
    void revokedAtCanBeSetAfterInsert() {
        Timestamp createdAt = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "INSERT INTO api_keys (client_name, key_hash, created_at) VALUES (?, ?, ?)",
                "jogo-acoes", "hash-to-revoke", createdAt);

        Timestamp revokedAt = Timestamp.from(Instant.now().truncatedTo(ChronoUnit.MICROS));
        jdbcTemplate.update("UPDATE api_keys SET revoked_at = ? WHERE key_hash = ?", revokedAt, "hash-to-revoke");

        Timestamp reloaded = jdbcTemplate.queryForObject(
                "SELECT revoked_at FROM api_keys WHERE key_hash = ?", Timestamp.class, "hash-to-revoke");
        assertThat(reloaded).isEqualTo(revokedAt);
    }
}
