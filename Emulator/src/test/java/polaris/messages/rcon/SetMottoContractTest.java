package polaris.messages.rcon;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SetMottoContractTest {
    private static String setMottoSource() throws Exception {
        return Files.readString(Path.of("src/main/java/polaris/messages/rcon/SetMotto.java"));
    }

    @Test
    void onlineMottoUpdateDoesNotRequireCurrentRoom() throws Exception {
        String source = setMottoSource();

        assertTrue(
                source.contains("getCurrentRoom() != null"),
                "RCON SetMotto must not fail for online users outside a room");
    }
}
