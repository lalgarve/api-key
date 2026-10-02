package dev.leilaalgarve.apikey.core;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    List<ApiKey> findByClientName(String clientName);

    /** Lookup by the unique key_hash column -- how a presented key is matched to its row. */
    Optional<ApiKey> findByKeyHash(String keyHash);
}
