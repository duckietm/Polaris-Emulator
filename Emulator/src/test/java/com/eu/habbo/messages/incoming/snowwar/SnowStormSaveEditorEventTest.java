package com.eu.habbo.messages.incoming.snowwar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SnowStormSaveEditorEventTest {

    @Test
    void preservesGameplayRecordsThatTheVisualEditorDoesNotSend() {
        String existing = String.join(
                "\r\n", "snst_tree1 4 5 0 3 2300 0", "snowball_machine 26 24", "spawn 22 9 1 1", "spawn 25 12 1 1");
        StringBuilder saved = new StringBuilder("snst_fence 10 11 2 3 0 0\r\n");

        SnowStormSaveEditorEvent.appendPreservedGameplayLines(saved, existing, true, true);

        assertEquals(
                String.join(
                        "\r\n",
                        "snst_fence 10 11 2 3 0 0",
                        "snowball_machine 26 24",
                        "spawn 22 9 1 1",
                        "spawn 25 12 1 1",
                        ""),
                saved.toString());
    }

    @Test
    void canPreserveSpawnsWithoutDuplicatingAnIncomingMachine() {
        StringBuilder saved = new StringBuilder("s_snowball_machine 12 13\r\n");

        SnowStormSaveEditorEvent.appendPreservedGameplayLines(
                saved, "snowball_machine 26 24\r\nspawn 22 9 1 1", false, true);

        assertEquals("s_snowball_machine 12 13\r\nspawn 22 9 1 1\r\n", saved.toString());
    }

    @Test
    void normalizesPublicArenaNames() {
        assertEquals("Custom Arena 1000", SnowStormSaveEditorEvent.normalizeArenaName("  \n", 1000));
        assertEquals("Duckie Arena", SnowStormSaveEditorEvent.normalizeArenaName(" Duckie\r\nArena ", 1000));
        assertEquals("x".repeat(64), SnowStormSaveEditorEvent.normalizeArenaName("x".repeat(80), 1000));
    }

    @Test
    void acceptsRectangularNitroFloorHeightsWithRoomForBothPlayers() {
        assertEquals(List.of("0x", "qa"), SnowStormSaveEditorEvent.normalizeHeightmapRows(List.of("0X", "QA")));
        assertNull(SnowStormSaveEditorEvent.normalizeHeightmapRows(List.of("00", "x")));
        assertNull(SnowStormSaveEditorEvent.normalizeHeightmapRows(List.of("00", "xz")));
        assertNull(SnowStormSaveEditorEvent.normalizeHeightmapRows(List.of("xx", "x0")));
    }

    @Test
    void validatesEditorCoordinatesAgainstThePublishedFloorPlan() {
        List<String> rows = List.of("ax", "xq");

        assertTrue(SnowStormSaveEditorEvent.isEditorPositionValid(rows, 0, 0, true));
        assertTrue(SnowStormSaveEditorEvent.isEditorPositionValid(rows, 1, 0, false));
        assertFalse(SnowStormSaveEditorEvent.isEditorPositionValid(rows, 1, 0, true));
        assertTrue(SnowStormSaveEditorEvent.isEditorPositionValid(rows, 1, 1, true));
        assertFalse(SnowStormSaveEditorEvent.isEditorPositionValid(rows, 2, 0, false));
        assertFalse(SnowStormSaveEditorEvent.isEditorPositionValid(rows, 0, -1, false));
    }
}
