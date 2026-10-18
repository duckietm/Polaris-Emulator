package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboStats;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredServices;
import com.eu.habbo.habbohotel.wired.core.WiredState;
import com.eu.habbo.messages.outgoing.users.UserProfileLevel;
import org.junit.jupiter.api.Test;

/** User Level reads the account level, the same value as @level, without needing a variable. */
class WiredConditionUserLevelAccountTest {

    @Test
    void theAccountLevelIsCompared() {
        Room room = mock(Room.class);
        RoomUnit unit = mock(RoomUnit.class);
        Habbo habbo = mock(Habbo.class);
        HabboStats stats = mock(HabboStats.class);
        when(room.getHabbo(unit)).thenReturn(habbo);
        when(habbo.getHabboStats()).thenReturn(stats);
        when(stats.getAchievementScore()).thenReturn(5000);
        int level = UserProfileLevel.accountLevel(5000);
        WiredContext ctx = new WiredContext(
                WiredEvent.builder(WiredEvent.Type.CUSTOM, room).actor(unit).build(),
                null,
                mock(WiredServices.class),
                new WiredState(20));

        WiredConditionUserLevel equal = new WiredConditionUserLevel(1, 1, mock(Item.class), "", 0, 0);
        equal.saveData(new WiredSettings(
                new int[] {level, WiredConditionUserLevel.COMPARISON_EQUAL, 0, 0}, "", new int[0], 0));
        assertTrue(equal.evaluate(ctx));

        WiredConditionUserLevel higher = new WiredConditionUserLevel(2, 1, mock(Item.class), "", 0, 0);
        higher.saveData(new WiredSettings(
                new int[] {level + 1, WiredConditionUserLevel.COMPARISON_GREATER_THAN_OR_EQUAL, 0, 0},
                "",
                new int[0],
                0));
        assertFalse(higher.evaluate(ctx));
    }
}
