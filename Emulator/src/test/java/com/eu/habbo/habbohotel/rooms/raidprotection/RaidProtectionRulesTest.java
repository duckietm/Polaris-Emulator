package com.eu.habbo.habbohotel.rooms.raidprotection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RaidProtectionRulesTest {
    private static final long DAY = RaidSuspicion.DAY_SECONDS;

    @Test
    void acceptsOnlyAirsValues() {
        assertTrue(RaidProtectionSettings.defaults(1).isValid());
        assertTrue(RaidProtectionSettings.isValid(2, 1, 604800, 10800, 0));
        assertFalse(RaidProtectionSettings.isValid(3, 0, 3600, 900, 1));
        assertFalse(RaidProtectionSettings.isValid(1, 2, 3600, 900, 1));
        assertFalse(RaidProtectionSettings.isValid(1, 0, 3601, 900, 1));
        assertFalse(RaidProtectionSettings.isValid(1, 0, 3600, 21600, 1));
        assertFalse(RaidProtectionSettings.isValid(1, 0, 3600, 900, -1));
    }

    @Test
    void defaultsKeepProtectionOff() {
        RaidProtectionSettings settings = RaidProtectionSettings.defaults(9);
        assertEquals(9, settings.roomId());
        assertFalse(settings.enabled());
        assertFalse(settings.guardEnabled());
        assertEquals(RaidProtectionSettings.ACTION_KICK, settings.actionType());
    }

    @Test
    void suspicionFollowsTrustAccountAgeAndRoomHistory() {
        assertEquals(RaidSuspicion.TRUSTED, RaidSuspicion.level(true, true, 0, 7));
        assertEquals(RaidSuspicion.NEW_ACCOUNT, RaidSuspicion.level(false, false, DAY - 1, 7));
        assertEquals(RaidSuspicion.UNKNOWN, RaidSuspicion.level(false, false, 3 * DAY, 7));
        assertEquals(RaidSuspicion.UNKNOWN, RaidSuspicion.level(false, true, 400 * DAY, 7));
        assertEquals(RaidSuspicion.KNOWN, RaidSuspicion.level(false, false, 400 * DAY, 7));

        assertTrue(RaidSuspicion.counts(RaidSuspicion.UNKNOWN));
        assertFalse(RaidSuspicion.counts(RaidSuspicion.KNOWN));
        assertFalse(RaidSuspicion.counts(RaidSuspicion.TRUSTED));
    }

    @Test
    void theGuardGetsStricterWithItsSensitivity() {
        int low = RaidProtectionSettings.SENSITIVITY_LOW;
        int medium = RaidProtectionSettings.SENSITIVITY_MEDIUM;
        int high = RaidProtectionSettings.SENSITIVITY_HIGH;

        assertTrue(RaidSuspicion.guardBlocks(RaidSuspicion.NEW_ACCOUNT, low));
        assertFalse(RaidSuspicion.guardBlocks(RaidSuspicion.UNKNOWN, low));
        assertTrue(RaidSuspicion.guardBlocks(RaidSuspicion.UNKNOWN, medium));
        assertFalse(RaidSuspicion.guardBlocks(RaidSuspicion.KNOWN, medium));
        assertTrue(RaidSuspicion.guardBlocks(RaidSuspicion.KNOWN, high));
        assertFalse(RaidSuspicion.guardBlocks(RaidSuspicion.TRUSTED, high));
    }

    @Test
    void saveResultsFollowAirsOrder() {
        assertEquals(
                RaidProtectionManager.RESULT_SAVED,
                RaidProtectionManager.saveResult(true, true, true, true, false, false));
        assertEquals(
                RaidProtectionManager.RESULT_SAVED,
                RaidProtectionManager.saveResult(true, true, true, true, true, true));
        assertEquals(
                RaidProtectionManager.RESULT_FEATURE_DISABLED,
                RaidProtectionManager.saveResult(false, false, false, false, true, false));
        assertEquals(
                RaidProtectionManager.RESULT_ROOM_UNAVAILABLE,
                RaidProtectionManager.saveResult(true, false, false, false, true, false));
        assertEquals(
                RaidProtectionManager.RESULT_NO_RIGHTS,
                RaidProtectionManager.saveResult(true, true, false, false, true, false));
        assertEquals(
                RaidProtectionManager.RESULT_INVALID,
                RaidProtectionManager.saveResult(true, true, true, false, true, false));
        assertEquals(
                RaidProtectionManager.RESULT_CONFIRMATION_REQUIRED,
                RaidProtectionManager.saveResult(true, true, true, true, true, false));
    }
}
