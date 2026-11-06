package polaris.hotel.items.interactions.wired.effects;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.base;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.context;

import org.junit.jupiter.api.Test;
import polaris.hotel.games.Game;
import polaris.hotel.games.GamePlayer;
import polaris.hotel.games.GameState;
import polaris.hotel.games.wired.WiredGame;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;

/** Give Score (to the user) does nothing while the user's game is not running, as in Habbo. */
class WiredEffectGiveScoreRunningGameTest {

    @Test
    void noScoreOutsideARunningGame() {
        Room room = mock(Room.class);
        RoomUnit unit = mock(RoomUnit.class);
        User user = mock(User.class);
        UserInfo info = mock(UserInfo.class);
        GamePlayer player = mock(GamePlayer.class);
        Game game = mock(Game.class);
        when(game.getState()).thenReturn(GameState.IDLE);
        when(room.getUser(unit)).thenReturn(user);
        when(user.getUserInfo()).thenReturn(info);
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
