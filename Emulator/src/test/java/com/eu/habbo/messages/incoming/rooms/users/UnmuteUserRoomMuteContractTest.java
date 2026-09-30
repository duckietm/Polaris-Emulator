package com.eu.habbo.messages.incoming.rooms.users;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class UnmuteUserRoomMuteContractTest {

    @Test
    void unmuteAlsoLiftsTheRoomMute() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/eu/habbo/messages/incoming/rooms/users/UnmuteUserEvent.java"));

        int permission = source.indexOf("Permission.ACC_AMBASSADOR");
        int roomUnmute = source.indexOf("room.unmuteHabbo(target)");

        assertTrue(roomUnmute > -1, "Unmute must lift the room mute an ambassador gave");
        assertTrue(permission < roomUnmute, "Only ambassadors and staff reach the unmute");
    }
}
