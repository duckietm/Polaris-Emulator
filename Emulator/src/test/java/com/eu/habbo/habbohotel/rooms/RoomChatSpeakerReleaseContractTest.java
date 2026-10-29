package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The texts a say's wired queued for after the speaker's line are thread-local. When sending the line threw,
 * they stayed on that thread and went out after the next line it handled, from another room or user.
 */
class RoomChatSpeakerReleaseContractTest {

    @Test
    void theQueuedTextsAreReleasedEvenWhenSendingTheLineFails() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/rooms/RoomChatManager.java"));

        int sendTalk = source.indexOf("this.handleTalk(habbo, roomChatMessage");
        int finallyBlock = source.indexOf("} finally {", sendTalk);
        int release = source.indexOf("WiredBoxFlashes.releaseAfterSpeaker();", sendTalk);

        assertTrue(sendTalk > -1, "the line is sent by handleTalk");
        assertTrue(finallyBlock > sendTalk, "sending the line is followed by a finally block");
        assertTrue(release > finallyBlock, "the queued texts are released inside that finally block");
    }
}
