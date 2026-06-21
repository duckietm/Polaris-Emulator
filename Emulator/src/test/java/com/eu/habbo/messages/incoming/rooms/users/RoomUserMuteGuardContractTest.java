package com.eu.habbo.messages.incoming.rooms.users;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomUserMuteGuardContractTest {

    private static String source() throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/messages/incoming/rooms/users/RoomUserMuteEvent.java"));
    }

    @Test
    void muteDurationIsBoundedBeforeApplyingMute() throws Exception {
        String source = source();

        int targetLookup = source.indexOf("Habbo habbo = room.getHabbo(userId)");
        int durationGuard = source.indexOf("minutes < MIN_MUTE_MINUTES || minutes > MAX_MUTE_MINUTES", targetLookup);
        int muteCall = source.indexOf("room.muteHabbo(habbo, minutes)", targetLookup);

        assertTrue(targetLookup > -1, "Mute handler must resolve the room target");
        assertTrue(durationGuard > targetLookup, "Mute handler must bound client-provided minutes");
        assertTrue(durationGuard < muteCall, "Mute duration must be validated before mutating room state");
    }

    @Test
    void unkickableTargetsCannotBeMutedThroughRoomPacket() throws Exception {
        String source = source();

        int unkickableGuard = source.indexOf("habbo.hasPermission(Permission.ACC_UNKICKABLE)");
        int muteCall = source.indexOf("room.muteHabbo(habbo, minutes)");

        assertTrue(unkickableGuard > -1, "Room mute must respect ACC_UNKICKABLE like kick and ban");
        assertTrue(unkickableGuard < muteCall, "Unkickable targets must be rejected before muting");
    }
}
