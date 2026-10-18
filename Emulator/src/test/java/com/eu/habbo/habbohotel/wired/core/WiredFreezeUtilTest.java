package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.games.GamePlayer;
import com.eu.habbo.habbohotel.games.GameTeamColors;
import com.eu.habbo.habbohotel.games.wired.WiredGame;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.messages.ServerMessage;
import org.junit.jupiter.api.Test;

/** Freeze and unfreeze send only the effect, as in Habbo; a Wired team member gets the team effect back. */
class WiredFreezeUtilTest {

    @Test
    void freezingAStandingUserSendsOnlyTheEffect() {
        Room room = mock(Room.class);
        RoomUnit unit = new RoomUnit();

        WiredFreezeUtil.freeze(room, unit, 218, false);

        verify(room).giveEffect(unit, 218, Integer.MAX_VALUE);
        verify(room, never()).sendComposer(any(ServerMessage.class));
    }

    @Test
    void unfreezingGivesAWiredTeamMemberTheTeamEffectWithoutAUserUpdate() {
        Room room = mock(Room.class);
        RoomUnit unit = new RoomUnit();
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        GamePlayer player = mock(GamePlayer.class);
        when(room.getHabbo(unit)).thenReturn(habbo);
        when(habbo.getHabboInfo()).thenReturn(info);
        when(info.getCurrentGame()).thenAnswer(invocation -> WiredGame.class);
        when(info.getGamePlayer()).thenReturn(player);
        when(player.getTeamColor()).thenReturn(GameTeamColors.BLUE);
        WiredFreezeUtil.freeze(room, unit, 218, false);
        unit.setEffectId(218, 0); // the room mock does not wear the effect itself

        WiredFreezeUtil.unfreeze(room, unit);

        verify(room).giveEffect(unit, WiredGame.BLUE_EFFECT_ID, -1);
        verify(room, never()).sendComposer(any(ServerMessage.class));
    }

    @Test
    void aWiredTeleportEndsACancellingFreezeWithoutAnEffectPacketOfItsOwn() {
        Room room = mock(Room.class);
        RoomUnit unit = new RoomUnit();
        WiredFreezeUtil.freeze(room, unit, 218, true);
        unit.setEffectId(218, 0);

        WiredFreezeUtil.onWiredTeleport(room, unit);

        assertFalse(WiredFreezeUtil.isFrozen(unit));
        assertEquals(0, unit.getEffectId());
        verify(room, never()).giveEffect(any(RoomUnit.class), anyInt(), eq(-1));
    }

    @Test
    void aWiredTeleportKeepsAFreezeThatDoesNotCancelOnTeleport() {
        Room room = mock(Room.class);
        RoomUnit unit = new RoomUnit();
        WiredFreezeUtil.freeze(room, unit, 218, false);

        WiredFreezeUtil.onWiredTeleport(room, unit);

        assertTrue(WiredFreezeUtil.isFrozen(unit));
    }
}
