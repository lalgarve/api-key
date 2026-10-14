package dev.leilaalgarve.apikey;

import dev.leilaalgarve.apikey.core.InvalidPermissionCatalogException;
import dev.leilaalgarve.apikey.core.PermissionCatalog;
import dev.leilaalgarve.apikey.issuance.GenerateCommand;
import dev.leilaalgarve.apikey.management.ListCommand;
import dev.leilaalgarve.apikey.management.RevokeCommand;
import dev.leilaalgarve.apikey.management.ValidateCommand;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Dispatches to the right {@link CliCommand} by {@code args[0]}, runs it against the real
 * stdout/stderr, and exits the process with the returned code when it's non-zero. An
 * unrecognized (or missing) command word is a silent no-op, same as before this class had more
 * than one command to choose from. Success (0) never calls {@link ProcessExiter#exit(int)} --
 * the JVM ends normally on its own, which is what keeps DeployoApiKeyApplicationTests' direct
 * call to {@code main()} safe (it exercises the success path).
 *
 * <p>An invalid permission catalog stops every command with exit 2 before it runs
 * (specs/013-key-permissions/spec.md FR3) -- checked here, once, instead of in each command.
 */
@Component
public class ApiKeyCliRunner implements CommandLineRunner {

    private final Map<String, CliCommand> commands;
    private final PermissionCatalog catalog;
    private final ProcessExiter exiter;

    /** Without a permission catalog, as before 1.1.0. */
    public ApiKeyCliRunner(GenerateCommand generateCommand, RevokeCommand revokeCommand, ListCommand listCommand,
            ValidateCommand validateCommand, ProcessExiter exiter) {
        this(generateCommand, revokeCommand, listCommand, validateCommand, PermissionCatalog.none(), exiter);
    }

    @Autowired
    public ApiKeyCliRunner(GenerateCommand generateCommand, RevokeCommand revokeCommand, ListCommand listCommand,
            ValidateCommand validateCommand, PermissionCatalog catalog, ProcessExiter exiter) {
        this.commands = Map.of(
                "generate", generateCommand,
                "revoke", revokeCommand,
                "list", listCommand,
                "validate", validateCommand);
        this.catalog = catalog;
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
        try {
            catalog.requireValid();
        } catch (InvalidPermissionCatalogException e) {
            CliOutput.println(System.err, e.getMessage());
            exiter.exit(2);
            return;
        }
        int exitCode = command.execute(args, System.in, System.out, System.err);
        if (exitCode != 0) {
            exiter.exit(exitCode);
        }
    }
}
