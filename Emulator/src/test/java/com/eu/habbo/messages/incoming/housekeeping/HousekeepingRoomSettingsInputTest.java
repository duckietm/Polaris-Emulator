package com.eu.habbo.messages.incoming.housekeeping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class HousekeepingRoomSettingsInputTest {
    @Test
    void nameMustBePresentAndFitWhatTheRoomStores() {
        assertTrue(HousekeepingRoomSettingsInput.isValidName("Lobby"));
        assertTrue(HousekeepingRoomSettingsInput.isValidName("x".repeat(50)));
        assertFalse(HousekeepingRoomSettingsInput.isValidName("x".repeat(51)));
        assertFalse(HousekeepingRoomSettingsInput.isValidName("   "));
        assertFalse(HousekeepingRoomSettingsInput.isValidName(null));
    }

    @Test
    void descriptionMayBeEmptyButNotLongerThanTheStoredLimit() {
        assertTrue(HousekeepingRoomSettingsInput.isValidDescription(""));
        assertTrue(HousekeepingRoomSettingsInput.isValidDescription("x".repeat(250)));
        assertFalse(HousekeepingRoomSettingsInput.isValidDescription("x".repeat(251)));
        assertFalse(HousekeepingRoomSettingsInput.isValidDescription(null));
    }

    @Test
    void numericSettingsStayInsideTheOwnerDialogRanges() {
        assertTrue(HousekeepingRoomSettingsInput.isValidUsersMax(1));
        assertTrue(HousekeepingRoomSettingsInput.isValidUsersMax(200));
        assertFalse(HousekeepingRoomSettingsInput.isValidUsersMax(0));
        assertFalse(HousekeepingRoomSettingsInput.isValidUsersMax(201));
        assertTrue(HousekeepingRoomSettingsInput.isValidTradeMode(0));
        assertTrue(HousekeepingRoomSettingsInput.isValidTradeMode(2));
        assertFalse(HousekeepingRoomSettingsInput.isValidTradeMode(3));
        assertFalse(HousekeepingRoomSettingsInput.isValidTradeMode(-1));
        assertTrue(HousekeepingRoomSettingsInput.isValidTagCount(2));
        assertFalse(HousekeepingRoomSettingsInput.isValidTagCount(3));
        assertFalse(HousekeepingRoomSettingsInput.isValidTagCount(-1));
    }

    @Test
    void tagsAreJoinedTheWayTheRoomsTableStoresThem() {
        assertEquals("", HousekeepingRoomSettingsInput.joinTags(List.of()));
        assertEquals("games;chill;", HousekeepingRoomSettingsInput.joinTags(List.of(" games ", "chill")));
        assertEquals("games;", HousekeepingRoomSettingsInput.joinTags(List.of("games", "games", "")));
        assertEquals("games;", HousekeepingRoomSettingsInput.joinTags(Arrays.asList("games", null)));
    }

    @Test
    void tagsThatWouldBreakTheSeparatorOrAreTooLongAreRefused() {
        assertNull(HousekeepingRoomSettingsInput.joinTags(List.of("a;b")));
        assertNull(HousekeepingRoomSettingsInput.joinTags(List.of("x".repeat(16))));
    }
}
