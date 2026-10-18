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
}
