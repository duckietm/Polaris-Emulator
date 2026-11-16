package com.srth.plugin.habbo.Delitos;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import org.junit.jupiter.api.Test;

class PoliceTransportManagerTest {

    @Test
    void gateApproachUsesTheOutsideSideOfTheDoor() {
        Room room = mock(Room.class);
        RoomLayout layout = mock(RoomLayout.class, CALLS_REAL_METHODS);
        RoomTile gateBase = tile(10, 10);
        RoomTile inside = tile(11, 10);
        RoomTile outside = tile(9, 10);
        doReturn(inside).when(layout).getTile((short) 11, (short) 10);
        doReturn(outside).when(layout).getTile((short) 9, (short) 10);
        when(room.getLayout()).thenReturn(layout);

        assertSame(outside, PoliceTransportManager.findGateApproachTile(room, gateBase, 2));
    }

    private static RoomTile tile(int x, int y) {
        return new RoomTile((short) x, (short) y, (short) 0, RoomTileState.OPEN, true);
    }
}
