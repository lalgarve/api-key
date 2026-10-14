package dev.leilaalgarve.apikey.core;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loading many keys must not cost one permissions query per key -- `list` reads every key
 * (specs/013-key-permissions/spec.md, "Requisitos não-funcionais"; plan.md, EAGER + SUBSELECT).
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class ApiKeyPermissionsFetchTest {

    @Autowired
    private ApiKeyRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void loadsThePermissionsOfManyKeysWithOneExtraQuery() {
        for (int i = 0; i < 5; i++) {
            repository.save(new ApiKey("fetch-test", "fetch-hash-" + i, Instant.now(), null, Set.of("email:send")));
        }
        entityManager.flush();
        entityManager.clear();

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        List<ApiKey> keys = repository.findByClientName("fetch-test");
        keys.forEach(key -> assertThat(key.getPermissions()).containsExactly("email:send"));

        assertThat(keys).hasSize(5);
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(2);
    }
}
