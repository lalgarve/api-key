package dev.leilaalgarve.apikey.validation.cucumber;

import static dev.leilaalgarve.apikey.validation.IssuedKeyMother.activeKey;
import static dev.leilaalgarve.apikey.validation.cucumber.CucumberSpringConfiguration.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import dev.leilaalgarve.apikey.core.ApiKeyHasher;
import dev.leilaalgarve.apikey.core.ApiKeyRepository;
import dev.leilaalgarve.apikey.validation.ApiKeyFailureReason;
import dev.leilaalgarve.apikey.validation.ApiKeyValidationResult;
import dev.leilaalgarve.apikey.validation.ApiKeyValidationResult.Invalid;
import dev.leilaalgarve.apikey.validation.ApiKeyValidationResult.Valid;
import dev.leilaalgarve.apikey.validation.ApiKeyValidator;
import dev.leilaalgarve.apikey.validation.IssuedKeyMother;
import dev.leilaalgarve.apikey.validation.IssuedKeyMother.Builder;
import dev.leilaalgarve.apikey.validation.IssuedKeyMother.IssuedKey;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Steps for validate-api-key.feature. Cucumber creates a fresh instance of this class per
 * scenario, so the fields below are per-scenario state.
 *
 * <p>Scenarios aren't wrapped in a rolled-back transaction, and in CI every module's tests share
 * one PostgreSQL database -- where api-key-cli's tests expect to find only their own rows (e.g.
 * GenerateCommandTest counts the whole table). So each scenario deletes the keys it issued.
 */
public class ValidationSteps {

    @Autowired
    private ApiKeyValidator validator;

    @Autowired
    private ApiKeyRepository repository;

    @Autowired
    private ApiKeyHasher hasher;

    private final Map<String, String> rawKeysByLabel = new HashMap<>();
    private final List<Long> issuedIds = new ArrayList<>();
    private ApiKeyValidationResult result;

    @After
    public void deleteIssuedKeys() {
        repository.deleteAllById(issuedIds);
    }

    @Given("client {string} has an active key labeled {string}")
    public void clientHasAnActiveKey(String client, String label) {
        issue(label, activeKey(NOW).forClient(client));
    }

    @Given("client {string} has a key labeled {string} that never expires")
    public void clientHasAKeyThatNeverExpires(String client, String label) {
        issue(label, activeKey(NOW).forClient(client).neverExpiring());
    }

    @Given("client {string} has a key labeled {string} that expired {int} day(s) ago")
    public void clientHasAnExpiredKey(String client, String label, int days) {
        issue(label, activeKey(NOW).forClient(client).expiringAt(NOW.minus(days, ChronoUnit.DAYS)));
    }

    @Given("client {string} has a key labeled {string} that was revoked {int} day(s) ago")
    public void clientHasARevokedKey(String client, String label, int days) {
        issue(label, activeKey(NOW).forClient(client).revokedAt(NOW.minus(days, ChronoUnit.DAYS)));
    }

    @Given("client {string} has a key labeled {string} whose revocation is scheduled in {int} day(s)")
    public void clientHasAKeyWithAScheduledRevocation(String client, String label, int days) {
        issue(label, activeKey(NOW).forClient(client).revokedAt(NOW.plus(days, ChronoUnit.DAYS)));
    }

    @Given("client {string} has a key labeled {string} that expired {int} day(s) ago and was revoked {int} day(s) ago")
    public void clientHasAnExpiredAndRevokedKey(String client, String label, int expiredDays, int revokedDays) {
        issue(label, activeKey(NOW).forClient(client)
                .expiringAt(NOW.minus(expiredDays, ChronoUnit.DAYS))
                .revokedAt(NOW.minus(revokedDays, ChronoUnit.DAYS)));
    }

    @Given("a well-formed key labeled {string} that was never issued")
    public void aWellFormedKeyThatWasNeverIssued(String label) {
        rawKeysByLabel.put(label, IssuedKeyMother.wellFormedRawKey());
    }

    @When("the service validates the key labeled {string}")
    public void theServiceValidatesTheKeyLabeled(String label) {
        assertThat(rawKeysByLabel).as("key labeled %s was set up by a Given step", label).containsKey(label);
        result = validator.validate(rawKeysByLabel.get(label));
    }

    @When("the service validates no key")
    public void theServiceValidatesNoKey() {
        result = validator.validate(null);
    }

    @When("the service validates the key {string}")
    public void theServiceValidatesTheLiteralKey(String rawKey) {
        result = validator.validate(rawKey);
    }

    @Then("the key is accepted for client {string}")
    public void theKeyIsAcceptedForClient(String client) {
        assertThat(result).isEqualTo(new Valid(client));
    }

    @Then("the key is rejected as {string}")
    public void theKeyIsRejectedAs(String reason) {
        assertThat(result).isEqualTo(new Invalid(ApiKeyFailureReason.valueOf(reason)));
    }

    private void issue(String label, Builder key) {
        IssuedKey issued = key.saveWith(repository, hasher);
        issuedIds.add(issued.row().getId());
        rawKeysByLabel.put(label, issued.rawKey());
    }
}
