package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * A room password could be guessed in a loop: nothing counted the wrong answers. A random-state furni scheduled one
 * more result and broadcast for every click while it was still rolling.
 */
class RoomPasswordAttemptsTest {

    @Test
    void aRoomStopsCheckingPasswordsAfterFiveWrongOnes() {
        assertTrue(RoomEntryService.passwordAttemptsLeft(null));
        assertTrue(RoomEntryService.passwordAttemptsLeft(new AtomicInteger(4)));
        assertFalse(RoomEntryService.passwordAttemptsLeft(new AtomicInteger(5)));
    }

    @Test
    void aRandomStateFurniIgnoresClicksWhileItRolls() throws Exception {
        String source = Files.readString(
                        Path.of("src/main/java/com/eu/habbo/habbohotel/items/interactions/InteractionRandomState.java"))
                .replaceAll("\\s+", " ");

        assertTrue(source.contains("if (this.rolling) return;"));
        assertTrue(source.contains("this.rolling = false;"));
        assertTrue(Files.readString(Path.of(
                        "src/main/java/com/eu/habbo/messages/incoming/rooms/items/UseRandomStateItemEvent.java"))
                .contains("public int getRatelimit()"));
    }
}
