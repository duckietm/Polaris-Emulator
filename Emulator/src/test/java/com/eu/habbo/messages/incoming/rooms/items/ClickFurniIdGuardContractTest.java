package com.eu.habbo.messages.incoming.rooms.items;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClickFurniIdGuardContractTest {
    @Test
    void rejectsNonPositiveClientIdsBeforeRoomLookup() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/eu/habbo/messages/incoming/rooms/items/ClickFurniEvent.java"));

        int read = source.indexOf("int itemId = this.packet.readInt()");
        int guard = source.indexOf("RoomItemInputGuard.isPositiveId(itemId)", read);
        int lookup = source.indexOf("room.getHabboItem(itemId)", read);

        assertTrue(read > -1, "ClickFurniEvent must preserve the exact client-provided id");
        assertTrue(guard > read, "ClickFurniEvent must validate the id after reading it");
        assertTrue(guard < lookup, "ClickFurniEvent must reject invalid ids before room lookup");
        assertFalse(source.contains("Math.abs"), "negative ids must not alias valid room item ids");
    }
}
