package polaris;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.BufferedReader;
import java.io.StringReader;
import org.junit.jupiter.api.Test;

class EmulatorConsoleEofTest {

    @Test
    void endOfInputStopsTheConsoleLoop() throws Exception {
        BufferedReader reader = new BufferedReader(new StringReader(""));

        assertFalse(Emulator.readConsoleCommand(reader));
    }
}
