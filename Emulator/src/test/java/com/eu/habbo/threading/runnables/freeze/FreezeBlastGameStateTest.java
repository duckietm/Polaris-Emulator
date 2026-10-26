package com.eu.habbo.threading.runnables.freeze;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.games.GameState;
import com.eu.habbo.habbohotel.games.GameTeamColors;
import com.eu.habbo.habbohotel.games.freeze.FreezeGame;
import com.eu.habbo.habbohotel.games.freeze.FreezeGamePlayer;
import com.eu.habbo.habbohotel.items.interactions.games.freeze.InteractionFreezeTile;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.threading.ThreadPooling;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

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
        Habbo throwerHabbo = habbo(thrower);
        Habbo victimHabbo = habbo(victim);
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
        when(room.getHabbosAt(anyShort(), anyShort())).thenReturn(Set.of(victimHabbo));

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class);
                MockedStatic<AchievementManager> achievements = mockStatic(AchievementManager.class)) {
            emulator.when(Emulator::getThreading).thenReturn(mock(ThreadPooling.class));
            emulator.when(Emulator::getGameEnvironment).thenReturn(mock(GameEnvironment.class, RETURNS_DEEP_STUBS));

            new FreezeHandleSnowballExplosion(new FreezeThrowSnowball(throwerHabbo, freezeTile, room)).run();
        }
        return victim;
    }

    private static FreezeGamePlayer player(GameTeamColors team) {
        FreezeGamePlayer player = mock(FreezeGamePlayer.class);
        when(player.getTeamColor()).thenReturn(team);
        when(player.canGetFrozen()).thenReturn(true);
        return player;
    }

    private static Habbo habbo(FreezeGamePlayer player) {
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        when(habbo.getHabboInfo()).thenReturn(info);
        when(info.getGamePlayer()).thenReturn(player);
        return habbo;
    }
}
