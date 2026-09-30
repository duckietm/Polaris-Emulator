package com.eu.habbo.messages.incoming.rooms.users;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RoomUserMuteGuardContractTest {

    private static String source() throws Exception {
        return Files.readString(
                Path.of("src/main/java/com/eu/habbo/messages/incoming/rooms/users/RoomUserMuteEvent.java"));
    }

    @Test
    void muteDurationIsBoundedBeforeApplyingMute() throws Exception {
        String source = source();

        int targetLookup = source.indexOf("Habbo habbo = room.getHabbo(userId)");
        int durationGuard = source.indexOf(
                "minutes < MIN_MUTE_MINUTES || minutes > maxMinutes(this.client.getHabbo())", targetLookup);
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

    @Test
    void onlyStaffAndAmbassadorsGetTheLongMutes() throws Exception {
        String source = source();

        assertTrue(source.contains("MAX_MUTE_MINUTES = 1440"), "Room rights keep the 24 hour cap");
        assertTrue(source.contains("MAX_STAFF_MUTE_MINUTES = 4320"), "Ambassadors and staff reach Flash's 72 hours");
        assertTrue(
                source.contains(
                        "moderator.hasPermission(\"cmd_mute\") || moderator.hasPermission(Permission.ACC_AMBASSADOR)"),
                "Only cmd_mute and ambassadors get the longer cap");
    }
}
