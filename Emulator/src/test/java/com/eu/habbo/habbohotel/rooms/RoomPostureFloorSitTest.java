package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/** A floor sit goes out as the official {@code sit 0.55 1}: height 0.55 and the "can stand up" flag. */
class RoomPostureFloorSitTest {

    @Test
    void aFloorSitSendsTheOfficialHeightAndStandFlag() {
        Room room = mock(Room.class);
        RoomUnit unit = new RoomUnit();
        unit.setRoom(room);
        unit.setLocation(new RoomTile((short) 3, (short) 3, (short) 0, RoomTileState.OPEN, true));
        unit.setBodyRotation(RoomUserRotation.EAST);
        Habbo habbo = mock(Habbo.class);
        when(habbo.getRoomUnit()).thenReturn(unit);

        try (MockedStatic<WiredManager> ignored = mockStatic(WiredManager.class)) {
            new RoomPostureService(room).makeSit(habbo);
        }

        assertEquals("0.55 1", unit.getStatus(RoomUnitStatus.SIT));
    }
}
