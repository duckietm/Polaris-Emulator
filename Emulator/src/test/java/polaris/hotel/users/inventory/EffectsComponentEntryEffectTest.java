package polaris.hotel.users.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class EffectsComponentEntryEffectTest {

    @Test
    void commandedEffectWinsOverTheWornOne() {
        assertEquals(112, EffectsComponent.entryEffect(112, 0));
        assertEquals(112, EffectsComponent.entryEffect(112, 13));
    }

    @Test
    void withoutACommandedEffectTheWornOneIsKept() {
        assertEquals(13, EffectsComponent.entryEffect(0, 13));
        assertEquals(0, EffectsComponent.entryEffect(0, 0));
    }

    @Test
    void theCommandRemembersAndRoomEntryUsesIt() throws IOException {
        String command = Files.readString(Path.of("src/main/java/polaris/hotel/commands/EnableCommand.java"));
        String entry = Files.readString(Path.of("src/main/java/polaris/hotel/rooms/RoomManager.java"));
        String picker =
                Files.readString(Path.of("src/main/java/polaris/messages/incoming/users/EnableEffectEvent.java"));

        assertTrue(command.contains("setCommandEffect(effectId)"));
        assertTrue(entry.contains("getEffectsComponent().entryEffect()"));
        assertTrue(picker.contains("setCommandEffect(0)"));
    }
}
