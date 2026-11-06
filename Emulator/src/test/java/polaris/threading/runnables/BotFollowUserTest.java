package polaris.threading.runnables;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import polaris.hotel.bots.Bot;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomTileState;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;

/** Bot Reached Avatar is raised on an arrival only, not for a bot that starts next to the user. */
class BotFollowUserTest {

    @Test
    void aBotThatStartsNextToTheUserHasAlreadyReachedThem() {
        assertTrue(follow(4, 4, 5, 5).hasReached());
        assertTrue(follow(4, 4, 4, 5).hasReached());
    }

    @Test
    void aBotFurtherAwayStillHasToArrive() {
        assertFalse(follow(4, 4, 6, 4).hasReached());
        assertFalse(new BotFollowUser(null, null, mock(Room.class)).hasReached());
    }

    private static BotFollowUser follow(int botX, int botY, int userX, int userY) {
        Bot bot = mock(Bot.class);
        User user = mock(User.class);
        RoomUnit botUnit = unitAt(botX, botY);
        RoomUnit userUnit = unitAt(userX, userY);
        when(bot.getRoomUnit()).thenReturn(botUnit);
        when(user.getRoomUnit()).thenReturn(userUnit);
        return new BotFollowUser(bot, user, mock(Room.class));
    }

    private static RoomUnit unitAt(int x, int y) {
        RoomUnit unit = new RoomUnit();
        unit.setLocation(new RoomTile((short) x, (short) y, (short) 0, RoomTileState.OPEN, true));
        return unit;
    }

    @Test
    void theBotWalksToARandomFreeTileAroundTheUserAndWaitsWhileTheyAreAway() {
        Room room = mock(Room.class);
        polaris.hotel.rooms.RoomLayout layout = mock(polaris.hotel.rooms.RoomLayout.class);
        when(room.getLayout()).thenReturn(layout);
        when(layout.getTile(org.mockito.ArgumentMatchers.anyShort(), org.mockito.ArgumentMatchers.anyShort()))
                .thenAnswer(invocation -> new RoomTile(
                        invocation.getArgument(0), invocation.getArgument(1), (short) 0, RoomTileState.OPEN, true));
        when(room.getRoomUnitsAt(org.mockito.ArgumentMatchers.any())).thenReturn(java.util.List.of());
        Bot bot = mock(Bot.class);
        RoomUnit botUnit = unitAt(1, 1);
        when(bot.getRoomUnit()).thenReturn(botUnit);
        when(bot.getRoom()).thenReturn(room);
        when(bot.getFollowingUserId()).thenReturn(9);
        User user = mock(User.class);
        polaris.hotel.users.UserInfo info = mock(polaris.hotel.users.UserInfo.class);
        when(info.getId()).thenReturn(9);
        when(user.getUserInfo()).thenReturn(info);
        RoomUnit userUnit = unitAt(6, 6);
        when(user.getRoomUnit()).thenReturn(userUnit);
        BotFollowUser follow = new BotFollowUser(bot, user, room);

        // Away: nothing happens.
        RoomTile before = botUnit.getGoal();
        follow.step(new java.util.Random(1));
        org.junit.jupiter.api.Assertions.assertSame(before, botUnit.getGoal());

        // Back in the room: a tile next to them.
        when(room.getUser(9)).thenReturn(user);
        java.util.Set<String> goals = new java.util.HashSet<>();
        try (org.mockito.MockedStatic<polaris.Emulator> emulator =
                org.mockito.Mockito.mockStatic(polaris.Emulator.class)) {
            emulator.when(polaris.Emulator::getPluginManager).thenReturn(mock(polaris.plugin.PluginManager.class));
            java.util.Random random = new java.util.Random(7);
            for (int i = 0; i < 40; i++) {
                RoomTile goal =
                        BotFollowUser.randomFreeTileAround(room, userUnit.getCurrentLocation(), botUnit, random);
                assertTrue(BotFollowUser.nextTo(goal, userUnit.getCurrentLocation()), goal.x + "," + goal.y);
                goals.add(goal.x + "," + goal.y);
            }
        }
        assertTrue(goals.size() > 1, "the tile is random: " + goals);
    }
}
