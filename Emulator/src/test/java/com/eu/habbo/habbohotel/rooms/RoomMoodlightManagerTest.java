package com.eu.habbo.habbohotel.rooms;

import gnu.trove.map.TIntObjectMap;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks in the behaviour-preserving moodlight extraction (Phase 4, step 5):
 * RoomMoodlightManager parses the {@code moodlight_data} DB column into presets
 * and serializes them back in the exact legacy format. Pure logic — no DB.
 */
class RoomMoodlightManagerTest {

    @Test
    void emptyColumnYieldsThreeDefaultPresets() {
        RoomMoodlightManager manager = new RoomMoodlightManager("");
        TIntObjectMap<RoomMoodlightData> data = manager.getMoodlightData();

        assertEquals(3, data.size());
        for (RoomMoodlightData preset : data.valueCollection()) {
            assertTrue(preset.isEnabled());
            assertTrue(preset.isBackgroundOnly());
            assertEquals("#000000", preset.getColor());
            assertEquals(255, preset.getIntensity());
        }
    }

    @Test
    void nullColumnKeepsDefaultsWithoutThrowing() {
        RoomMoodlightManager manager = new RoomMoodlightManager(null);

        assertEquals(3, manager.getMoodlightData().size());
        for (RoomMoodlightData preset : manager.getMoodlightData().valueCollection()) {
            assertEquals("#000000", preset.getColor());
            assertEquals(255, preset.getIntensity());
        }
    }

    @Test
    void parsedSegmentOverridesMatchingDefault() {
        // flags "1" -> false, color #FF0000, intensity 128, id 1
        RoomMoodlightManager manager = new RoomMoodlightManager("1,1,1,#FF0000,128");

        RoomMoodlightData first = manager.getMoodlightData().get(1);
        assertNotNull(first);
        assertFalse(first.isEnabled());
        assertFalse(first.isBackgroundOnly());
        assertEquals("#FF0000", first.getColor());
        assertEquals(128, first.getIntensity());

        // ids 2 and 3 keep their defaults -> still three presets
        assertEquals(3, manager.getMoodlightData().size());
    }

    @Test
    void serializeEmitsOneSegmentPerPresetWithIdsOneToN() {
        RoomMoodlightManager manager = new RoomMoodlightManager("");
        String[] segments = manager.serialize().split(";");

        assertEquals(3, segments.length);

        Set<Integer> ids = new HashSet<>();
        for (String segment : segments) {
            String[] fields = segment.split(",");
            assertEquals(5, fields.length, "each moodlight segment has 5 comma-separated fields");
            ids.add(Integer.parseInt(fields[1]));
        }
        assertEquals(Set.of(1, 2, 3), ids, "serialize renumbers preset ids 1..N");
    }

    @Test
    void overriddenPresetSurvivesSerializeRoundTrip() {
        RoomMoodlightManager original = new RoomMoodlightManager("1,1,1,#FF0000,128");
        RoomMoodlightManager reparsed = new RoomMoodlightManager(original.serialize());

        assertEquals(3, reparsed.getMoodlightData().size());

        boolean hasRed = false;
        for (RoomMoodlightData preset : reparsed.getMoodlightData().valueCollection()) {
            if ("#FF0000".equals(preset.getColor()) && preset.getIntensity() == 128
                    && !preset.isEnabled() && !preset.isBackgroundOnly()) {
                hasRed = true;
            }
        }
        assertTrue(hasRed, "the overridden red preset should survive a serialize -> parse round-trip");
    }
}
