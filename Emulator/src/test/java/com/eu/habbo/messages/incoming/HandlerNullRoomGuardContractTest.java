package com.eu.habbo.messages.incoming;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Two handlers read the sender's room, and the client of another user, without checking them: sent outside a room, or
 * about a user whose connection was closing, they threw and each packet logged a stack trace.
 */
class HandlerNullRoomGuardContractTest {
    private static final Path INCOMING = Path.of("src/main/java/com/eu/habbo/messages/incoming");

    @Test
    void theAmbassadorAlertChecksTheRoomAndTheTargetsClient() throws Exception {
        String source = Files.readString(INCOMING.resolve("ambassadors/AmbassadorAlertCommandEvent.java"));

        assertFalse(source.contains("getCurrentRoom().getHabbo("), "the room is read once and checked");
        assertTrue(source.contains("if (room == null)"));
        assertTrue(source.contains("habbo.getClient() == null"));
    }

    @Test
    void theGuideInviteChecksTheRoomAndBothClients() throws Exception {
        String source = Files.readString(INCOMING.resolve("guides/GuideInviteUserEvent.java"));

        assertTrue(source.contains("if (tour == null || room == null) return;"));
        assertTrue(source.contains("participant.getClient() != null"));
        assertFalse(source.contains("tour.getNoob().getClient().sendResponse"));
    }
}
