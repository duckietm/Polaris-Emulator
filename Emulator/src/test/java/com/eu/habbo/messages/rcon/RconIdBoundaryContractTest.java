package com.eu.habbo.messages.rcon;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RconIdBoundaryContractTest {
    private static String source(String file) throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/messages/rcon/" + file));
    }

    @Test
    void ticketIdsAreValidatedBeforeCreatingTheTicket() throws Exception {
        String ticket = source("CreateModToolTicket.java");

        assertTrue(ticket.contains("@Positive(message = \"invalid sender\")"));
        assertTrue(ticket.contains("@Positive(message = \"invalid reported user\")"));
        assertTrue(ticket.contains("@PositiveOrZero(message = \"invalid room\")"));
        assertTrue(ticket.contains("RconUserLookup.userExists(json.sender_id)"));
        assertTrue(ticket.contains("RconUserLookup.userExists(json.reported_id)"));
        assertTrue(ticket.contains("getRoomManager().loadRoom(json.reported_room_id)"));
    }

    @Test
    void mottoAndMandatoryResourceIdsUseBeanValidation() throws Exception {
        String motto = source("SetMotto.java");
        String clothing = source("GiveUserClothing.java");
        String bundle = source("SendRoomBundle.java");

        assertTrue(motto.contains("@Positive(message = \"invalid user\")"));
        assertTrue(clothing.contains("@Positive(message = \"invalid clothing\")"));
        assertTrue(bundle.contains("@Positive(message = \"invalid catalog page\")"));
    }

    @Test
    void disconnectTreatsZeroAsAnInvalidIdAndUsesOnlyARealUsernameFallback() throws Exception {
        String disconnect = source("DisconnectUser.java");

        assertTrue(disconnect.contains("if (json.user_id > 0)"));
        assertTrue(disconnect.contains("json.username != null && !json.username.isBlank()"));
    }
}
