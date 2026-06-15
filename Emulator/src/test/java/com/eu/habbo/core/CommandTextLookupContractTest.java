package com.eu.habbo.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class CommandTextLookupContractTest {
    private static final Path TEXTS_MANAGER = Path.of("src/main/java/com/eu/habbo/core/TextsManager.java");
    private static final Path COMMANDS_COMMAND = Path.of("src/main/java/com/eu/habbo/habbohotel/commands/CommandsCommand.java");
    private static final Path AVAILABLE_COMMANDS_COMPOSER = Path.of(
            "src/main/java/com/eu/habbo/messages/outgoing/commands/AvailableCommandsComposer.java");

    @Test
    void textsManagerExposesQuietFallbackLookupForOptionalTexts() throws IOException {
        String source = Files.readString(TEXTS_MANAGER);

        assertTrue(source.contains("public String getValueQuietly(String key, String defaultValue)"));
        assertTrue(source.contains("return this.texts.getProperty(key, defaultValue);"));
    }

    @Test
    void commandListsUseQuietDescriptionLookups() throws IOException {
        String commandsCommand = Files.readString(COMMANDS_COMMAND);
        String availableCommandsComposer = Files.readString(AVAILABLE_COMMANDS_COMPOSER);

        assertTrue(commandsCommand.contains("getValueQuietly(textKey, \"\")"),
                ":commands should not log an error when an optional command description is missing");
        assertTrue(availableCommandsComposer.contains("getValueQuietly(\"commands.description.\" + cmd.permission, cmd.permission)"),
                "available commands composer should not log an error when an optional command description is missing");
    }
}
