package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitType;
import org.junit.jupiter.api.Test;

class WiredConditionPulsesAndNamesTest {
    @Test
    void timeElapsedCountsWholePulsesAndNoneBeforeTheReset() {
        assertEquals(0, WiredConditionLessTimeElapsed.pulsesSinceReset(-300));
        assertEquals(0, WiredConditionLessTimeElapsed.pulsesSinceReset(499));
        assertEquals(1, WiredConditionLessTimeElapsed.pulsesSinceReset(500));
        assertEquals(3, WiredConditionLessTimeElapsed.pulsesSinceReset(1999));
    }

    @Test
    void aUsersNameMatchesWithItsCaseABotsWithout() {
        RoomUnit user = mock(RoomUnit.class);
        when(user.getRoomUnitType()).thenReturn(RoomUnitType.USER);
        RoomUnit bot = mock(RoomUnit.class);
        when(bot.getRoomUnitType()).thenReturn(RoomUnitType.BOT);

        assertTrue(WiredConditionTriggererMatch.namesMatch("Alice", "Alice", user));
        assertFalse(WiredConditionTriggererMatch.namesMatch("alice", "Alice", user));
        assertTrue(WiredConditionTriggererMatch.namesMatch("bob", "Bob", bot));
        assertFalse(WiredConditionTriggererMatch.namesMatch("bob", null, bot));
    }

    @Test
    void theChestConditionsKeepQuantifierZeroOneOrTwo() {
        assertEquals(2, WiredConditionChestHasItems.normalizeChestQuantifier(2));
        assertEquals(0, WiredConditionChestHasItems.normalizeChestQuantifier(5));
        assertEquals(0, WiredConditionChestHasItems.normalizeChestQuantifier(-1));
        assertEquals(1, new WiredConditionChestHasItems(1, 1, null, "", 0, 0).officialQuantifierCode() + 1);
    }
}
