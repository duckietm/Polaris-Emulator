package com.eu.habbo.habbohotel.commands;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A command that returns false is said in the room as plain chat. Failures, missing permissions and bad
 * input must be answered privately instead.
 */
class CommandFeedbackContractTest {
    private static final Path COMMANDS = Path.of("src/main/java/com/eu/habbo/habbohotel/commands");
    private static final Path MIGRATION =
            Path.of("src/main/resources/db/migration/V20261003100000__command_feedback_texts.sql");

    private static String source(String file) throws Exception {
        return Files.readString(COMMANDS.resolve(file));
    }

    private static String main(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo").resolve(path));
    }

    @Test
    void handleCommandKeepsItsOldContractForRconWiredAndPlugins() throws Exception {
        String handler = source("CommandHandler.java");
        int execute = handler.indexOf("private static CommandOutcome execute(");

        assertTrue(handler.contains("return execute(gameClient, commandLine) == CommandOutcome.SUCCESS;"));
        assertTrue(execute > 0);
        // The shared runner gives no feedback itself; only the chat entry point whispers.
        String runner = handler.substring(execute, handler.indexOf("public static Command getCommand(", execute));
        assertFalse(runner.contains("whisper"));
        assertTrue(runner.contains("LOGGER.error(\"Caught exception\", e);"));
        assertTrue(runner.contains("return CommandOutcome.FAILED;"));
        assertTrue(runner.contains("return CommandOutcome.NO_PERMISSION;"));
        // A forbidden command is only reported for a registered key; unknown ":words" stay chat.
        assertTrue(runner.lastIndexOf("if (s.equalsIgnoreCase(parts[0]))", runner.indexOf("NO_PERMISSION;")) > 0);

        assertTrue(main("messages/rcon/ExecuteCommand.java").contains("CommandHandler.handleCommand("));
        assertTrue(main("habbohotel/items/interactions/wired/effects/WiredEffectSayCommand.java")
                .contains("CommandHandler.handleCommand("));
        assertTrue(main("messages/incoming/rooms/items/SavePostItStickyPoleEvent.java")
                .contains("CommandHandler.handleCommand("));
    }

    @Test
    void typedChatUsesTheFeedbackEntryPoint() throws Exception {
        String handler = source("CommandHandler.java");
        int chat = handler.indexOf("public static boolean handleChatCommand(");
        String chatEntry = handler.substring(chat, handler.indexOf("private static CommandOutcome execute(", chat));

        assertTrue(chatEntry.contains("GENERIC_ERROR_KEY"));
        assertTrue(chatEntry.contains("NO_PERMISSION_KEY"));
        assertTrue(chatEntry.contains("return outcome.consumesChat();"));
        assertTrue(handler.contains("\"commands.error.generic\""));
        assertTrue(handler.contains("\"commands.error.no_permission\""));

        String chatManager = main("habbohotel/rooms/RoomChatManager.java");
        assertFalse(chatManager.contains("CommandHandler.handleCommand("));
        assertTrue(chatManager.indexOf("CommandHandler.handleChatCommand(")
                != chatManager.lastIndexOf("CommandHandler.handleChatCommand("));
        assertTrue(main("habbohotel/messenger/StaffChatBuddy.java").contains("CommandHandler.handleChatCommand("));
    }

    @Test
    void badInputIsAnsweredInsteadOfSaid() throws Exception {
        for (String command : List.of(
                "CalendarCommand.java",
                "CreditsCommand.java",
                "EnableCommand.java",
                "FastwalkCommand.java",
                "GiftCommand.java",
                "MassGiftCommand.java",
                "PetInfoCommand.java",
                "RoomAlertCommand.java",
                "RoomGiftCommand.java",
                "SetMaxCommand.java",
                "TakeBadgeCommand.java",
                "UnloadRoomCommand.java")) {
            assertFalse(
                    source(command).contains("return false"), command + " must not say the command line on bad input");
        }
    }

    @Test
    void theEasterEggCommandsStillSayTheLine() throws Exception {
        assertTrue(source("MoonwalkCommand.java").contains("return false"));
        assertTrue(source("HabnamCommand.java").contains("return false"));
    }

    @Test
    void creditsForAnUnknownUserDoesNotParseTheAmount() throws Exception {
        String credits = source("CreditsCommand.java");
        int notFound = credits.indexOf("commands.error.cmd_credits.user_not_found");

        assertTrue(notFound > 0);
        String line = credits.substring(notFound, credits.indexOf('\n', notFound));
        assertFalse(line.contains("Integer.parseInt"));
    }

    @Test
    void unloadWithoutRoomDoesNotThrow() throws Exception {
        String unload = source("UnloadRoomCommand.java");

        assertTrue(unload.indexOf("if (room == null)") < unload.indexOf("room.getOwnerId()"));
    }

    @Test
    void enableAnswersUnknownTargetsAndMissingRights() throws Exception {
        String enable = source("EnableCommand.java");

        assertTrue(enable.contains("\"generic.user.not_found\""));
        assertTrue(enable.contains("\"generic.cannot_do_that\""));
    }

    @Test
    void takeBadgeAnswersUnknownUsersAndWrongArguments() throws Exception {
        String takeBadge = source("TakeBadgeCommand.java");

        assertTrue(takeBadge.contains("\"generic.user.not_found\""));
        assertTrue(takeBadge.contains("\"commands.description.cmd_take_badge\""));
    }

    @Test
    void newTextsAreShippedByAnIdempotentMigration() throws Exception {
        String migration = Files.readString(MIGRATION);

        for (String key : List.of(
                "commands.error.generic",
                "commands.error.no_permission",
                "commands.error.cmd_shutdown.usage",
                "commands.error.cmd_shutdown.none_pending",
                "commands.succes.cmd_shutdown.cancelled",
                "commands.error.cmd_calendar.not_found")) {
            assertTrue(migration.contains("('" + key + "'"), key + " must be inserted");
        }
        assertTrue(migration.contains("ON DUPLICATE KEY UPDATE `value` = `value`"));
    }
}
