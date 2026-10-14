package dev.leilaalgarve.apikey.cucumber;

import dev.leilaalgarve.apikey.core.ApiKey;
import dev.leilaalgarve.apikey.core.ApiKeyHasher;
import dev.leilaalgarve.apikey.core.ApiKeyRepository;
import dev.leilaalgarve.apikey.issuance.ApiKeyGenerator;
import io.cucumber.java.en.Given;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Preconditions for validate-api-key-cli.feature. Unlike KeyLifecycleSteps (generate/revoke/
 * list, which only ever reference a key by id/label), `validate` is presented the plaintext key
 * itself -- so these steps issue a real plaintext/hash pair via the real generator/hasher, not
 * a fake hash string, and remember the plaintext in ScenarioState for "that key on stdin" to use.
 */
public class ValidationLifecycleSteps {

    @Autowired
    private ScenarioState state;

    @Autowired
    private ApiKeyRepository repository;

    @Autowired
    private ApiKeyGenerator generator;

    @Autowired
    private ApiKeyHasher hasher;

    @Given("client {string} has an active key")
    public void clientHasAnActiveKey(String client) {
        issueKey(client, Instant.now(), null, null);
    }

    @Given("client {string} has an active key that never expires")
    public void clientHasAnActiveKeyThatNeverExpires(String client) {
        issueKey(client, Instant.now(), null, null);
    }

    @Given("client {string} has a key scheduled to be revoked in {int} days")
    public void clientHasAKeyScheduledToBeRevokedInDays(String client, int days) {
        issueKey(client, Instant.now(), null, Instant.now().plus(days, ChronoUnit.DAYS));
    }

    @Given("client {string} has a revoked key")
    public void clientHasARevokedKey(String client) {
        issueKey(client, Instant.now(), null, Instant.now().minus(1, ChronoUnit.DAYS));
    }

    @Given("client {string} has an expired key")
    public void clientHasAnExpiredKey(String client) {
        Instant now = Instant.now();
        issueKey(client, now.minus(100, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS), null);
    }

    @Given("client {string} has a key that is both expired and revoked")
    public void clientHasAKeyThatIsBothExpiredAndRevoked(String client) {
        Instant now = Instant.now();
        Instant expiresAt = now.minus(10, ChronoUnit.DAYS);
        issueKey(client, now.minus(100, ChronoUnit.DAYS), expiresAt, expiresAt);
    }

    private void issueKey(String client, Instant createdAt, Instant expiresAt, Instant revokedAt) {
        String qualifiedClient = state.qualifyClient(client);
        String rawKey = generator.generate();
        ApiKey key = new ApiKey(qualifiedClient, hasher.hash(rawKey), createdAt, expiresAt);
        if (revokedAt != null) {
            key.revokeAt(revokedAt);
        }
        repository.save(key);
        state.lastIssuedRawKey = rawKey;
    }
}
