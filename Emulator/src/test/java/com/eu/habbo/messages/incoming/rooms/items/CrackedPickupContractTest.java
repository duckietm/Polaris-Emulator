package com.eu.habbo.messages.incoming.rooms.items;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** A crackable that had already given its reward could be picked up before it vanished, and cracked again. */
class CrackedPickupContractTest {

    private static String source(String name) throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/messages/incoming/rooms/items/" + name));
    }

    @Test
    void neitherPickupTakesACrackedCrackable() throws Exception {
        String single = source("RoomPickupItemEvent.java");
        String chooser = source("RoomPickupChooserEvent.java");

        int guard = single.indexOf("if (item instanceof InteractionCrackable crackable && crackable.cracked) return;");
        assertTrue(guard > 0 && guard < single.indexOf("room.pickUpItem("));
        assertTrue(
                chooser.contains("if (item instanceof InteractionCrackable crackable && crackable.cracked) continue;"));
    }
}
