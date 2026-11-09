package polaris.messages.incoming.wheel;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** A prize that failed to save must not get every other prize disabled with it. */
class WheelAdminSavePrizesContractTest {

    @Test
    void removedPrizesAreDisabledOnlyWhenEverySaveWentThrough() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/polaris/messages/incoming/wheel/WheelAdminSavePrizesEvent.java"));

        int counted = source.indexOf("else failedSaves++;");
        int guard = source.indexOf("if (failedSaves == 0) {");
        int disable = source.indexOf("wheel.disablePrizesNotIn(keptIds);");

        assertTrue(counted > 0, "a save that returned no id is counted as failed");
        assertTrue(guard > counted && disable > guard, "and the removals wait for a clean save");
    }
}
