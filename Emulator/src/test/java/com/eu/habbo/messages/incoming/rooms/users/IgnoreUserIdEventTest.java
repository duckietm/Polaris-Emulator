package com.eu.habbo.messages.incoming.rooms.users;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class IgnoreUserIdEventTest {
    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/" + path));
    }

    @Test
    void theHeaderMatchesTheRendererAndIsRegistered() throws Exception {
        assertTrue(source("com/eu/habbo/messages/incoming/Incoming.java").contains("IgnoreUserIdEvent = 3314"));
        assertTrue(source("com/eu/habbo/messages/PacketManager.java")
                .contains("Incoming.IgnoreUserIdEvent, IgnoreUserIdEvent.class"));
    }

    @Test
    void itRefusesTheSenderAndAnswersOnlyForSomebodyInTheRoom() throws Exception {
        String handler = source("com/eu/habbo/messages/incoming/rooms/users/IgnoreUserIdEvent.java");

        assertTrue(handler.contains("userId <= 0 || self == null || userId == self.getHabboInfo().getId()"));
        assertTrue(handler.contains("self.getHabboStats().ignoreUser(this.client, userId)"));
        assertTrue(handler.contains("Habbo target = room.getHabbo(userId);"));
    }

    @Test
    void ignoringSomebodyWhoIsOfflineNoLongerThrows() throws Exception {
        String stats = source("com/eu/habbo/habbohotel/users/HabboStats.java");

        assertTrue(stats.contains("if (target != null"));
        assertTrue(stats.contains("&& target.hasPermission(Permission.ACC_UNIGNORABLE)) {"));
    }
}
