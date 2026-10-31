package com.eu.habbo.messages.incoming.floorplaneditor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The floor plan area counted the row separators, so a plan under the limit could be refused. */
class FloorPlanAreaLimitTest {

    @Test
    void theAreaIsRowsTimesWidth() {
        assertFalse(FloorPlanEditorSaveEvent.exceedsArea(64, 64, 64 * 64));
        assertTrue(FloorPlanEditorSaveEvent.exceedsArea(64, 65, 64 * 64));
    }

    @Test
    void theRawTextMayHoldOneSeparatorPerRow() {
        // 64 rows of 64 tiles plus 63 separators: under the raw cap of area + width, so the tiles decide.
        int raw = 64 * 64 + 63;
        assertTrue(raw
                <= FloorPlanEditorSaveEvent.MAXIMUM_FLOORPLAN_SIZE
                        + FloorPlanEditorSaveEvent.MAXIMUM_FLOORPLAN_WIDTH_LENGTH);
    }
}
