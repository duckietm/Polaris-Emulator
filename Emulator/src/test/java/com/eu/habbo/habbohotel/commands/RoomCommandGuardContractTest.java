package com.eu.habbo.habbohotel.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Room commands sent from the staff chat ran without a room and threw; parameters split on single spaces only. */
class RoomCommandGuardContractTest {

    @Test
    void everyRoomCommandIsGuarded() {
        assertEquals(14, CommandHandler.ROOM_COMMANDS.size());
        assertTrue(CommandHandler.ROOM_COMMANDS.contains(FreezeCommand.class));
        assertTrue(CommandHandler.ROOM_COMMANDS.contains(WordQuizCommand.class));
    }

    @Test
    void theGuardRunsBeforeTheCommandAndParametersSplitOnAnyWhitespace() throws Exception {
        String handler =
                Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/commands/CommandHandler.java"));

        int guard = handler.indexOf("if (ROOM_COMMANDS.contains(command.getClass())");
        assertTrue(guard > 0 && guard < handler.indexOf("command.handle(gameClient, parts)"));
        assertTrue(handler.contains("String[] parts = commandLine.trim().split(\"\\\\s+\");"));
        assertTrue(handler.contains("String[] args = commandLine.trim().split(\"\\\\s+\");"));
    }
}
