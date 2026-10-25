package com.eu.habbo.messages.incoming.modtool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.modtool.ModToolManager;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * A sanctioned mute added its length in seconds to the current time in milliseconds, then
 * divided by a thousand: a one-hour mute ended after about three and a half seconds.
 */
class SanctionMuteLengthTest {

    private static final Path SOURCE =
            Path.of("src/main/java/com/eu/habbo/messages/incoming/modtool/ModToolSanctionMuteEvent.java");

    @Test
    void theMuteEndIsHoursFromNowInSeconds() throws Exception {
        String source = Files.readString(SOURCE);

        assertTrue(
                source.contains("ModToolManager.hoursToSeconds(modToolSanctionLevelItem.sanctionHourLength)"),
                "the length is converted to seconds once:\n" + source);
        assertFalse(source.contains("System.currentTimeMillis()"), "and never mixed with milliseconds");
    }

    @Test
    void anHourIsThirtySixHundredSeconds() {
        int now = 1_800_000_000;

        assertEquals(3600, ModToolManager.banExpiry(now, ModToolManager.hoursToSeconds(1)) - now);
        assertEquals(
                Integer.MAX_VALUE, ModToolManager.banExpiry(now, ModToolManager.hoursToSeconds(Integer.MAX_VALUE)));
    }

    @Test
    void aMissingSanctionLevelDoesNotThrow() throws Exception {
        String source = Files.readString(SOURCE);

        assertTrue(
                source.contains("modToolSanctionLevelItem == null"), "an unknown level is handled, not dereferenced");
    }
}
