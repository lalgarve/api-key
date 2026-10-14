package dev.leilaalgarve.apikey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.leilaalgarve.apikey.core.PermissionCatalog;
import dev.leilaalgarve.apikey.core.PermissionCatalog.Entry;
import dev.leilaalgarve.apikey.issuance.GenerateCommand;
import dev.leilaalgarve.apikey.management.ListCommand;
import dev.leilaalgarve.apikey.management.RevokeCommand;
import dev.leilaalgarve.apikey.management.ValidateCommand;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Proves the dispatch-by-args[0] wiring and the exit-code wiring, without ever calling the real System.exit (see ProcessExiter). */
class ApiKeyCliRunnerTest {

    @Test
    void dispatchesToGenerateCommand() {
        GenerateCommand generateCommand = mock(GenerateCommand.class);
        RevokeCommand revokeCommand = mock(RevokeCommand.class);
        ListCommand listCommand = mock(ListCommand.class);
        ValidateCommand validateCommand = mock(ValidateCommand.class);
        when(generateCommand.execute(any(), any(), any(), any())).thenReturn(0);

        new ApiKeyCliRunner(generateCommand, revokeCommand, listCommand, validateCommand, failOnExit())
                .run("generate", "--client", "jogo-acoes");

        verifyNoInteractions(revokeCommand);
        verifyNoInteractions(listCommand);
        verifyNoInteractions(validateCommand);
    }

    @Test
    void dispatchesToRevokeCommand() {
        GenerateCommand generateCommand = mock(GenerateCommand.class);
        RevokeCommand revokeCommand = mock(RevokeCommand.class);
        ListCommand listCommand = mock(ListCommand.class);
        ValidateCommand validateCommand = mock(ValidateCommand.class);
        when(revokeCommand.execute(any(), any(), any(), any())).thenReturn(0);

        new ApiKeyCliRunner(generateCommand, revokeCommand, listCommand, validateCommand, failOnExit())
                .run("revoke", "--id", "3");

        verifyNoInteractions(generateCommand);
        verifyNoInteractions(listCommand);
        verifyNoInteractions(validateCommand);
    }

    @Test
    void dispatchesToListCommand() {
        GenerateCommand generateCommand = mock(GenerateCommand.class);
        RevokeCommand revokeCommand = mock(RevokeCommand.class);
        ListCommand listCommand = mock(ListCommand.class);
        ValidateCommand validateCommand = mock(ValidateCommand.class);
        when(listCommand.execute(any(), any(), any(), any())).thenReturn(0);

        new ApiKeyCliRunner(generateCommand, revokeCommand, listCommand, validateCommand, failOnExit()).run("list");

        verifyNoInteractions(generateCommand);
        verifyNoInteractions(revokeCommand);
        verifyNoInteractions(validateCommand);
    }

    @Test
    void dispatchesToValidateCommand() {
        GenerateCommand generateCommand = mock(GenerateCommand.class);
        RevokeCommand revokeCommand = mock(RevokeCommand.class);
        ListCommand listCommand = mock(ListCommand.class);
        ValidateCommand validateCommand = mock(ValidateCommand.class);
        when(validateCommand.execute(any(), any(), any(), any())).thenReturn(0);

        new ApiKeyCliRunner(generateCommand, revokeCommand, listCommand, validateCommand, failOnExit()).run("validate");

        verifyNoInteractions(generateCommand);
        verifyNoInteractions(revokeCommand);
        verifyNoInteractions(listCommand);
    }

    @Test
    void exitsWithTheCommandsExitCodeWhenNonZero() {
        GenerateCommand generateCommand = mock(GenerateCommand.class);
        when(generateCommand.execute(any(), any(), any(), any())).thenReturn(1);
        List<Integer> exitCalls = new ArrayList<>();

        new ApiKeyCliRunner(generateCommand, mock(RevokeCommand.class), mock(ListCommand.class),
                mock(ValidateCommand.class), exitCalls::add)
                .run("generate");

        assertThat(exitCalls).containsExactly(1);
    }

    @Test
    void doesNotExitWhenTheCommandSucceeds() {
        GenerateCommand generateCommand = mock(GenerateCommand.class);
        when(generateCommand.execute(any(), any(), any(), any())).thenReturn(0);
        List<Integer> exitCalls = new ArrayList<>();

        new ApiKeyCliRunner(generateCommand, mock(RevokeCommand.class), mock(ListCommand.class),
                mock(ValidateCommand.class), exitCalls::add)
                .run("generate");

        assertThat(exitCalls).isEmpty();
    }

    @Test
    void unknownCommandWordDoesNothing() {
        GenerateCommand generateCommand = mock(GenerateCommand.class);
        RevokeCommand revokeCommand = mock(RevokeCommand.class);
        ListCommand listCommand = mock(ListCommand.class);
        ValidateCommand validateCommand = mock(ValidateCommand.class);

        new ApiKeyCliRunner(generateCommand, revokeCommand, listCommand, validateCommand, failOnExit())
                .run("--not-a-command");

        verifyNoInteractions(generateCommand);
        verifyNoInteractions(revokeCommand);
        verifyNoInteractions(listCommand);
        verifyNoInteractions(validateCommand);
    }

    @Test
    void noArgumentsAtAllDoesNothing() {
        GenerateCommand generateCommand = mock(GenerateCommand.class);
        RevokeCommand revokeCommand = mock(RevokeCommand.class);
        ListCommand listCommand = mock(ListCommand.class);
        ValidateCommand validateCommand = mock(ValidateCommand.class);

        new ApiKeyCliRunner(generateCommand, revokeCommand, listCommand, validateCommand, failOnExit()).run();

        verifyNoInteractions(generateCommand);
        verifyNoInteractions(revokeCommand);
        verifyNoInteractions(listCommand);
        verifyNoInteractions(validateCommand);
    }

    @Test
    void anInvalidPermissionCatalogExitsTwoBeforeAnyCommandRuns() {
        GenerateCommand generateCommand = mock(GenerateCommand.class);
        RevokeCommand revokeCommand = mock(RevokeCommand.class);
        PermissionCatalog withoutDefault = new PermissionCatalog(List.of(new Entry("email:send", false)));
        List<Integer> exitCalls = new ArrayList<>();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;

        System.setErr(new PrintStream(errBytes, true, StandardCharsets.UTF_8));
        try {
            new ApiKeyCliRunner(generateCommand, revokeCommand, mock(ListCommand.class),
                    mock(ValidateCommand.class), withoutDefault, exitCalls::add)
                    .run("revoke", "--id", "3");
        } finally {
            System.setErr(originalErr);
        }

        assertThat(exitCalls).containsExactly(2);
        assertThat(errBytes.toString(StandardCharsets.UTF_8))
                .isEqualTo("Error: api-key.permissions must mark at least one permission as default.\n");
        verifyNoInteractions(generateCommand);
        verifyNoInteractions(revokeCommand);
    }

    @Test
    void anInvalidPermissionCatalogDoesNotTurnAnUnknownCommandIntoAnError() {
        PermissionCatalog withoutDefault = new PermissionCatalog(List.of(new Entry("email:send", false)));

        new ApiKeyCliRunner(mock(GenerateCommand.class), mock(RevokeCommand.class), mock(ListCommand.class),
                mock(ValidateCommand.class), withoutDefault, failOnExit())
                .run("--not-a-command");
    }

    private static ProcessExiter failOnExit() {
        return code -> {
            throw new AssertionError("exit(" + code + ") should not have been called");
        };
    }
}
