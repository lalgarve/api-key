package io.deployo.apikey.issuance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Proves the exit-code wiring without ever calling the real System.exit (see ProcessExiter). */
class ApiKeyCliRunnerTest {

    @Test
    void exitsWithTheCommandsExitCodeWhenNonZero() {
        GenerateCommand command = mock(GenerateCommand.class);
        when(command.execute(any(), any(), any())).thenReturn(1);
        List<Integer> exitCalls = new ArrayList<>();

        new ApiKeyCliRunner(command, exitCalls::add).run("generate");

        assertThat(exitCalls).containsExactly(1);
    }

    @Test
    void doesNotExitWhenTheCommandSucceeds() {
        GenerateCommand command = mock(GenerateCommand.class);
        when(command.execute(any(), any(), any())).thenReturn(0);
        List<Integer> exitCalls = new ArrayList<>();

        new ApiKeyCliRunner(command, exitCalls::add).run("generate");

        assertThat(exitCalls).isEmpty();
    }
}
