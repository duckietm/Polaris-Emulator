package com.eu.habbo.habbohotel.rooms;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterizes the RoomMoodlightData parse/format primitive that the moodlight
 * subsystem (and the extracted RoomMoodlightManager) relies on. Pure logic.
 *
 * Of note for stability: a well-formed 5-field record parses as expected, while
 * any record with the wrong field count safely falls back to the default preset
 * (so a truncated/garbage moodlight_data segment does not corrupt the preset).
 */
class RoomMoodlightDataTest {

    @Test
    void parsesWellFormedRecord() {
        // fields: enabledFlag, id, backgroundOnlyFlag, color, intensity
        RoomMoodlightData data = RoomMoodlightData.fromString("1,5,2,#ABCDEF,200");

        assertEquals(5, data.getId());
        assertFalse(data.isEnabled());        // "1" != "2"
        assertTrue(data.isBackgroundOnly());  // "2" == "2"
        assertEquals("#ABCDEF", data.getColor());
        assertEquals(200, data.getIntensity());
    }

    @Test
    void enabledFlagIsTrueOnlyForTwo() {
        assertTrue(RoomMoodlightData.fromString("2,1,2,#000000,255").isEnabled());
        assertFalse(RoomMoodlightData.fromString("1,1,2,#000000,255").isEnabled());
    }

    @Test
    void wrongFieldCountFallsBackToDefaultPreset() {
        for (String malformed : new String[]{"", "garbage", "1,2,3", "1,2,3,4", "1,2,3,4,5,6"}) {
            RoomMoodlightData data = RoomMoodlightData.fromString(malformed);

            assertEquals(1, data.getId(), "default id for: '" + malformed + "'");
            assertTrue(data.isEnabled(), "default enabled for: '" + malformed + "'");
            assertTrue(data.isBackgroundOnly(), "default bgOnly for: '" + malformed + "'");
            assertEquals("#000000", data.getColor(), "default color for: '" + malformed + "'");
            assertEquals(255, data.getIntensity(), "default intensity for: '" + malformed + "'");
        }
    }

    @Test
    void toStringMatchesTheColumnFormat() {
        RoomMoodlightData data = new RoomMoodlightData(3, true, false, "#112233", 64);
        assertEquals("2,3,1,#112233,64", data.toString());
    }

    @Test
    void parseOfToStringRoundTrips() {
        RoomMoodlightData original = new RoomMoodlightData(2, false, true, "#FF8800", 128);
        RoomMoodlightData parsed = RoomMoodlightData.fromString(original.toString());

        assertEquals(original.getId(), parsed.getId());
        assertEquals(original.isEnabled(), parsed.isEnabled());
        assertEquals(original.isBackgroundOnly(), parsed.isBackgroundOnly());
        assertEquals(original.getColor(), parsed.getColor());
        assertEquals(original.getIntensity(), parsed.getIntensity());
    }
}
