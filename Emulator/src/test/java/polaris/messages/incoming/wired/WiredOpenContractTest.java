package polaris.messages.incoming.wired;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Placing a wired box and being handed its window is a conversation with two halves, and neither of
 * them existed: the server never announced the box, and the client's answer had no handler.
 */
class WiredOpenContractTest {
    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/" + path));
    }

    @Test
    void puttingDownAWiredBoxAnnouncesIt() throws Exception {
        String place = source("polaris/messages/incoming/rooms/items/RoomPlaceItemEvent.java");

        assertTrue(place.contains("new WiredOpenComposer(item)"));
        assertTrue(place.contains("item instanceof InteractionWired && room.canInspectWired(this.client.getUser())"));
        // Only once the furniture is actually down.
        assertTrue(place.indexOf("room.placeFloorFurniAt(item, tile, rotation")
                < place.indexOf("new WiredOpenComposer(item)"));
    }

    @Test
    void theAnswerIsRegisteredOnTheHeaderTheClientUses() throws Exception {
        String incoming = source("polaris/messages/incoming/Incoming.java");
        String registry = source("polaris/messages/PacketManager.java");

        assertTrue(incoming.contains("WiredOpenEvent = 768"));
        assertTrue(registry.contains("Incoming.WiredOpenEvent, WiredOpenEvent.class"));
    }

    @Test
    void theWindowIsFilledWithWhateverKindOfBoxItIs() throws Exception {
        String handler = source("polaris/messages/incoming/wired/WiredOpenEvent.java");

        assertTrue(handler.contains("new WiredTriggerDataComposer(trigger, room)"));
        assertTrue(handler.contains("new WiredEffectDataComposer(effect, room)"));
        assertTrue(handler.contains("new WiredConditionDataComposer(condition, room)"));
    }

    @Test
    void aPacketThatArrivesOnItsOwnIsStillChecked() throws Exception {
        String handler = source("polaris/messages/incoming/wired/WiredOpenEvent.java");

        assertTrue(handler.contains("if (room == null || !room.canInspectWired(this.client.getUser())) return;"));
        assertTrue(handler.indexOf("canInspectWired") < handler.indexOf("room.getUserItem("));
    }
}
