package com.eu.habbo.habbohotel.mentions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.rooms.RoomChatMessage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class MentionDeliveryContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/eu/habbo");

    @Test
    void aChatLineStartsUndelivered() throws Exception {
        assertEquals(boolean.class, RoomChatMessage.class.getField("delivered").getType());
        assertTrue(Files.readString(MAIN.resolve("habbohotel/rooms/RoomChatMessage.java"))
                .contains("public boolean delivered = false;"));
    }

    @Test
    void theRoomMarksTheLineOnlyWhereItIsSentToTheRoom() throws Exception {
        String chat = Files.readString(MAIN.resolve("habbohotel/rooms/RoomChatManager.java"));

        // Only what lies between the two statements matters, not how deep the block is indented.
        assertTrue(
                Pattern.compile("roomChatMessage\\.delivered = true;\\s*this\\.handleTalk\\(")
                        .matcher(chat)
                        .find(),
                "talk must mark the line as delivered right before it reaches the room");
        assertTrue(
                Pattern.compile("roomChatMessage\\.delivered = true;\\s*this\\.handleShout\\(")
                        .matcher(chat)
                        .find(),
                "shout must mark the line as delivered right before it reaches the room");
    }

    @Test
    void mentionsAreOnlyProcessedForDeliveredLines() throws Exception {
        for (String handler : new String[] {"RoomUserTalkEvent.java", "RoomUserShoutEvent.java"}) {
            String source = Files.readString(MAIN.resolve("messages/incoming/rooms/users/" + handler));
            int gate = source.indexOf("if (message.delivered)");
            int process = source.indexOf("getMentionManager()");

            assertTrue(gate >= 0 && process > gate, handler + " must only process mentions for a delivered line");
        }
    }

    @Test
    void markReadAndDeleteAreNeverDroppedByACooldown() throws Exception {
        for (String handler : new String[] {"MarkMentionsReadEvent.java", "DeleteMentionEvent.java"}) {
            String source = Files.readString(MAIN.resolve("messages/incoming/mentions/" + handler));

            assertFalse(source.contains("tryAcquire"), handler + " must always apply what the client already showed");
        }
    }
}
