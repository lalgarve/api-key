package io.deployo.apikey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.deployo.apikey.issuance.GenerateCommand;
import io.deployo.apikey.management.ListCommand;
import io.deployo.apikey.management.RevokeCommand;
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
        when(generateCommand.execute(any(), any(), any())).thenReturn(0);

        new ApiKeyCliRunner(generateCommand, revokeCommand, listCommand, failOnExit())
                .run("generate", "--client", "jogo-acoes");

        verifyNoInteractions(revokeCommand);
        verifyNoInteractions(listCommand);
    }

    @Test
    void dispatchesToRevokeCommand() {
        GenerateCommand generateCommand = mock(GenerateCommand.class);
        RevokeCommand revokeCommand = mock(RevokeCommand.class);
        ListCommand listCommand = mock(ListCommand.class);
        when(revokeCommand.execute(any(), any(), any())).thenReturn(0);

        new ApiKeyCliRunner(generateCommand, revokeCommand, listCommand, failOnExit()).run("revoke", "--id", "3");

        verifyNoInteractions(generateCommand);
        verifyNoInteractions(listCommand);
    }

    @Test
    void dispatchesToListCommand() {
        GenerateCommand generateCommand = mock(GenerateCommand.class);
        RevokeCommand revokeCommand = mock(RevokeCommand.class);
        ListCommand listCommand = mock(ListCommand.class);
        when(listCommand.execute(any(), any(), any())).thenReturn(0);

        new ApiKeyCliRunner(generateCommand, revokeCommand, listCommand, failOnExit()).run("list");

        verifyNoInteractions(generateCommand);
        verifyNoInteractions(revokeCommand);
    }

    @Test
    void exitsWithTheCommandsExitCodeWhenNonZero() {
        GenerateCommand generateCommand = mock(GenerateCommand.class);
        when(generateCommand.execute(any(), any(), any())).thenReturn(1);
        List<Integer> exitCalls = new ArrayList<>();

        new ApiKeyCliRunner(generateCommand, mock(RevokeCommand.class), mock(ListCommand.class), exitCalls::add)
                .run("generate");

        assertThat(exitCalls).containsExactly(1);
    }

    @Test
    void doesNotExitWhenTheCommandSucceeds() {
        GenerateCommand generateCommand = mock(GenerateCommand.class);
        when(generateCommand.execute(any(), any(), any())).thenReturn(0);
        List<Integer> exitCalls = new ArrayList<>();

        new ApiKeyCliRunner(generateCommand, mock(RevokeCommand.class), mock(ListCommand.class), exitCalls::add)
                .run("generate");

        assertThat(exitCalls).isEmpty();
    }

    @Test
    void unknownCommandWordDoesNothing() {
        GenerateCommand generateCommand = mock(GenerateCommand.class);
        RevokeCommand revokeCommand = mock(RevokeCommand.class);
        ListCommand listCommand = mock(ListCommand.class);

        new ApiKeyCliRunner(generateCommand, revokeCommand, listCommand, failOnExit())
                .run("--spring.datasource.url=jdbc:h2:mem:unused");

        verifyNoInteractions(generateCommand);
        verifyNoInteractions(revokeCommand);
        verifyNoInteractions(listCommand);
    }

    @Test
    void noArgumentsAtAllDoesNothing() {
        GenerateCommand generateCommand = mock(GenerateCommand.class);
        RevokeCommand revokeCommand = mock(RevokeCommand.class);
        ListCommand listCommand = mock(ListCommand.class);

        new ApiKeyCliRunner(generateCommand, revokeCommand, listCommand, failOnExit()).run();

        verifyNoInteractions(generateCommand);
        verifyNoInteractions(revokeCommand);
        verifyNoInteractions(listCommand);
    }

    private static ProcessExiter failOnExit() {
        return code -> {
            throw new AssertionError("exit(" + code + ") should not have been called");
        };
    }
}
