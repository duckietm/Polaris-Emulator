package com.eu.habbo.messages.incoming.rooms.users;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomUserSignGuardContractTest {

    private static String source() throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/messages/incoming/rooms/users/RoomUserSignEvent.java"));
    }

    @Test
    void signIdIsValidatedBeforeRoomStateAndWiredTriggers() throws Exception {
        String source = source();

        int signRead = source.indexOf("int signId = this.packet.readInt()");
        int guard = source.indexOf("signId < MIN_SIGN_ID || signId > MAX_SIGN_ID", signRead);
        int status = source.indexOf("setStatus(RoomUnitStatus.SIGN", signRead);
        int wired = source.indexOf("WiredManager.triggerUserPerformsAction", signRead);

        assertTrue(signRead > -1, "Sign handler must read the client-provided sign id");
        assertTrue(guard > signRead, "Sign handler must reject out-of-range sign ids");
        assertTrue(guard < status, "Sign id must be validated before status mutation");
        assertTrue(guard < wired, "Sign id must be validated before wired triggers");
    }

    @Test
    void voteCountersOnlyReceiveValidatedSigns() throws Exception {
        String source = source();

        int guard = source.indexOf("signId < MIN_SIGN_ID || signId > MAX_SIGN_ID");
        int vote = source.indexOf(".vote(room, userId, signId)");

        assertTrue(guard > -1, "Sign id range guard must exist");
        assertTrue(vote > guard, "Vote counters must only receive signs after the range guard");
    }
}
