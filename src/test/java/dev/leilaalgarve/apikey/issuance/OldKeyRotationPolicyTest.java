package dev.leilaalgarve.apikey.issuance;

import static org.assertj.core.api.Assertions.assertThat;

import dev.leilaalgarve.apikey.DeployoApiKeyApplication;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = DeployoApiKeyApplication.class)
@Transactional
class OldKeyRotationPolicyTest {

    @Autowired
    private OldKeyRotationPolicy policy;

    @Autowired
    private ApiKeyRepository repository;

    @Test
    void noActiveKeysMeansNoEffect() {
        OldKeyRotationPolicy.Result result = policy.scheduleRevocationOfActiveKeys("no-such-client", 7);

        assertThat(result.updatedCount()).isZero();
    }

    @Test
    void schedulesRevocationOfASingleActiveKey() {
        ApiKey key = repository.save(new ApiKey("jogo-acoes", "hash-single", Instant.now(), null));

        OldKeyRotationPolicy.Result result = policy.scheduleRevocationOfActiveKeys("jogo-acoes", 7);

        assertThat(result.updatedCount()).isEqualTo(1);
        ApiKey reloaded = repository.findById(key.getId()).orElseThrow();
        assertThat(reloaded.isRevoked(Instant.now().plus(6, ChronoUnit.DAYS))).isFalse();
        assertThat(reloaded.isRevoked(Instant.now().plus(8, ChronoUnit.DAYS))).isTrue();
    }

    @Test
    void schedulesRevocationOfMultipleActiveKeys() {
        ApiKey key1 = repository.save(new ApiKey("jogo-acoes", "hash-multi-1", Instant.now(), null));
        ApiKey key2 = repository.save(new ApiKey("jogo-acoes", "hash-multi-2", Instant.now(), null));

        OldKeyRotationPolicy.Result result = policy.scheduleRevocationOfActiveKeys("jogo-acoes", 7);

        assertThat(result.updatedCount()).isEqualTo(2);
        assertThat(repository.findById(key1.getId()).orElseThrow().getRevokedAt()).isNotNull();
        assertThat(repository.findById(key2.getId()).orElseThrow().getRevokedAt()).isNotNull();
    }

    @Test
    void zeroDaysRevokesImmediately() {
        ApiKey key = repository.save(new ApiKey("jogo-acoes", "hash-immediate", Instant.now(), null));

        OldKeyRotationPolicy.Result result = policy.scheduleRevocationOfActiveKeys("jogo-acoes", 0);

        assertThat(result.updatedCount()).isEqualTo(1);
        assertThat(repository.findById(key.getId()).orElseThrow().isRevoked(Instant.now())).isTrue();
    }

    @Test
    void neverPushesBackAnAlreadySoonerSchedule() {
        ApiKey key = repository.save(new ApiKey("jogo-acoes", "hash-sooner", Instant.now(), null));
        key.revokeAt(Instant.now().plus(2, ChronoUnit.DAYS));
        repository.save(key);

        OldKeyRotationPolicy.Result result = policy.scheduleRevocationOfActiveKeys("jogo-acoes", 7);

        assertThat(result.updatedCount()).isZero();
        ApiKey reloaded = repository.findById(key.getId()).orElseThrow();
        // still ~2 days out, not pushed to 7
        assertThat(reloaded.isRevoked(Instant.now().plus(3, ChronoUnit.DAYS))).isTrue();
    }

    @Test
    void pullsForwardALaterExistingSchedule() {
        ApiKey key = repository.save(new ApiKey("jogo-acoes", "hash-later", Instant.now(), null));
        key.revokeAt(Instant.now().plus(30, ChronoUnit.DAYS));
        repository.save(key);

        OldKeyRotationPolicy.Result result = policy.scheduleRevocationOfActiveKeys("jogo-acoes", 7);

        assertThat(result.updatedCount()).isEqualTo(1);
        ApiKey reloaded = repository.findById(key.getId()).orElseThrow();
        assertThat(reloaded.isRevoked(Instant.now().plus(8, ChronoUnit.DAYS))).isTrue();
    }

    @Test
    void alreadyRevokedKeysAreIgnored() {
        ApiKey key = repository.save(new ApiKey("jogo-acoes", "hash-already-revoked", Instant.now(), null));
        Instant pastRevocation = Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);
        key.revokeAt(pastRevocation);
        repository.save(key);

        OldKeyRotationPolicy.Result result = policy.scheduleRevocationOfActiveKeys("jogo-acoes", 7);

        assertThat(result.updatedCount()).isZero();
        assertThat(repository.findById(key.getId()).orElseThrow().getRevokedAt()).isEqualTo(pastRevocation);
    }

    @Test
    void doesNotTouchOtherClientsKeys() {
        ApiKey otherClientKey = repository.save(new ApiKey("billing", "hash-other-client", Instant.now(), null));

        policy.scheduleRevocationOfActiveKeys("jogo-acoes", 7);

        assertThat(repository.findById(otherClientKey.getId()).orElseThrow().getRevokedAt()).isNull();
    }
}
