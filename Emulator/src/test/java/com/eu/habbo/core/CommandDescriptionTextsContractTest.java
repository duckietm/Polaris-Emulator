package com.eu.habbo.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class CommandDescriptionTextsContractTest {
    private static final Path FULL_DATABASE = Path.of("../Default Database/FullDatabase.sql");
    private static final Path LIVE_SCHEMA_UPDATE = Path.of("../Database Updates/003_live_required_schema.sql");

    private static final List<String> REQUIRED_DESCRIPTION_KEYS = List.of(
            "commands.description.acc_modtool_room_info",
            "commands.description.cmd_add_youtube_playlist",
            "commands.description.cmd_disablemassmentions",
            "commands.description.cmd_disablementions",
            "commands.description.cmd_give_prefix",
            "commands.description.cmd_hidewired",
            "commands.description.cmd_list_prefixes",
            "commands.description.cmd_remove_prefix",
            "commands.description.cmd_setroom_template",
            "commands.description.cmd_update_youtube_playlists"
    );

    @Test
    void fullDatabaseDefinesCommandDescriptionsUsedByCommandsList() throws IOException {
        assertContainsAllDescriptionKeys(Files.readString(FULL_DATABASE), "FullDatabase.sql");
    }

    @Test
    void liveSchemaUpdateBackfillsCommandDescriptionsForExistingDatabases() throws IOException {
        assertContainsAllDescriptionKeys(Files.readString(LIVE_SCHEMA_UPDATE), "003_live_required_schema.sql");
    }

    private static void assertContainsAllDescriptionKeys(String source, String fileName) {
        for (String key : REQUIRED_DESCRIPTION_KEYS) {
            assertTrue(source.contains("'" + key + "'"),
                    fileName + " must define " + key + " to avoid TextsManager missing-key logs");
        }
    }
}
