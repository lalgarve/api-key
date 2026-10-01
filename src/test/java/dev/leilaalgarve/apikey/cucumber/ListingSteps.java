package dev.leilaalgarve.apikey.cucumber;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Then;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Asserts on `list`'s printed table rather than querying the repository directly -- these
 * scenarios are precisely about what the command shows, so checking its real output is more
 * faithful than recomputing status with a parallel implementation.
 */
public class ListingSteps {

    @Autowired
    private ScenarioState state;

    @Then("the output lists the key labeled {string}")
    public void theOutputListsTheKeyLabeled(String label) {
        assertThat(findRow(label)).as("row for key labeled " + label).isPresent();
    }

    @Then("the output does not list the key labeled {string}")
    public void theOutputDoesNotListTheKeyLabeled(String label) {
        assertThat(findRow(label)).as("row for key labeled " + label).isEmpty();
    }

    @Then("the output lists the key labeled {string} with status {string}")
    public void theOutputListsTheKeyLabeledWithStatus(String label, String status) {
        Optional<String> row = findRow(label);
        assertThat(row).as("row for key labeled " + label).isPresent();
        assertThat(row.get()).contains(status);
    }

    @Then("the output says no keys were found")
    public void theOutputSaysNoKeysWereFound() {
        assertThat(state.outBytes.toString(StandardCharsets.UTF_8))
                .isEqualTo("No API keys found for the given filters.\n");
    }

    private Optional<String> findRow(String label) {
        String realId = state.resolveId(label);
        String out = state.outBytes.toString(StandardCharsets.UTF_8);
        for (String line : out.split("\n")) {
            if (line.startsWith(realId + " ")) {
                return Optional.of(line);
            }
        }
        return Optional.empty();
    }
}
