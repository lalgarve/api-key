package io.deployo.apikey;

import io.deployo.apikey.issuance.GenerateCommand;
import io.deployo.apikey.management.ListCommand;
import io.deployo.apikey.management.RevokeCommand;
import java.util.Map;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Dispatches to the right {@link CliCommand} by {@code args[0]}, runs it against the real
 * stdout/stderr, and exits the process with the returned code when it's non-zero. An
 * unrecognized (or missing) command word is a silent no-op, same as before this class had more
 * than one command to choose from. Success (0) never calls {@link ProcessExiter#exit(int)} --
 * the JVM ends normally on its own, which is what keeps DeployoApiKeyApplicationTests' direct
 * call to {@code main()} safe (it exercises the success path).
 */
@Component
public class ApiKeyCliRunner implements CommandLineRunner {

    private final Map<String, CliCommand> commands;
    private final ProcessExiter exiter;

    public ApiKeyCliRunner(GenerateCommand generateCommand, RevokeCommand revokeCommand, ListCommand listCommand,
            ProcessExiter exiter) {
        this.commands = Map.of(
                "generate", generateCommand,
                "revoke", revokeCommand,
                "list", listCommand);
        this.exiter = exiter;
    }

    @Override
    public void run(String... args) {
        if (args.length == 0) {
            return;
        }
        CliCommand command = commands.get(args[0]);
        if (command == null) {
            return;
        }
        int exitCode = command.execute(args, System.out, System.err);
        if (exitCode != 0) {
            exiter.exit(exitCode);
        }
    }
}
