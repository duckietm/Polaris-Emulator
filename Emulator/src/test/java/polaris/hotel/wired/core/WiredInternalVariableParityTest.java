package polaris.hotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import polaris.hotel.games.GamePlayer;
import polaris.hotel.games.GameTeam;
import polaris.hotel.games.GameTeamColors;
import polaris.hotel.games.wired.WiredGame;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.rooms.RoomUserRotation;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.arrays.WiredArrayChange;

class WiredInternalVariableParityTest {
    @Test
    void userAltitudeEffectAndHandItemWritesReachTheRoomAndRejectInvalidValues() {
        Room room = mock(Room.class);
        RoomUnit unit = new RoomUnit();
        unit.setCurrentLocation(
                new RoomTile((short) 0, (short) 0, (short) 0, polaris.hotel.rooms.RoomTileState.OPEN, true));
        unit.setPreviousLocation(
                new RoomTile((short) 0, (short) 0, (short) 0, polaris.hotel.rooms.RoomTileState.OPEN, true));
        unit.setBodyRotation(RoomUserRotation.NORTH);
        unit.setHeadRotation(RoomUserRotation.NORTH);
        User user = mock(User.class);
        when(room.getUser(unit)).thenReturn(user);

        assertTrue(WiredInternalVariableSupport.writeUserValue(room, unit, "@altitude", 125));
        assertEquals(1.25, unit.getZ());
        assertEquals(1.25, unit.getPreviousLocationZ());
        verify(room).sendComposer(any());
        assertTrue(WiredInternalVariableSupport.writeUserValue(room, unit, "@effect", 42));
        verify(room).giveEffect(unit, 42, Integer.MAX_VALUE);
        assertTrue(WiredInternalVariableSupport.writeUserValue(room, unit, "@handitem", 6));
        verify(room).giveHandItem(user, 6);
        assertFalse(WiredInternalVariableSupport.writeUserValue(room, unit, "@effect", -1));
        assertFalse(WiredInternalVariableSupport.writeUserValue(room, unit, "@handitem", -1));
    }

    @Test
    void teamScoreWritesSetTheDisplayedTotalIncludingMemberScores() {
        Room room = mock(Room.class);
        RoomUnit unit = mock(RoomUnit.class);
        User user = mock(User.class);
        UserInfo info = mock(UserInfo.class);
        GamePlayer player = mock(GamePlayer.class);
        WiredGame game = mock(WiredGame.class);
        GameTeam team = new GameTeam(GameTeamColors.RED);
        team.addTeamScore(4);
        team.addMember(player);
        when(player.getScore()).thenReturn(6);
        when(player.getTeamColor()).thenReturn(GameTeamColors.RED);
        when(user.getUserInfo()).thenReturn(info);
        when(info.getGamePlayer()).thenReturn(player);
        when(room.getUser(unit)).thenReturn(user);
        when(room.getGame(WiredGame.class)).thenReturn(game);
        when(game.getTeam(GameTeamColors.RED)).thenReturn(team);

        assertTrue(WiredInternalVariableSupport.writeUserValue(room, unit, "@team.score", 25));
        assertEquals(25, team.getTotalScore());
        assertTrue(WiredInternalVariableSupport.writeRoomValue(room, "@teams.red.score", 11));
        assertEquals(11, team.getTotalScore());
        assertFalse(WiredInternalVariableSupport.writeRoomValue(room, "@teams.red.score", -1));
        assertEquals(11, team.getTotalScore());
    }

    @Test
    void personalScoreAndBotHandItemsUseTheirOwnTargets() {
        Room room = mock(Room.class);
        RoomUnit unit = mock(RoomUnit.class);
        User user = mock(User.class);
        UserInfo info = mock(UserInfo.class);
        GamePlayer player = new GamePlayer(user, GameTeamColors.RED);
        WiredGame game = mock(WiredGame.class);
        GameTeam team = new GameTeam(GameTeamColors.RED);
        team.addMember(player);
        team.addTeamScore(4);
        when(user.getUserInfo()).thenReturn(info);
        when(user.getRoomUnit()).thenReturn(unit);
        when(info.getGamePlayer()).thenReturn(player);
        when(info.getCurrentGame()).thenAnswer(invocation -> WiredGame.class);
        when(info.getCurrentRoom()).thenReturn(room);
        when(room.getGame(WiredGame.class)).thenReturn(game);
        when(game.getTeamForUser(user)).thenReturn(team);
        when(game.getTeam(GameTeamColors.RED)).thenReturn(team);
        when(room.getUser(unit)).thenReturn(user);

        assertTrue(WiredInternalVariableSupport.writeUserValue(room, unit, "@player.score", 25));
        assertEquals(25, player.getScore());
        assertEquals(25, WiredInternalVariableSupport.readUserValue(room, unit, "@player.score"));
        assertEquals(29, WiredInternalVariableSupport.readUserValue(room, unit, "@team.score"));
        assertTrue(WiredInternalVariableSupport.writeUserValue(room, unit, "@player.score", 3));
        assertEquals(3, player.getScore());
        assertEquals(7, team.getTotalScore());
        assertFalse(WiredInternalVariableSupport.writeUserValue(room, unit, "@player.score", -1));

        RoomUnit bot = mock(RoomUnit.class);
        assertTrue(WiredInternalVariableSupport.writeUserValue(room, bot, "@handitem", 8));
        verify(room).giveHandItem(bot, 8);
    }

    @Test
    void variableUpdateReferencesExposeTheExecutingTriggerAndExactScalarDeltaOnly() {
        Room room = mock(Room.class);
        UserItem trigger = mock(UserItem.class);
        when(trigger.getId()).thenReturn(91);
        WiredEvent event = WiredEvent.builder(WiredEvent.Type.VARIABLE_CHANGED, room)
                .variableDefinitionItemId(72)
                .variableValues(Integer.MIN_VALUE, Integer.MAX_VALUE)
                .variableChangeOrigin(2)
                .build();
        WiredContext context = new WiredContext(event, trigger, mock(WiredServices.class), new WiredState(100));

        assertEquals(91L, WiredInternalVariableSupport.readContextLongValue(context, "@event.variable_update.box_id"));
        assertEquals(
                1L, WiredInternalVariableSupport.readContextLongValue(context, "@event.variable_update.change_type"));
        assertEquals(
                (long) Integer.MIN_VALUE,
                WiredInternalVariableSupport.readContextLongValue(context, "@event.variable_update.old_value"));
        assertEquals(
                (long) Integer.MAX_VALUE,
                WiredInternalVariableSupport.readContextLongValue(context, "@event.variable_update.new_value"));
        assertEquals(
                4294967295L,
                WiredInternalVariableSupport.readContextLongValue(context, "@event.variable_update.difference"));
        assertNull(WiredInternalVariableSupport.readContextValue(context, "@event.variable_update.difference"));
        assertEquals(
                2L, WiredInternalVariableSupport.readContextLongValue(context, "@event.variable_update.change_origin"));

        WiredEvent arrayEvent = WiredEvent.builder(WiredEvent.Type.VARIABLE_CHANGED, room)
                .arrayChange(WiredArrayChange.field(0, 1, 2, 3, 1, 1))
                .build();
        WiredContext arrayContext =
                new WiredContext(arrayEvent, trigger, mock(WiredServices.class), new WiredState(100));
        assertNull(WiredInternalVariableSupport.readContextLongValue(arrayContext, "@event.variable_update.new_value"));
    }
}
