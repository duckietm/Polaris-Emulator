package com.eu.habbo.habbohotel.commands;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** :roomgift was listed as a room command but never registered, and :test refused every user. */
class CommandRegistrationTest {
    private static final Path COMMANDS = Path.of("src/main/java/com/eu/habbo/habbohotel/commands");

    @Test
    void everyRoomCommandIsRegistered() throws Exception {
        String handler = Files.readString(COMMANDS.resolve("CommandHandler.java"));
        String registrations = handler.substring(handler.indexOf("public void reloadCommands() {"));

        for (Class<? extends Command> command : CommandHandler.ROOM_COMMANDS) {
            assertTrue(
                    registrations.contains("addCommand(new " + command.getSimpleName() + "());"),
                    command.getSimpleName() + " is a room command but is not registered");
        }
    }

    @Test
    void theTestCommandRefusesOnlyAMissingUser() throws Exception {
        String test = Files.readString(COMMANDS.resolve("TestCommand.java"));

        assertTrue(test.contains("gameClient.getHabbo() == null"));
        assertTrue(!test.contains("gameClient.getHabbo() != null"));
        assertTrue(test.contains("for (int i = 2; i < params.length; i++) {"));
    }
}
