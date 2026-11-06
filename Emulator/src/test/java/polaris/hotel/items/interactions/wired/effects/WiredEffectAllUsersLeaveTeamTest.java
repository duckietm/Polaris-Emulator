package polaris.hotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.base;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.context;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.row;

import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import polaris.hotel.games.Game;
import polaris.hotel.games.wired.WiredGame;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.wired.core.WiredEvent;
import polaris.hotel.wired.core.WiredSourceUtil;

/**
 * "All users leave team" on its default source empties every team in the room, which is what its
 * name says. The dialog also offers narrower user sources, which were saved and then ignored: any
 * source still removed everyone.
 */
class WiredEffectAllUsersLeaveTeamTest {

    @Test
    void theDefaultSourceEmptiesEveryTeamInTheRoom() throws Exception {
        Room room = mock(Room.class);
        Game game = mock(Game.class);
        RoomUnit actorUnit = mock(RoomUnit.class);
        User actor = playing(room, game, actorUnit);
        User bystander = playing(room, game, mock(RoomUnit.class));
        ConcurrentHashMap<Integer, User> present = new ConcurrentHashMap<>();
        present.put(1, actor);
        present.put(2, bystander);
        when(room.getCurrentUsers()).thenReturn(present);

        WiredEffectAllUsersLeaveTeam box = box(room, WiredSourceUtil.SOURCE_TRIGGER);
        box.execute(context(room, actorUnit));

        verify(game).removeUser(actor);
        verify(game).removeUser(bystander);
    }

    @Test
    void aNarrowerSourceOnlyRemovesTheUsersItResolves() throws Exception {
        Room room = mock(Room.class);
        Game game = mock(Game.class);
        RoomUnit actorUnit = mock(RoomUnit.class);
        User actor = playing(room, game, actorUnit);
        User bystander = playing(room, game, mock(RoomUnit.class));
        ConcurrentHashMap<Integer, User> present = new ConcurrentHashMap<>();
        present.put(1, actor);
        present.put(2, bystander);
        when(room.getCurrentUsers()).thenReturn(present);

        WiredEffectAllUsersLeaveTeam box = box(room, WiredSourceUtil.SOURCE_SIGNAL);
        box.execute(context(WiredEvent.Type.SIGNAL_RECEIVED, room, actorUnit));

        verify(game).removeUser(actor);
        verify(game, never()).removeUser(bystander);
    }

    @Test
    void aLegacyRowThatIsNotANumberLoadsWithNoDelay() throws Exception {
        WiredEffectAllUsersLeaveTeam box = new WiredEffectAllUsersLeaveTeam(1, 1, base(), "", 0, 0);

        box.loadWiredData(row("abc"), null);
        assertEquals(0, box.getDelay());

        box.loadWiredData(row("7"), null);
        assertEquals(7, box.getDelay());
    }

    private static WiredEffectAllUsersLeaveTeam box(Room room, int userSource) throws Exception {
        WiredEffectAllUsersLeaveTeam box = new WiredEffectAllUsersLeaveTeam(1, 1, base(), "", 0, 0);
        box.loadWiredData(row("{\"delay\":0,\"userSource\":" + userSource + "}"), room);
        return box;
    }

    private static User playing(Room room, Game game, RoomUnit unit) {
        User user = mock(User.class);
        UserInfo info = mock(UserInfo.class);
        doReturn(WiredGame.class).when(info).getCurrentGame();
        when(user.getUserInfo()).thenReturn(info);
        when(room.getUser(unit)).thenReturn(user);
        when(room.getGame(WiredGame.class)).thenReturn(game);
        return user;
    }
}
