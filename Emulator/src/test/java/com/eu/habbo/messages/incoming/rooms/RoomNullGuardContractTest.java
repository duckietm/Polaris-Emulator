package com.eu.habbo.messages.incoming.rooms;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Moving a pet and clicking a random-state furni read the current room without checking it was there. */
class RoomNullGuardContractTest {

    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/messages/incoming/rooms/" + path));
    }

    @Test
    void movingAPetChecksTheRoomFirst() throws Exception {
        String move = source("pets/MovePetEvent.java");

        int guard = move.indexOf("if (room == null) return;");
        assertTrue(guard > 0 && guard < move.indexOf("room.getPet("));
    }

    @Test
    void aRandomStateClickChecksTheRoomFirst() throws Exception {
        String use = source("items/UseRandomStateItemEvent.java");

        int guard = use.indexOf("if (room == null) return;");
        assertTrue(guard > 0 && guard < use.indexOf("room.getHabboItem("));
    }
}
