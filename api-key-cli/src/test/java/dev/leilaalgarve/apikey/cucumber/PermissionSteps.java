package dev.leilaalgarve.apikey.cucumber;

import static org.assertj.core.api.Assertions.assertThat;

import dev.leilaalgarve.apikey.core.ApiKey;
import dev.leilaalgarve.apikey.core.ApiKeyHasher;
import dev.leilaalgarve.apikey.core.ApiKeyRepository;
import dev.leilaalgarve.apikey.core.PermissionCatalog;
import dev.leilaalgarve.apikey.core.PermissionCatalog.Entry;
import dev.leilaalgarve.apikey.validation.ApiKeyValidationResult;
import dev.leilaalgarve.apikey.validation.ApiKeyValidator;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * key-permissions.feature (specs/013-key-permissions). The Spring context has no catalog, so a
 * scenario that sets one keeps it in ScenarioState and CommandDispatchSteps builds the commands
 * with it -- the same trick the missing-pepper scenario uses.
 */
public class PermissionSteps {

    @Autowired
    private ScenarioState state;

    @Autowired
    private ApiKeyRepository repository;

    @Autowired
    private ApiKeyHasher hasher;

    private ApiKeyValidationResult validationResult;

    @Given("the permission catalog is:")
    public void thePermissionCatalogIs(DataTable table) {
        List<Entry> entries = table.asMaps().stream()
                .map(row -> new Entry(row.get("permission"), "yes".equals(row.get("default"))))
                .toList();
        state.catalog = new PermissionCatalog(entries);
    }

    @Given("no permission catalog is configured")
    public void noPermissionCatalogIsConfigured() {
        // Documentary: ScenarioState starts without a catalog, so the context's own beans run.
        state.catalog = null;
    }

    @When("the protected service validates that key")
    public void theProtectedServiceValidatesThatKey() {
        PermissionCatalog catalog = state.catalog == null ? PermissionCatalog.none() : state.catalog;
        validationResult = new ApiKeyValidator(repository, hasher, Clock.systemUTC(), catalog)
                .validate(state.lastIssuedRawKey);
    }

    @Then("the result is valid with the permissions {string}")
    public void theResultIsValidWithThePermissions(String permissions) {
        assertThat(validationResult).isInstanceOf(ApiKeyValidationResult.Valid.class);
        assertThat(((ApiKeyValidationResult.Valid) validationResult).permissions()).isEqualTo(parse(permissions));
    }

    @Then("the result is valid with no permissions")
    public void theResultIsValidWithNoPermissions() {
        assertThat(validationResult).isInstanceOf(ApiKeyValidationResult.Valid.class);
        assertThat(((ApiKeyValidationResult.Valid) validationResult).permissions()).isEmpty();
    }

    @Then("the last line of stdout is {string}")
    public void theLastLineOfStdoutIs(String expected) {
        String[] lines = out().split("\n");
        assertThat(lines[lines.length - 1]).isEqualTo(expected);
    }

    @Then("stdout does not contain {string}")
    public void stdoutDoesNotContain(String unexpected) {
        assertThat(out()).doesNotContain(unexpected);
    }

    @Then("stdout says the key is valid for client {string} with the permissions {string}")
    public void stdoutSaysTheKeyIsValidForClientWithThePermissions(String client, String permissions) {
        assertThat(out()).isEqualTo("API key is valid for client '" + state.qualifyClient(client) + "'.\n"
                + "Permissions: " + permissions + ".\n");
    }

    @Then("the new key has exactly the permissions {string}")
    public void theNewKeyHasExactlyThePermissions(String permissions) {
        assertThat(onlyKeyOfLastClient().getPermissions()).isEqualTo(parse(permissions));
    }

    @Then("the new key has no permissions")
    public void theNewKeyHasNoPermissions() {
        assertThat(onlyKeyOfLastClient().getPermissions()).isEmpty();
    }

    @Then("no key is saved")
    public void noKeyIsSaved() {
        assertThat(repository.findByClientName(state.qualifyClient(state.lastClient))).isEmpty();
    }

    @Then("the last column of the header is {string}")
    public void theLastColumnOfTheHeaderIs(String column) {
        String header = out().split("\n")[0];
        String[] columns = header.trim().split("\\s+");
        assertThat(columns[columns.length - 1]).isEqualTo(column);
    }

    @Then("the row of that key ends with {string}")
    public void theRowOfThatKeyEndsWith(String ending) {
        assertThat(state.lastIssuedKeyId).as("no key was issued yet this scenario").isNotNull();
        String row = Arrays.stream(out().split("\n"))
                .filter(line -> line.startsWith(state.lastIssuedKeyId + " "))
                .findFirst().orElseThrow();
        assertThat(row).endsWith(ending);
    }

    private ApiKey onlyKeyOfLastClient() {
        assertThat(state.lastClient).as("no --client was referenced yet this scenario").isNotNull();
        List<ApiKey> keys = repository.findByClientName(state.qualifyClient(state.lastClient));
        assertThat(keys).hasSize(1);
        return keys.get(0);
    }

    private String out() {
        return state.outBytes.toString(StandardCharsets.UTF_8);
    }

    private static Set<String> parse(String permissions) {
        return Arrays.stream(permissions.split(",")).map(String::strip).collect(Collectors.toSet());
    }
}
