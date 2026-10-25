package com.eu.habbo.messages.incoming.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The background toner packet wrote a colour into any furni by id: anyone holding rights overwrote the
 * state of gates, trophies and stack helpers placed by others. A negative value also went straight into
 * the extradata, since % keeps the sign.
 */
class RoomBackgroundTonerOnlyTest {

    private static final Path SOURCE =
            Path.of("src/main/java/com/eu/habbo/messages/incoming/rooms/RoomBackgroundEvent.java");

    @Test
    void onlyATonerTakesAColour() throws Exception {
        String source = Files.readString(SOURCE);

        int guard = source.indexOf("if (!(item instanceof InteractionBackgroundToner)) return;");
        int write = source.indexOf("item.setExtradata(");

        assertTrue(guard > 0 && guard < write, "the item must be a toner before its extradata is written:\n" + source);
    }

    @Test
    void coloursStayInRange() throws Exception {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("hue = Math.floorMod(event.hue, 256);"));
        assertFalse(source.contains("event.hue % 256"));
        assertEquals(255, Math.floorMod(-1, 256));
    }
}
