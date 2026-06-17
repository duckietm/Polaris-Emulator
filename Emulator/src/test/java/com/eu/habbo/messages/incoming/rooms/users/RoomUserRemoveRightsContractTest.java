package com.eu.habbo.messages.incoming.rooms.users;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class RoomUserRemoveRightsContractTest {
    private static final Path SOURCE = Path.of(
            "src/main/java/com/eu/habbo/messages/incoming/rooms/users/RoomUserRemoveRightsEvent.java");

    @Test
    void removeRightsBatchIsBoundedAndRequiresCompletePayload() throws IOException {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("private static final int MAX_RIGHTS_REMOVALS = 100;"));
        assertTrue(source.contains("PacketGuard.isCountInRange(amount, 1, MAX_RIGHTS_REMOVALS)"));
        assertTrue(source.contains("PacketGuard.hasFixedWidthEntries(this.packet, amount, BYTES_PER_USER_ID)"));

        int guardIndex = source.indexOf("PacketGuard.isCountInRange(amount, 1, MAX_RIGHTS_REMOVALS)");
        int payloadIndex = source.indexOf("PacketGuard.hasFixedWidthEntries(this.packet, amount, BYTES_PER_USER_ID)");
        int readIndex = source.indexOf("int userId = this.packet.readInt();");
        int removeIndex = source.indexOf("room.removeRights(userId);");

        assertTrue(guardIndex < readIndex, "batch size should be validated before reading user ids");
        assertTrue(payloadIndex < readIndex, "payload length should be validated before reading user ids");
        assertTrue(readIndex < removeIndex, "rights should only be removed after reading a validated user id");
    }
}
