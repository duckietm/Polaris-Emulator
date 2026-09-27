package com.eu.habbo.messages.incoming.housekeeping;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class HousekeepingAccessContractTest {
    private static final Path BASE = Path.of("src/main/java/com/eu/habbo/messages/incoming/housekeeping");

    @Test
    void everyHandlerAnswersARefusalInsteadOfStayingSilent() throws Exception {
        List<Path> handlers;
        try (Stream<Path> files = Files.list(BASE)) {
            handlers = files.filter(path -> path.getFileName().toString().endsWith("Event.java"))
                    .toList();
        }

        assertFalse(handlers.isEmpty());

        for (Path handler : handlers) {
            String source = Files.readString(handler);
            assertTrue(
                    source.contains("HousekeepingAccess.check(this.client)"),
                    handler.getFileName()
                            + " must gate on HousekeepingAccess.check so a refused client gets an answer");
            assertFalse(
                    source.contains("hasPermission(Permission.ACC_HOUSEKEEPING)"),
                    handler.getFileName() + " must not check the permission silently");
        }
    }

    @Test
    void deletingARoomGivesTheFurniBackLikeTheOwnerDoes() throws Exception {
        String source = Files.readString(BASE.resolve("HousekeepingDeleteRoomEvent.java"));

        assertTrue(
                source.contains("loadRoom(roomId, true)"),
                "the room must be loaded with its items before it is deleted");
        assertTrue(
                source.contains("RoomDeleter.delete("), "housekeeping must delete through the same path as the owner");
    }
}
