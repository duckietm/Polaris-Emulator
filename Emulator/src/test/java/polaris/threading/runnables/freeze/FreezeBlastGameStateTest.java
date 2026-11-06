package polaris.threading.runnables.freeze;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.hotel.GameEnvironment;
import polaris.hotel.achievements.AchievementManager;
import polaris.hotel.games.GameState;
import polaris.hotel.games.GameTeamColors;
import polaris.hotel.games.freeze.FreezeGame;
import polaris.hotel.games.freeze.FreezeGamePlayer;
import polaris.hotel.items.interactions.games.freeze.InteractionFreezeTile;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomLayout;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomTileState;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.threading.ThreadPooling;

/** A freeze blast takes a life only while the game runs. */
class FreezeBlastGameStateTest {

    @Test
    void aBlastAfterTheGameEndedFreezesNobody() {
        FreezeGamePlayer victim = this.blast(GameState.IDLE);
        verify(victim, never()).freeze();
    }

    @Test
    void aBlastWhileTheGameRunsFreezesTheVictim() {
        FreezeGamePlayer victim = this.blast(GameState.RUNNING);
        verify(victim).freeze();
    }

    private FreezeGamePlayer blast(GameState state) {
        RoomTile tile = new RoomTile((short) 1, (short) 1, (short) 0, RoomTileState.OPEN, true);
        InteractionFreezeTile freezeTile = mock(InteractionFreezeTile.class);
        when(freezeTile.getX()).thenReturn((short) 1);
        when(freezeTile.getY()).thenReturn((short) 1);

        FreezeGamePlayer thrower = player(GameTeamColors.RED);
        FreezeGamePlayer victim = player(GameTeamColors.BLUE);
        User throwerUser = user(thrower);
        User victimUser = user(victim);
        thrower.nextHorizontal = true;

        FreezeGame game = mock(FreezeGame.class);
        game.state = state;
        when(game.affectedTilesByExplosion(anyShort(), anyShort(), anyInt())).thenReturn(Set.of(tile));

        Room room = mock(Room.class);
        RoomLayout layout = mock(RoomLayout.class);
        when(room.getLayout()).thenReturn(layout);
        when(layout.getTile(anyShort(), anyShort())).thenReturn(tile);
        when(room.getGame(FreezeGame.class)).thenReturn(game);
        when(room.getItemsAt(tile)).thenReturn(Set.of(freezeTile));
        when(room.getUsersAt(anyShort(), anyShort())).thenReturn(Set.of(victimUser));

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class);
                MockedStatic<AchievementManager> achievements = mockStatic(AchievementManager.class)) {
            emulator.when(Emulator::getThreading).thenReturn(mock(ThreadPooling.class));
            emulator.when(Emulator::getGameEnvironment).thenReturn(mock(GameEnvironment.class, RETURNS_DEEP_STUBS));

            new FreezeHandleSnowballExplosion(new FreezeThrowSnowball(throwerUser, freezeTile, room)).run();
        }
        return victim;
    }

    private static FreezeGamePlayer player(GameTeamColors team) {
        FreezeGamePlayer player = mock(FreezeGamePlayer.class);
        when(player.getTeamColor()).thenReturn(team);
        when(player.canGetFrozen()).thenReturn(true);
        return player;
    }

    private static User user(FreezeGamePlayer player) {
        User user = mock(User.class);
        UserInfo info = mock(UserInfo.class);
        when(user.getUserInfo()).thenReturn(info);
        when(info.getGamePlayer()).thenReturn(player);
        return user;
    }
}
