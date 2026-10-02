package dev.leilaalgarve.apikey;

import java.io.PrintStream;

/**
 * Line output for every {@link CliCommand}. Always terminates with "\n" instead of
 * PrintStream.println's platform line separator, so the CLI's output is byte-for-byte the same
 * on Windows and Linux -- contracts/cli-commands.md and the tests pin it to "\n".
 */
public final class CliOutput {

    private CliOutput() {
    }

    public static void println(PrintStream stream, String line) {
        stream.print(line + "\n");
    }

    public static void println(PrintStream stream) {
        stream.print("\n");
    }
}
