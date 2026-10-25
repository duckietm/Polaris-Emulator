package com.eu.habbo.messages.incoming.rooms.items.jukebox;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Removing a disc from the jukebox needed no rights, unlike adding one: any visitor could empty the
 * playlist and stop the music. The two playlist requests threw a NullPointerException outside a room.
 */
class JukeBoxRightsTest {

    private static String source(String handler) throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/messages/incoming/rooms/items/jukebox/" + handler));
    }

    @Test
    void removingADiscNeedsRights() throws Exception {
        String source = source("JukeBoxRemoveSoundTrackEvent.java");

        int guard = source.indexOf("if (room == null || !room.hasRights(this.client.getHabbo())) return;");
        int remove = source.indexOf("removeSong(");

        assertTrue(guard > 0 && guard < remove, source);
    }

    @Test
    void thePlaylistRequestsNeedARoom() throws Exception {
        for (String handler : new String[] {"JukeBoxEventOne.java", "JukeBoxEventTwo.java"}) {
            String source = source(handler);

            assertTrue(source.contains("if (room == null) return;"), handler);
            assertFalse(
                    source.contains("getCurrentRoom().getTraxManager()"),
                    handler + " no longer dereferences the room blindly");
        }
    }
}
