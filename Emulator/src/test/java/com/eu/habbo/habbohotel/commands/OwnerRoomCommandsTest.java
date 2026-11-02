package com.eu.habbo.habbohotel.commands;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * A command permission of value 2 passes for anyone with rights in the room, as it always has. :setmax and
 * :roommute change the room itself, so a friend given rights could make it unreachable or silence everyone.
 */
class OwnerRoomCommandsTest {

    @Test
    void theRoomLimitAndTheRoomMuteAreTheOwnersCall() throws Exception {
        for (String command : new String[] {"SetMaxCommand", "RoomMuteCommand"}) {
            String source = Files.readString(
                            Path.of("src/main/java/com/eu/habbo/habbohotel/commands/" + command + ".java"))
                    .replaceAll("\\s+", " ");

            assertTrue(source.contains(".isOwner(gameClient.getHabbo())"), command);
            assertTrue(source.contains("whisperLocalized(\"generic.cannot_do_that\""), command);
        }
    }
}
