package polaris.hotel.items.interactions.wired.conditions;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.users.UserStats;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredEvent;
import polaris.hotel.wired.core.WiredServices;
import polaris.hotel.wired.core.WiredState;
import polaris.messages.outgoing.users.UserProfileLevel;

/** User Level reads the account level, the same value as @level, without needing a variable. */
class WiredConditionUserLevelAccountTest {

    @Test
    void theAccountLevelIsCompared() {
        Room room = mock(Room.class);
        RoomUnit unit = mock(RoomUnit.class);
        User user = mock(User.class);
        UserStats stats = mock(UserStats.class);
        when(room.getUser(unit)).thenReturn(user);
        when(user.getUserStats()).thenReturn(stats);
        when(stats.getAchievementScore()).thenReturn(5000);
        int level = UserProfileLevel.accountLevel(5000);
        WiredContext ctx = new WiredContext(
                WiredEvent.builder(WiredEvent.Type.CUSTOM, room).actor(unit).build(),
                null,
                mock(WiredServices.class),
                new WiredState(20));

        WiredConditionUserLevel equal = new WiredConditionUserLevel(1, 1, mock(Item.class), "", 0, 0);
        equal.saveData(
                new WiredSettings(new int[] {level, WiredConditionUserLevel.OFFICIAL_EQUAL, 0}, "", new int[0], 0));
        assertTrue(equal.evaluate(ctx));

        WiredConditionUserLevel higher = new WiredConditionUserLevel(2, 1, mock(Item.class), "", 0, 0);
        higher.saveData(new WiredSettings(
                new int[] {level, WiredConditionUserLevel.OFFICIAL_GREATER_THAN, 0}, "", new int[0], 0));
        assertFalse(higher.evaluate(ctx));
    }
}
