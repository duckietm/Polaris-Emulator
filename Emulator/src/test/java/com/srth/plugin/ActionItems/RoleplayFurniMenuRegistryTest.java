package com.srth.plugin.ActionItems;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RoleplayFurniMenuRegistryTest {
    @TempDir
    Path tempDir;

    @Test
    void loadsMenusByFurniNameWithoutCaseSensitivity() throws Exception {
        Path file = this.tempDir.resolve("menus.json");
        Files.writeString(file, """
                {
                  "menus": {
                    "rp_basura_urban": {
                      "title": "Basura urbana",
                      "buttons": [
                        { "label": "Buscar", "actionId": "actionBuscarBasuraUrbana" }
                      ]
                    }
                  }
                }
                """);

        RoleplayFurniMenuRegistry registry = RoleplayFurniMenuRegistry.load(file);
        RoleplayFurniMenuRegistry.Menu menu = registry.getMenu("RP_BASURA_URBAN");

        assertEquals("Basura urbana", menu.title());
        assertEquals(1, menu.buttons().size());
        assertEquals("Buscar", menu.buttons().getFirst().label());
        assertEquals("actionBuscarBasuraUrbana", menu.buttons().getFirst().actionId());
    }

    @Test
    void rejectsMenuButtonsWithoutAnActionId() throws Exception {
        Path file = this.tempDir.resolve("menus.json");
        Files.writeString(file, """
                {
                  "menus": {
                    "rp_basura_urban": {
                      "buttons": [{ "label": "Buscar" }]
                    }
                  }
                }
                """);

        assertThrows(IllegalArgumentException.class, () -> RoleplayFurniMenuRegistry.load(file));
    }

    @Test
    void rejectsMenusThatExceedTheRendererButtonLimit() throws Exception {
        Path file = this.tempDir.resolve("menus.json");
        String button = "{\"label\":\"Use\",\"actionId\":\"use\"}";
        String buttons = String.join(",", java.util.Collections.nCopies(101, button));
        Files.writeString(file, """
                {
                  "menus": {
                    "rp_basura_urban": {
                      "buttons": [%s]
                    }
                  }
                }
                """.formatted(buttons));

        assertThrows(IllegalArgumentException.class, () -> RoleplayFurniMenuRegistry.load(file));
    }

    @Test
    void returnsNoMenuForUnconfiguredFurni() {
        assertNull(RoleplayFurniMenuRegistry.empty().getMenu("unknown_furni"));
    }
}
