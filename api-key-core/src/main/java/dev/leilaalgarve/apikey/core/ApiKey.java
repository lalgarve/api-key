package dev.leilaalgarve.apikey.core;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

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

    /**
     * Rows of `api_key_permissions` (specs/013-key-permissions/data-model.md), written once with
     * the key and never changed. EAGER + SUBSELECT: loaded even outside a transaction (list and
     * the validator run without one), and in one extra query for a whole findAll, not one per
     * key (013 plan.md, "Como evitar N+1 em list").
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "api_key_permissions", joinColumns = @JoinColumn(name = "api_key_id"))
    @Column(name = "permission", nullable = false, length = 100)
    @Fetch(FetchMode.SUBSELECT)
    private Set<String> permissions = new HashSet<>();

    protected ApiKey() {
        // JPA
    }

    /** A key with no stored permission -- same as every key issued before 1.1.0. */
    public ApiKey(String clientName, String keyHash, Instant createdAt, Instant expiresAt) {
        this(clientName, keyHash, createdAt, expiresAt, Set.of());
    }

    public ApiKey(String clientName, String keyHash, Instant createdAt, Instant expiresAt, Set<String> permissions) {
        this.clientName = clientName;
        this.keyHash = keyHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.permissions = new HashSet<>(permissions);
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

    /** The stored permissions, sorted; empty for a key issued without any (see PermissionCatalog#effectivePermissions). */
    public SortedSet<String> getPermissions() {
        return Collections.unmodifiableSortedSet(new TreeSet<>(permissions));
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
