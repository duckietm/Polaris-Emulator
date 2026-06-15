package com.eu.habbo.messages.incoming.polls;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PollRoomScopeContractTest {
    @Test
    void pollHandlersRequireMatchingCurrentRoomPoll() throws Exception {
        assertRequiresMatchingRoomPoll("AnswerPollEvent.java");
        assertRequiresMatchingRoomPoll("CancelPollEvent.java");
        assertRequiresMatchingRoomPoll("GetPollDataEvent.java");
    }

    private void assertRequiresMatchingRoomPoll(String fileName) throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/messages/incoming/polls/" + fileName));
        int packetPollId = source.indexOf("int pollId = this.packet.readInt();");
        int pollLookup = source.indexOf("getPoll(pollId)");

        assertTrue(packetPollId >= 0, fileName + " must read the poll id from the packet");
        assertTrue(pollLookup >= 0, fileName + " must look up the requested poll explicitly");

        String guardedSection = source.substring(packetPollId, pollLookup);

        assertTrue(guardedSection.contains("getCurrentRoom()"),
                fileName + " must bind poll actions to the caller's current room");
        assertTrue(guardedSection.contains("room == null || room.getPollId() != pollId"),
                fileName + " must reject poll ids that are not active in the current room");
    }
}
