package com.eu.habbo.messages.incoming.housekeeping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class HousekeepingReloadContractTest {
    private static final Path HANDLER =
            Path.of("src/main/java/com/eu/habbo/messages/incoming/housekeeping/HousekeepingReloadEvent.java");

    @Test
    void theTargetsAreTheOnesTheClientOffers() {
        assertEquals(
                List.of("catalog", "texts", "permissions", "items", "navigator", "config", "wordfilter"),
                HousekeepingReloadEvent.TARGETS);
    }

    @Test
    void everyTargetRunsItsUpdateCommandUnderThatCommandsPermission() throws Exception {
        String source = Files.readString(HANDLER);

        for (String command : List.of(
                "UpdateCatalogCommand",
                "UpdateTextsCommand",
                "UpdatePermissionsCommand",
                "UpdateItemsCommand",
                "UpdateNavigatorCommand",
                "UpdateConfigCommand",
                "UpdateWordFilterCommand")) {
            assertTrue(source.contains("new " + command + "()"), "the reload must reuse " + command);
        }

        assertTrue(source.contains("HousekeepingAccess.check(this.client)"), "the panel permission comes first");
        assertTrue(
                source.contains("hasPermission(command.permission)"),
                "the operator must also hold the command's own permission");
        assertTrue(source.contains("default -> null"), "an unknown target must be refused");
    }
}
