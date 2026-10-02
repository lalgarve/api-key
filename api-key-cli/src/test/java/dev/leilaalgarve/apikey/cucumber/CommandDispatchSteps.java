package dev.leilaalgarve.apikey.cucumber;

import static org.assertj.core.api.Assertions.assertThat;

import dev.leilaalgarve.apikey.CliCommand;
import dev.leilaalgarve.apikey.core.ApiKeyHasher;
import dev.leilaalgarve.apikey.core.ApiKeyRepository;
import dev.leilaalgarve.apikey.issuance.ApiKeyGenerator;
import dev.leilaalgarve.apikey.issuance.GenerateCommand;
import dev.leilaalgarve.apikey.issuance.OldKeyRotationPolicy;
import dev.leilaalgarve.apikey.management.ListCommand;
import dev.leilaalgarve.apikey.management.RevokeCommand;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The one generic "run a CLI command and check what happened" vocabulary every feature shares
 * -- see ScenarioState for why client names and key-id labels get rewritten before dispatch.
 */
public class CommandDispatchSteps {

    private static final Pattern PLAINTEXT_KEY_PATTERN = Pattern.compile("dak_[A-Za-z0-9_-]+");

    @Autowired
    private ScenarioState state;

    @Autowired
    private GenerateCommand generateCommand;

    @Autowired
    private RevokeCommand revokeCommand;

    @Autowired
    private ListCommand listCommand;

    @Autowired
    private ApiKeyRepository repository;

    @Autowired
    private ApiKeyGenerator apiKeyGenerator;

    @Given("the HMAC pepper is not configured")
    public void theHmacPepperIsNotConfigured() {
        state.hmacPepperMissing = true;
    }

    @Given("the database rejects the next update to the key labeled {string}")
    public void theDatabaseRejectsTheNextUpdateToTheKeyLabeled(String label) {
        long realId = Long.parseLong(state.resolveId(label));
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataAccessResourceFailureException("connection refused"))
                .when(repository)
                .save(org.mockito.ArgumentMatchers.argThat(key -> key != null && realId == key.getId()));
    }

    @When("the operator runs {string}")
    public void theOperatorRuns(String commandLine) {
        List<String> tokens = substitute(tokenize(commandLine));
        PrintStream out = new PrintStream(state.outBytes, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(state.errBytes, true, StandardCharsets.UTF_8);

        CliCommand command = switch (tokens.get(0)) {
            case "generate" -> state.hmacPepperMissing ? generateCommandWithMissingPepper() : generateCommand;
            case "revoke" -> revokeCommand;
            case "list" -> listCommand;
            default -> throw new IllegalArgumentException("Unknown command word: " + tokens.get(0));
        };
        state.exitCode = command.execute(tokens.toArray(new String[0]), out, err);
    }

    private GenerateCommand generateCommandWithMissingPepper() {
        return new GenerateCommand(apiKeyGenerator, new ApiKeyHasher(""), repository, new OldKeyRotationPolicy(repository));
    }

    @Then("the exit code is {int}")
    public void theExitCodeIs(int expected) {
        assertThat(state.exitCode).isEqualTo(expected);
    }

    @Then("stdout contains {string}")
    public void stdoutContains(String expected) {
        assertThat(out()).contains(expected);
    }

    @Then("stdout says the key was generated for client {string}")
    public void stdoutSaysTheKeyWasGeneratedForClient(String client) {
        assertThat(out()).contains("API key generated for client '" + state.qualifyClient(client) + "'");
    }

    @Then("stdout is empty")
    public void stdoutIsEmpty() {
        assertThat(state.outBytes.toByteArray()).isEmpty();
    }

    @Then("stderr is {string}")
    public void stderrIs(String expected) {
        assertThat(err()).isEqualTo(expected + "\n");
    }

    @Then("stderr contains {string}")
    public void stderrContains(String expected) {
        assertThat(err()).contains(expected);
    }

    @Then("a dak_-prefixed key is printed to stdout exactly once")
    public void aDakPrefixedKeyIsPrintedToStdoutExactlyOnce() {
        Matcher matcher = PLAINTEXT_KEY_PATTERN.matcher(out());
        int occurrences = 0;
        while (matcher.find()) {
            occurrences++;
        }
        assertThat(occurrences).isEqualTo(1);
    }

    @Then("the stored key has no plaintext value anywhere")
    public void theStoredKeyHasNoPlaintextValueAnywhere() {
        assertThat(state.lastClient).as("no --client was referenced yet this scenario").isNotNull();
        var keys = repository.findByClientName(state.qualifyClient(state.lastClient));
        assertThat(keys).hasSize(1);
        assertThat(out()).doesNotContain(keys.get(0).getKeyHash());
    }

    private String out() {
        return state.outBytes.toString(StandardCharsets.UTF_8);
    }

    private String err() {
        return state.errBytes.toString(StandardCharsets.UTF_8);
    }

    private List<String> substitute(List<String> tokens) {
        List<String> result = new ArrayList<>(tokens);
        for (int i = 0; i < result.size() - 1; i++) {
            if ("--client".equals(result.get(i))) {
                String original = result.get(i + 1);
                state.lastClient = original;
                if (!original.isBlank()) {
                    result.set(i + 1, state.qualifyClient(original));
                }
            } else if ("--id".equals(result.get(i))) {
                result.set(i + 1, state.resolveId(result.get(i + 1)));
            }
        }
        return result;
    }

    /** Whitespace-separated tokens, except inside a pair of double quotes (which may be empty). */
    private static List<String> tokenize(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean tokenStarted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
                tokenStarted = true;
            } else if (Character.isWhitespace(c) && !inQuotes) {
                if (tokenStarted) {
                    tokens.add(current.toString());
                    current.setLength(0);
                    tokenStarted = false;
                }
            } else {
                current.append(c);
                tokenStarted = true;
            }
        }
        if (tokenStarted) {
            tokens.add(current.toString());
        }
        return tokens;
    }
}
