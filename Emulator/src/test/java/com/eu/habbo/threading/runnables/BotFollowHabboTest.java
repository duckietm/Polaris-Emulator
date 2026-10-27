package com.eu.habbo.threading.runnables;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import org.junit.jupiter.api.Test;

/** Bot Reached Avatar is raised on an arrival only, not for a bot that starts next to the user. */
class BotFollowHabboTest {

    @Test
    void aBotThatStartsNextToTheUserHasAlreadyReachedThem() {
        assertTrue(follow(4, 4, 5, 5).hasReached());
        assertTrue(follow(4, 4, 4, 5).hasReached());
    }

    @Test
    void aBotFurtherAwayStillHasToArrive() {
        assertFalse(follow(4, 4, 6, 4).hasReached());
        assertFalse(new BotFollowHabbo(null, null, mock(Room.class)).hasReached());
    }

    private static BotFollowHabbo follow(int botX, int botY, int userX, int userY) {
        Bot bot = mock(Bot.class);
        Habbo habbo = mock(Habbo.class);
        RoomUnit botUnit = unitAt(botX, botY);
        RoomUnit userUnit = unitAt(userX, userY);
        when(bot.getRoomUnit()).thenReturn(botUnit);
        when(habbo.getRoomUnit()).thenReturn(userUnit);
        return new BotFollowHabbo(bot, habbo, mock(Room.class));
    }

    private static RoomUnit unitAt(int x, int y) {
        RoomUnit unit = new RoomUnit();
        unit.setLocation(new RoomTile((short) x, (short) y, (short) 0, RoomTileState.OPEN, true));
        return unit;
    }

    @Test
    void theBotWalksToARandomFreeTileAroundTheUserAndWaitsWhileTheyAreAway() {
        Room room = mock(Room.class);
        com.eu.habbo.habbohotel.rooms.RoomLayout layout = mock(com.eu.habbo.habbohotel.rooms.RoomLayout.class);
        when(room.getLayout()).thenReturn(layout);
        when(layout.getTile(org.mockito.ArgumentMatchers.anyShort(), org.mockito.ArgumentMatchers.anyShort()))
                .thenAnswer(invocation -> new RoomTile(
                        invocation.getArgument(0), invocation.getArgument(1), (short) 0, RoomTileState.OPEN, true));
        when(room.getRoomUnitsAt(org.mockito.ArgumentMatchers.any())).thenReturn(java.util.List.of());
        Bot bot = mock(Bot.class);
        RoomUnit botUnit = unitAt(1, 1);
        when(bot.getRoomUnit()).thenReturn(botUnit);
        when(bot.getRoom()).thenReturn(room);
        when(bot.getFollowingHabboId()).thenReturn(9);
        Habbo habbo = mock(Habbo.class);
        com.eu.habbo.habbohotel.users.HabboInfo info = mock(com.eu.habbo.habbohotel.users.HabboInfo.class);
        when(info.getId()).thenReturn(9);
        when(habbo.getHabboInfo()).thenReturn(info);
        RoomUnit userUnit = unitAt(6, 6);
        when(habbo.getRoomUnit()).thenReturn(userUnit);
        BotFollowHabbo follow = new BotFollowHabbo(bot, habbo, room);

        // Away: nothing happens.
        RoomTile before = botUnit.getGoal();
        follow.step(new java.util.Random(1));
        org.junit.jupiter.api.Assertions.assertSame(before, botUnit.getGoal());

        // Back in the room: a tile next to them.
        when(room.getHabbo(9)).thenReturn(habbo);
        java.util.Set<String> goals = new java.util.HashSet<>();
        try (org.mockito.MockedStatic<com.eu.habbo.Emulator> emulator =
                org.mockito.Mockito.mockStatic(com.eu.habbo.Emulator.class)) {
            emulator.when(com.eu.habbo.Emulator::getPluginManager)
                    .thenReturn(mock(com.eu.habbo.plugin.PluginManager.class));
            java.util.Random random = new java.util.Random(7);
            for (int i = 0; i < 40; i++) {
                RoomTile goal =
                        BotFollowHabbo.randomFreeTileAround(room, userUnit.getCurrentLocation(), botUnit, random);
                assertTrue(BotFollowHabbo.nextTo(goal, userUnit.getCurrentLocation()), goal.x + "," + goal.y);
                goals.add(goal.x + "," + goal.y);
            }
        }
        assertTrue(goals.size() > 1, "the tile is random: " + goals);
    }
}
