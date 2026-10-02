package dev.leilaalgarve.apikey.core;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Maps to the `api_keys` table (data-model.md) -- only the hash is ever stored, never the plaintext key. */
@Entity
@Table(name = "api_keys")
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_name", nullable = false)
    private String clientName;

    @Column(name = "key_hash", nullable = false, unique = true)
    private String keyHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected ApiKey() {
        // JPA
    }

    public ApiKey(String clientName, String keyHash, Instant createdAt, Instant expiresAt) {
        this.clientName = clientName;
        this.keyHash = keyHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public String getClientName() {
        return clientName;
    }

    public String getKeyHash() {
        return keyHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void revokeAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    /** True once {@code now} has reached this key's revocation moment (past or exactly now). */
    public boolean isRevoked(Instant now) {
        return revokedAt != null && !revokedAt.isAfter(now);
    }

    /** True once {@code now} has reached this key's own expiration, independent of revocation. */
    public boolean isExpired(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }
}
