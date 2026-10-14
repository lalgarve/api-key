package dev.leilaalgarve.apikey;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CliArgsTest {

    @Test
    void parseCommaListStripsItemsAndDropsBlanksAndRepeats() {
        assertThat(CliArgs.parseCommaList(" b , a,,b ,  ")).containsExactly("b", "a");
    }

    @Test
    void parseCommaListOfOnlyCommasIsEmpty() {
        assertThat(CliArgs.parseCommaList(",,")).isEmpty();
        assertThat(CliArgs.parseCommaList("")).isEmpty();
    }
}
