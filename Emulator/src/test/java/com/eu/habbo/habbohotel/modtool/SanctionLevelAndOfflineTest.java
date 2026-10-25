package com.eu.habbo.habbohotel.modtool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * A sanction threw in two places: a user already at the last configured level reached a level
 * that does not exist, and an offline user was alerted or muted through a null Habbo after the
 * sanction row had been written.
 */
class SanctionLevelAndOfflineTest {

    private static final Path SOURCE = Path.of("src/main/java/com/eu/habbo/habbohotel/modtool/ModToolSanctions.java");

    private static final Map<Integer, ModToolSanctionLevelItem> LEVELS = Map.of(
            1, new ModToolSanctionLevelItem(1, "ALERT", 0, 30),
            2, new ModToolSanctionLevelItem(2, "MUTE", 1, 30),
            3, new ModToolSanctionLevelItem(3, "BAN", 18, 30));

    @Test
    void theNextLevelFollowsTheLastOne() {
        assertEquals(1, ModToolSanctions.nextLevel(LEVELS, 0).sanctionLevel);
        assertEquals(3, ModToolSanctions.nextLevel(LEVELS, 2).sanctionLevel);
    }

    @Test
    void theTopLevelIsWhereItStops() {
        assertEquals(3, ModToolSanctions.nextLevel(LEVELS, 3).sanctionLevel);
        assertEquals(3, ModToolSanctions.nextLevel(LEVELS, 40).sanctionLevel);
    }

    @Test
    void noConfiguredLevelMeansNoSanction() {
        assertNull(ModToolSanctions.nextLevel(Map.of(), 0));
        assertNull(ModToolSanctions.nextLevel(Map.of(5, new ModToolSanctionLevelItem(5, "BAN", 1, 1)), 0));
    }

    @Test
    void anOfflineUserIsNeverDereferenced() throws Exception {
        String source = Files.readString(SOURCE).replace("\r\n", "\n");

        assertFalse(source.contains("sanctionLevel++"), "the level comes from nextLevel:\n" + source);
        assertTrue(
                source.contains("if (habbo != null) {\n                    habbo.alert(reason);"),
                "an alert needs someone online");
        assertTrue(source.contains("muteOffline(habboId, seconds);"), "an offline mute is written to the settings row");
        assertTrue(
                source.contains("GREATEST(mute_end_timestamp, ?) + ?"),
                "and extends a running mute, as addMuteTime does");
    }
}
