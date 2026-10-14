package dev.leilaalgarve.apikey;

import java.io.InputStream;
import java.io.PrintStream;

/**
 * One CLI subcommand (`generate`, `revoke`, `list`, ...). Returns the exit code instead of
 * calling System.exit itself, so every branch stays unit-testable without terminating the JVM
 * -- see ApiKeyCliRunner for the thin adapter that actually exits the process, and ProcessExiter
 * for the seam around System.exit.
 */
@FunctionalInterface
public interface CliCommand {

    int execute(String[] args, PrintStream out, PrintStream err);

    /**
     * Same as {@link #execute(String[], PrintStream, PrintStream)}, plus stdin -- only
     * `validate` (010-validate-api-key-cli) reads it, so this default just ignores it and
     * delegates, rather than making every other command's implementation and tests take an
     * InputStream they'll never use (plan.md, "Como `validate` lê stdin").
     */
    default int execute(String[] args, InputStream in, PrintStream out, PrintStream err) {
        return execute(args, out, err);
    }
}
