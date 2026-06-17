package com.eu.habbo.messages.rcon;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecuteCommandGuardTest {
    @Test
    void extractsCommandKeyOnlyFromColonCommands() {
        assertEquals("dance", ExecuteCommand.commandKey(":dance"));
        assertEquals("dance", ExecuteCommand.commandKey("  :DaNcE 1 2  "));
        assertEquals("", ExecuteCommand.commandKey("dance"));
        assertEquals("", ExecuteCommand.commandKey(":   "));
    }

    @Test
    void deniedPermissionsBlockDangerousDefaultsUnlessExplicitlyAllowed() {
        assertFalse(ExecuteCommand.isAllowed("cmd_shutdown", "cmd_shutdown;cmd_give_rank", ""));
        assertFalse(ExecuteCommand.isAllowed("CMD_GIVE_RANK", "cmd_shutdown,cmd_give_rank", ""));
        assertTrue(ExecuteCommand.isAllowed("cmd_dance", "cmd_shutdown;cmd_give_rank", ""));
        assertTrue(ExecuteCommand.isAllowed("cmd_shutdown", "cmd_shutdown", "cmd_shutdown"));
        assertFalse(ExecuteCommand.isAllowed("cmd_dance", "cmd_shutdown", "cmd_about"));
    }

    @Test
    void parsesInvalidCommandLengthAsDefault() {
        assertEquals(ExecuteCommand.DEFAULT_MAX_COMMAND_LENGTH, ExecuteCommand.parseMaxCommandLength(null));
        assertEquals(ExecuteCommand.DEFAULT_MAX_COMMAND_LENGTH, ExecuteCommand.parseMaxCommandLength("0"));
        assertEquals(64, ExecuteCommand.parseMaxCommandLength("64"));
    }

    @Test
    void executeCommandHasConfigurableGuardRails() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/messages/rcon/ExecuteCommand.java"));
        String emulator = Files.readString(Path.of("src/main/java/com/eu/habbo/Emulator.java"));

        assertTrue(source.contains("CommandHandler.getCommand(commandKey)"),
                "RCON executecommand must resolve aliases to the registered command permission");
        assertTrue(source.contains("rcon.execute_command.denied_permissions"),
                "RCON executecommand must support a configurable denied-permission list");
        assertTrue(source.contains("rcon.execute_command.allowed_permissions"),
                "RCON executecommand must support a stricter configurable allowlist");
        assertTrue(source.contains("!commandLine.startsWith(\":\") || commandLine.length() > maxLength"),
                "RCON executecommand must reject non-command payloads and oversized command lines");
        assertTrue(emulator.contains("rcon.execute_command.denied_permissions"),
                "RCON executecommand guard defaults must be registered before the RCON server starts");
    }
}
