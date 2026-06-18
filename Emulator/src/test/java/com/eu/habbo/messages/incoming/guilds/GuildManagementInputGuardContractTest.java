package com.eu.habbo.messages.incoming.guilds;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GuildManagementInputGuardContractTest {
    private static String source(String file) throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/messages/incoming/guilds/" + file));
    }

    @Test
    void guildCreateAndRenameShareNameAndDescriptionBounds() throws Exception {
        String limits = source("GuildInputLimits.java");
        String buy = source("RequestGuildBuyEvent.java");
        String rename = source("GuildChangeNameDescEvent.java");

        assertTrue(limits.contains("MAX_GUILD_NAME_LENGTH = 29"),
                "Guild names should keep the existing 29 character protocol bound");
        assertTrue(limits.contains("MAX_GUILD_DESCRIPTION_LENGTH = 254"),
                "Guild descriptions should keep the existing database/protocol bound");
        assertTrue(limits.contains("!name.isBlank()"),
                "Guild names must not be empty or whitespace-only");
        assertTrue(buy.contains("GuildInputLimits.isValidGuildName(name)"),
                "Guild purchase should use the shared name guard");
        assertTrue(buy.contains("GuildInputLimits.isValidGuildDescription(description)"),
                "Guild purchase should use the shared description guard");
        assertTrue(rename.contains("GuildInputLimits.isValidGuildName(newName)"),
                "Guild rename should reject invalid client names before plugin events");
        assertTrue(rename.contains("GuildInputLimits.isValidGuildName(nameEvent.name)"),
                "Guild rename should reject invalid plugin-mutated names before persistence");
    }

    @Test
    void guildColorInputsAreCheckedAgainstLoadedPalette() throws Exception {
        String buy = source("RequestGuildBuyEvent.java");
        String colors = source("GuildChangeColorsEvent.java");

        assertTrue(buy.contains("symbolColor(colorOne)") && buy.contains("backgroundColor(colorTwo)"),
                "Guild purchase should reject color ids that are not in the loaded guild palette");
        assertTrue(colors.contains("symbolColor(colorOne)") && colors.contains("backgroundColor(colorTwo)"),
                "Guild color changes should reject invalid client color ids");
        assertTrue(colors.contains("symbolColor(colorsEvent.colorOne)") && colors.contains("backgroundColor(colorsEvent.colorTwo)"),
                "Guild color changes should reject invalid plugin-mutated color ids");
    }

    @Test
    void guildStateInputsStayInsideKnownEnumRange() throws Exception {
        String settings = source("GuildChangeSettingsEvent.java");

        assertTrue(settings.contains("state < 0 || state >= GuildState.values().length"),
                "Guild settings should reject invalid client state ids before event dispatch");
        assertTrue(settings.contains("settingsEvent.state < 0 || settingsEvent.state >= GuildState.values().length"),
                "Guild settings should reject invalid plugin-mutated state ids before applying them");
    }
}
