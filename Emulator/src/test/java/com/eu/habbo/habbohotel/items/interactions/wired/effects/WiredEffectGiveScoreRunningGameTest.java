package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTestFixtures.base;
import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTestFixtures.context;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.games.Game;
import com.eu.habbo.habbohotel.games.GamePlayer;
import com.eu.habbo.habbohotel.games.GameState;
import com.eu.habbo.habbohotel.games.wired.WiredGame;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import org.junit.jupiter.api.Test;

/** Give Score (to the user) does nothing while the user's game is not running, as in Habbo. */
class WiredEffectGiveScoreRunningGameTest {

    @Test
    void noScoreOutsideARunningGame() {
        Room room = mock(Room.class);
        RoomUnit unit = mock(RoomUnit.class);
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        GamePlayer player = mock(GamePlayer.class);
        Game game = mock(Game.class);
        when(game.getState()).thenReturn(GameState.IDLE);
        when(room.getHabbo(unit)).thenReturn(habbo);
        when(habbo.getHabboInfo()).thenReturn(info);
        when(info.getCurrentGame()).thenAnswer(invocation -> WiredGame.class);
        when(info.getGamePlayer()).thenReturn(player);
        when(room.getGame(WiredGame.class)).thenReturn(game);

        WiredEffectGiveScore box = new WiredEffectGiveScore(1, 1, base(), "", 0, 0);
        box.execute(context(room, unit));
        verify(player, never()).addScore(anyInt(), anyBoolean());

        when(game.getState()).thenReturn(GameState.RUNNING);
        box.execute(context(room, unit));
        verify(player).addScore(anyInt(), anyBoolean());
    }
}
