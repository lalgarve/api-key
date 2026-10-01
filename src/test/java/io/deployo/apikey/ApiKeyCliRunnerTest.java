package io.deployo.apikey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.deployo.apikey.issuance.GenerateCommand;
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
        when(generateCommand.execute(any(), any(), any())).thenReturn(0);

        new ApiKeyCliRunner(generateCommand, revokeCommand, failOnExit()).run("generate", "--client", "jogo-acoes");

        verifyNoInteractions(revokeCommand);
    }

    @Test
    void dispatchesToRevokeCommand() {
        GenerateCommand generateCommand = mock(GenerateCommand.class);
        RevokeCommand revokeCommand = mock(RevokeCommand.class);
        when(revokeCommand.execute(any(), any(), any())).thenReturn(0);

        new ApiKeyCliRunner(generateCommand, revokeCommand, failOnExit()).run("revoke", "--id", "3");

        verifyNoInteractions(generateCommand);
    }

    @Test
    void exitsWithTheCommandsExitCodeWhenNonZero() {
        GenerateCommand generateCommand = mock(GenerateCommand.class);
        when(generateCommand.execute(any(), any(), any())).thenReturn(1);
        List<Integer> exitCalls = new ArrayList<>();

        new ApiKeyCliRunner(generateCommand, mock(RevokeCommand.class), exitCalls::add).run("generate");

        assertThat(exitCalls).containsExactly(1);
    }

    @Test
    void doesNotExitWhenTheCommandSucceeds() {
        GenerateCommand generateCommand = mock(GenerateCommand.class);
        when(generateCommand.execute(any(), any(), any())).thenReturn(0);
        List<Integer> exitCalls = new ArrayList<>();

        new ApiKeyCliRunner(generateCommand, mock(RevokeCommand.class), exitCalls::add).run("generate");

        assertThat(exitCalls).isEmpty();
    }

    @Test
    void unknownCommandWordDoesNothing() {
        GenerateCommand generateCommand = mock(GenerateCommand.class);
        RevokeCommand revokeCommand = mock(RevokeCommand.class);

        new ApiKeyCliRunner(generateCommand, revokeCommand, failOnExit())
                .run("--spring.datasource.url=jdbc:h2:mem:unused");

        verifyNoInteractions(generateCommand);
        verifyNoInteractions(revokeCommand);
    }

    @Test
    void noArgumentsAtAllDoesNothing() {
        GenerateCommand generateCommand = mock(GenerateCommand.class);
        RevokeCommand revokeCommand = mock(RevokeCommand.class);

        new ApiKeyCliRunner(generateCommand, revokeCommand, failOnExit()).run();

        verifyNoInteractions(generateCommand);
        verifyNoInteractions(revokeCommand);
    }

    private static ProcessExiter failOnExit() {
        return code -> {
            throw new AssertionError("exit(" + code + ") should not have been called");
        };
    }
}
