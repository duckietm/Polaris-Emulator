package com.eu.habbo.messages.rcon;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangeRoomOwnerContractTest {
    private static String source() throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/messages/rcon/ChangeRoomOwner.java"));
    }

    @Test
    void validatesRoomAndUserBeforeChangingOwnership() throws Exception {
        String source = source();

        assertTrue(source.contains("json.room_id <= 0 || json.user_id <= 0"),
                "Room owner changes must reject invalid identifiers");
        assertTrue(source.contains("getHabboInfo(json.user_id)"),
                "Room owner changes must resolve the canonical user from storage");
        assertTrue(source.contains("HABBO_NOT_FOUND"),
                "Room owner changes must report missing target users");
        assertTrue(source.contains("ROOM_NOT_FOUND"),
                "Room owner changes must report missing rooms");
    }

    @Test
    void doesNotTrustClientSuppliedOwnerNames() throws Exception {
        String source = source();

        assertTrue(source.contains("setOwnerName(owner.getUsername())"),
                "Room owner changes must use the stored username");
        assertFalse(source.contains("setOwnerName(json.username)"),
                "Room owner changes must not trust JSON usernames");
    }
}
