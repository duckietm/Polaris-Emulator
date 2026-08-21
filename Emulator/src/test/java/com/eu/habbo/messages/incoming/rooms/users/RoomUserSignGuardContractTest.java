package com.eu.habbo.messages.incoming.rooms.users;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RoomUserSignGuardContractTest {

    @Test
    void supportsEverySignExposedByTheAvatarMenu() {
        assertTrue(RoomUserSignEvent.isValidSignId(0));
        assertTrue(RoomUserSignEvent.isValidSignId(10));
        assertTrue(RoomUserSignEvent.isValidSignId(11));
        assertTrue(RoomUserSignEvent.isValidSignId(17));
        assertFalse(RoomUserSignEvent.isValidSignId(-1));
        assertFalse(RoomUserSignEvent.isValidSignId(18));
    }

    private static String source() throws Exception {
        return Files.readString(
                Path.of("src/main/java/com/eu/habbo/messages/incoming/rooms/users/RoomUserSignEvent.java"));
    }

    @Test
    void signIdIsValidatedBeforeRoomStateAndWiredTriggers() throws Exception {
        String source = source();

        int signRead = source.indexOf("int signId = this.packet.readInt()");
        int guard = source.indexOf("if (!isValidSignId(signId))", signRead);
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

        int guard = source.indexOf("if (!isValidSignId(signId))");
        int vote = source.indexOf(".vote(room, userId, signId)");

        assertTrue(guard > -1, "Sign id range guard must exist");
        assertTrue(vote > guard, "Vote counters must only receive signs after the range guard");
    }
}
