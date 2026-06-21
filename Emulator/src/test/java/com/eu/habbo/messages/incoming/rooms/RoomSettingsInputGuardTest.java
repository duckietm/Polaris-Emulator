package com.eu.habbo.messages.incoming.rooms;

import com.eu.habbo.habbohotel.rooms.RoomState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RoomSettingsInputGuardTest {
    @Test
    void validatesRoomStateBeforeIndexing() {
        assertFalse(RoomSettingsInputGuard.isValidRoomState(-1));
        assertFalse(RoomSettingsInputGuard.isValidRoomState(RoomState.values().length));
        assertTrue(RoomSettingsInputGuard.isValidRoomState(RoomState.PASSWORD.getState()));
        assertEquals(RoomState.PASSWORD, RoomSettingsInputGuard.roomState(RoomState.PASSWORD.getState()));
    }

    @Test
    void validatesRoomCapacityAndCosmeticSizes() {
        assertFalse(RoomSettingsInputGuard.isValidUsersMax(-50));
        assertFalse(RoomSettingsInputGuard.isValidUsersMax(500));
        assertTrue(RoomSettingsInputGuard.isValidUsersMax(200));
        assertFalse(RoomSettingsInputGuard.isValidWallOrFloorSize(-50));
        assertFalse(RoomSettingsInputGuard.isValidWallOrFloorSize(50));
        assertTrue(RoomSettingsInputGuard.isValidWallOrFloorSize(-2));
        assertTrue(RoomSettingsInputGuard.isValidWallOrFloorSize(1));
    }

    @Test
    void validatesRoomOptionEnums() {
        assertFalse(RoomSettingsInputGuard.isValidTradeMode(-1));
        assertFalse(RoomSettingsInputGuard.isValidTradeMode(99));
        assertTrue(RoomSettingsInputGuard.isValidTradeMode(2));
        assertFalse(RoomSettingsInputGuard.isValidModerationOption(-1));
        assertFalse(RoomSettingsInputGuard.isValidModerationOption(99));
        assertTrue(RoomSettingsInputGuard.isValidModerationOption(2));
        assertFalse(RoomSettingsInputGuard.isValidChatMode(-1));
        assertFalse(RoomSettingsInputGuard.isValidChatMode(99));
        assertTrue(RoomSettingsInputGuard.isValidChatMode(2));
        assertFalse(RoomSettingsInputGuard.isValidChatWeight(-1));
        assertFalse(RoomSettingsInputGuard.isValidChatWeight(99));
        assertTrue(RoomSettingsInputGuard.isValidChatWeight(2));
        assertFalse(RoomSettingsInputGuard.isValidChatSpeed(-1));
        assertFalse(RoomSettingsInputGuard.isValidChatSpeed(99));
        assertTrue(RoomSettingsInputGuard.isValidChatSpeed(2));
        assertFalse(RoomSettingsInputGuard.isValidChatProtection(-1));
        assertFalse(RoomSettingsInputGuard.isValidChatProtection(99));
        assertTrue(RoomSettingsInputGuard.isValidChatProtection(2));
    }

    @Test
    void validatesChatDistanceSafely() {
        assertFalse(RoomSettingsInputGuard.isValidChatDistance(0));
        assertFalse(RoomSettingsInputGuard.isValidChatDistance(Integer.MIN_VALUE));
        assertFalse(RoomSettingsInputGuard.isValidChatDistance(500));
        assertTrue(RoomSettingsInputGuard.isValidChatDistance(1));
        assertTrue(RoomSettingsInputGuard.isValidChatDistance(99));
    }

    @Test
    void validatesTagCount() {
        assertFalse(RoomSettingsInputGuard.isValidTagCount(-1));
        assertFalse(RoomSettingsInputGuard.isValidTagCount(3));
        assertTrue(RoomSettingsInputGuard.isValidTagCount(0));
        assertTrue(RoomSettingsInputGuard.isValidTagCount(2));
    }

    @Test
    void rejectsOversizedPasswords() {
        assertTrue(RoomSettingsInputGuard.isSafePassword("short-secret"));
        assertFalse(RoomSettingsInputGuard.isSafePassword("x".repeat(RoomSettingsInputGuard.MAX_PASSWORD_LENGTH + 1)));
    }
}
