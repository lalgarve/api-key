package io.deployo.apikey;

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
}
