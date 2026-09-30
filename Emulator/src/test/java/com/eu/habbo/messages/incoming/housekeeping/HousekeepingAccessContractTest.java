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
    void everyHandlerGoesThroughTheBaseGateFirst() throws Exception {
        List<Path> handlers;
        try (Stream<Path> files = Files.list(BASE)) {
            handlers = files.filter(path -> {
                        try {
                            return Files.readString(path).contains("public void handle()");
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .toList();
        }

        assertFalse(handlers.isEmpty());

        for (Path handler : handlers) {
            String source = Files.readString(handler);
            String name = handler.getFileName().toString();
            String body = source.substring(source.indexOf("public void handle()"));
            String firstStatement = body.substring(body.indexOf('{') + 1).strip();

            assertTrue(
                    source.contains("extends HousekeepingHandler"),
                    name + " must extend HousekeepingHandler, the one gate of the panel");
            assertTrue(
                    firstStatement.startsWith("if (!this.allowed())"),
                    name + " must start handle() with if (!this.allowed()) return; so nothing runs before the gate");
            assertFalse(
                    source.contains("hasPermission(Permission.ACC_HOUSEKEEPING)"),
                    name + " must not check the permission silently");
        }
    }

    @Test
    void theGateChecksAccessThenTheLockdownThenTheArea() throws Exception {
        String access = Files.readString(BASE.resolve("HousekeepingAccess.java"));
        String base = Files.readString(BASE.resolve("HousekeepingHandler.java"));

        assertTrue(base.contains("protected final boolean allowed()"), "the gate cannot be overridden");
        assertTrue(base.contains("HousekeepingAccess.check(this.client, this.requiredPermission())"));

        int accessCheck = access.indexOf("Permission.ACC_HOUSEKEEPING");
        int lockdown = access.indexOf("HousekeepingLockdown.isLocked()");
        int area = access.indexOf("hasPermission(areaPermission)");

        assertTrue(accessCheck > 0 && lockdown > accessCheck && area > lockdown);
        assertTrue(
                access.contains("!HousekeepingTargetRankGuard.isTopRank(client.getHabbo())"),
                "the lockdown lets only the highest rank in, so it can always be switched off");
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
