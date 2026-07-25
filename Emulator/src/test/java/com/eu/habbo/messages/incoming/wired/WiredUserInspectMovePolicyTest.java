package com.eu.habbo.messages.incoming.wired;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitType;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import org.junit.jupiter.api.Test;

class WiredUserInspectMovePolicyTest {

    @Test
    void wiredModifyAccessIsRequiredForEveryTargetType() {
        Fixture fixture = fixture(false, 7, false);

        assertFalse(WiredUserInspectMovePolicy.canMove(fixture.room, fixture.requester, unit(9, RoomUnitType.BOT)));
        assertFalse(WiredUserInspectMovePolicy.canMove(fixture.room, fixture.requester, fixture.requesterUnit));
    }

    @Test
    void delegatedEditorCanMoveSelfBotsAndPetsButNotUnknownUnits() {
        Fixture fixture = fixture(true, 7, false);

        assertTrue(WiredUserInspectMovePolicy.canMove(fixture.room, fixture.requester, fixture.requesterUnit));
        assertTrue(WiredUserInspectMovePolicy.canMove(fixture.room, fixture.requester, unit(9, RoomUnitType.BOT)));
        assertTrue(WiredUserInspectMovePolicy.canMove(fixture.room, fixture.requester, unit(10, RoomUnitType.PET)));
        assertFalse(
                WiredUserInspectMovePolicy.canMove(fixture.room, fixture.requester, unit(11, RoomUnitType.UNKNOWN)));
    }

    @Test
    void delegatedEditorCannotMoveAnotherRealUser() {
        Fixture fixture = fixture(true, 99, false);

        assertFalse(WiredUserInspectMovePolicy.canMove(fixture.room, fixture.requester, unit(12, RoomUnitType.USER)));
    }

    @Test
    void roomOwnerCanMoveAnotherRealUser() {
        Fixture fixture = fixture(true, 7, false);

        assertTrue(WiredUserInspectMovePolicy.canMove(fixture.room, fixture.requester, unit(12, RoomUnitType.USER)));
    }

    @Test
    void superwiredEditorCanMoveAnotherRealUser() {
        Fixture fixture = fixture(true, 99, true);

        assertTrue(WiredUserInspectMovePolicy.canMove(fixture.room, fixture.requester, unit(12, RoomUnitType.USER)));
    }

    @Test
    void missingInputsFailClosed() {
        Fixture fixture = fixture(true, 7, true);

        assertFalse(WiredUserInspectMovePolicy.canMove(null, fixture.requester, fixture.requesterUnit));
        assertFalse(WiredUserInspectMovePolicy.canMove(fixture.room, null, fixture.requesterUnit));
        assertFalse(WiredUserInspectMovePolicy.canMove(fixture.room, fixture.requester, null));
    }

    private static Fixture fixture(boolean canModify, int ownerId, boolean superwired) {
        Room room = mock(Room.class);
        Habbo requester = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        RoomUnit requesterUnit = unit(3, RoomUnitType.USER);

        when(room.canModifyWired(requester)).thenReturn(canModify);
        when(room.getOwnerId()).thenReturn(ownerId);
        when(requester.getHabboInfo()).thenReturn(info);
        when(info.getId()).thenReturn(7);
        when(requester.getRoomUnit()).thenReturn(requesterUnit);
        when(requester.hasPermission(Permission.ACC_SUPERWIRED)).thenReturn(superwired);

        return new Fixture(room, requester, requesterUnit);
    }

    private static RoomUnit unit(int id, RoomUnitType type) {
        RoomUnit roomUnit = mock(RoomUnit.class);
        when(roomUnit.getId()).thenReturn(id);
        when(roomUnit.getRoomUnitType()).thenReturn(type);
        return roomUnit;
    }

    private record Fixture(Room room, Habbo requester, RoomUnit requesterUnit) {}
}
