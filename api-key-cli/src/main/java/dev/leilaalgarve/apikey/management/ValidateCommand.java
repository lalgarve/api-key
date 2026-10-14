package dev.leilaalgarve.apikey.management;

import dev.leilaalgarve.apikey.CliCommand;
import dev.leilaalgarve.apikey.CliOutput;
import dev.leilaalgarve.apikey.validation.ApiKeyFailureReason;
import dev.leilaalgarve.apikey.validation.ApiKeyValidationResult;
import dev.leilaalgarve.apikey.validation.ApiKeyValidationResult.Invalid;
import dev.leilaalgarve.apikey.validation.ApiKeyValidationResult.Valid;
import dev.leilaalgarve.apikey.validation.ApiKeyValidator;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/**
 * Orchestrates the `validate` command per contracts/cli-commands.md: read the first line of
 * stdin, ask {@link ApiKeyValidator} whether it's a currently valid key, and translate the
 * result into this command's own exit code and message. The validation rule itself is never
 * reimplemented here -- see specs/010-validate-api-key-cli/spec.md FR3. Only called by
 * ApiKeyCliRunner when args[0] is "validate" -- see GenerateCommand for why this returns an
 * exit code instead of calling System.exit itself.
 */
@Component
public class ValidateCommand implements CliCommand {

    private final ApiKeyValidator validator;

    public ValidateCommand(ApiKeyValidator validator) {
        this.validator = validator;
    }

    /** No stdin available in this form -- only reachable from a caller that bypasses ApiKeyCliRunner's dispatch. */
    @Override
    public int execute(String[] args, PrintStream out, PrintStream err) {
        return execute(args, InputStream.nullInputStream(), out, err);
    }

    @Override
    public int execute(String[] args, InputStream in, PrintStream out, PrintStream err) {
        String rawKey = readFirstLine(in);
        ApiKeyValidationResult result = validator.validate(rawKey);

        return switch (result) {
            case Valid valid -> {
                CliOutput.println(out, "API key is valid for client '" + valid.clientName() + "'.");
                yield 0;
            }
            case Invalid invalid -> {
                CliOutput.println(err, messageFor(invalid.reason()));
                yield exitCodeFor(invalid.reason());
            }
        };
    }

    private static String messageFor(ApiKeyFailureReason reason) {
        return switch (reason) {
            case MISSING -> "Error: no API key provided on stdin.";
            case MALFORMED -> "Invalid: the key is malformed.";
            case NOT_FOUND -> "Invalid: no API key matches.";
            case REVOKED -> "Invalid: the key was revoked.";
            case EXPIRED -> "Invalid: the key expired.";
        };
    }

    private static int exitCodeFor(ApiKeyFailureReason reason) {
        return switch (reason) {
            case MISSING -> 1;
            case MALFORMED -> 2;
            case NOT_FOUND -> 3;
            case REVOKED -> 4;
            case EXPIRED -> 5;
        };
    }

    /** Only the first line, trimmed -- never the whole stream (spec.md FR2). Null when stdin has none. */
    private static String readFirstLine(InputStream in) {
        try {
            String line = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).readLine();
            return line == null ? null : line.strip();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
