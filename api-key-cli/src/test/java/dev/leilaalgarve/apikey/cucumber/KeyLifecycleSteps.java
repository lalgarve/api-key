package dev.leilaalgarve.apikey.cucumber;

import static org.assertj.core.api.Assertions.assertThat;

import dev.leilaalgarve.apikey.core.ApiKey;
import dev.leilaalgarve.apikey.core.ApiKeyRepository;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

/** Creating and mutating keys as scenario preconditions, and asserting on their stored state. */
public class KeyLifecycleSteps {

    @Autowired
    private ScenarioState state;

    @Autowired
    private ApiKeyRepository repository;

    @Given("no key exists with id {string}")
    public void noKeyExistsWithId(String literalId) {
        assertThat(repository.findById(Long.parseLong(literalId))).isEmpty();
    }

    @Given("client {string} has no key at all")
    public void clientHasNoKeyAtAll(String client) {
        // Documentary only -- a freshly scenario-qualified client name starts empty (ScenarioState).
    }

    @Given("client {string} has an active key labeled {string}")
    public void clientHasAnActiveKeyLabeled(String client, String label) {
        createKey(client, label, Instant.now(), null, null);
    }

    @Given("client {string} has an active key labeled {string} expiring in {int} days")
    public void clientHasAnActiveKeyLabeledExpiringInDays(String client, String label, int days) {
        Instant now = Instant.now();
        createKey(client, label, now, now.plus(days, ChronoUnit.DAYS), null);
    }

    @Given("client {string} has an active key labeled {string} already scheduled to be revoked in {int} days")
    public void clientHasAnActiveKeyLabeledAlreadyScheduledToBeRevokedInDays(String client, String label, int days) {
        createKey(client, label, Instant.now(), null, Instant.now().plus(days, ChronoUnit.DAYS));
    }

    @Given("client {string} has a key labeled {string} that is already revoked")
    public void clientHasAKeyLabeledThatIsAlreadyRevoked(String client, String label) {
        createKey(client, label, Instant.now(), null, Instant.now().minus(1, ChronoUnit.DAYS));
    }

    @Given("client {string} has a key labeled {string} that already expired {int} days ago")
    public void clientHasAKeyLabeledThatAlreadyExpiredDaysAgo(String client, String label, int days) {
        Instant now = Instant.now();
        createKey(client, label, now.minus(100, ChronoUnit.DAYS), now.minus(days, ChronoUnit.DAYS), null);
    }

    @Given("client {string} has a key labeled {string} that is both expired and revoked")
    public void clientHasAKeyLabeledThatIsBothExpiredAndRevoked(String client, String label) {
        Instant now = Instant.now();
        Instant expiresAt = now.minus(10, ChronoUnit.DAYS);
        createKey(client, label, now.minus(100, ChronoUnit.DAYS), expiresAt, expiresAt);
    }

    private void createKey(String client, String label, Instant createdAt, Instant expiresAt, Instant revokedAt) {
        String qualifiedClient = state.qualifyClient(client);
        ApiKey key = new ApiKey(qualifiedClient, "hash-" + label + "-" + System.nanoTime(), createdAt, expiresAt);
        if (revokedAt != null) {
            key.revokeAt(revokedAt);
        }
        ApiKey saved = repository.save(key);
        state.rememberId(label, saved.getId());
    }

    @Then("the key labeled {string} is revoked")
    public void theKeyLabeledIsRevoked(String label) {
        assertThat(reload(label).isRevoked(Instant.now())).isTrue();
    }

    @Then("the key labeled {string} is not revoked")
    public void theKeyLabeledIsNotRevoked(String label) {
        assertThat(reload(label).isRevoked(Instant.now())).isFalse();
    }

    @Then("the key labeled {string} has no expiration")
    public void theKeyLabeledHasNoExpiration(String label) {
        assertThat(reload(label).getExpiresAt()).isNull();
    }

    @Then("the key labeled {string} expires in about {int} days")
    public void theKeyLabeledExpiresInAboutDays(String label, int days) {
        ApiKey key = reload(label);
        assertThat(key.isExpired(Instant.now().plus(days - 1, ChronoUnit.DAYS))).isFalse();
        assertThat(key.isExpired(Instant.now().plus(days + 1, ChronoUnit.DAYS))).isTrue();
    }

    @Then("the key labeled {string} is scheduled to be revoked in about {int} days")
    public void theKeyLabeledIsScheduledToBeRevokedInAboutDays(String label, int days) {
        ApiKey key = reload(label);
        assertThat(key.isRevoked(Instant.now().plus(days - 1, ChronoUnit.DAYS))).isFalse();
        assertThat(key.isRevoked(Instant.now().plus(days + 1, ChronoUnit.DAYS))).isTrue();
    }

    @Then("the only key for client {string} expires in about {int} days")
    public void theOnlyKeyForClientExpiresInAboutDays(String client, int days) {
        ApiKey key = theOnlyKeyFor(client);
        assertThat(key.isExpired(Instant.now().plus(days - 1, ChronoUnit.DAYS))).isFalse();
        assertThat(key.isExpired(Instant.now().plus(days + 1, ChronoUnit.DAYS))).isTrue();
    }

    @Then("the only key for client {string} has no expiration")
    public void theOnlyKeyForClientHasNoExpiration(String client) {
        assertThat(theOnlyKeyFor(client).getExpiresAt()).isNull();
    }

    @Then("the client {string} has exactly {int} key(s) stored")
    public void theClientHasExactlyKeysStored(String client, int expectedCount) {
        assertThat(repository.findByClientName(state.qualifyClient(client))).hasSize(expectedCount);
    }

    private ApiKey theOnlyKeyFor(String client) {
        List<ApiKey> keys = repository.findByClientName(state.qualifyClient(client));
        assertThat(keys).hasSize(1);
        return keys.get(0);
    }

    private ApiKey reload(String label) {
        long realId = Long.parseLong(state.resolveId(label));
        return repository.findById(realId).orElseThrow();
    }
}
