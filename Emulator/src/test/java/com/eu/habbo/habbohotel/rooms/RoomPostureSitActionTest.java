package com.eu.habbo.habbohotel.rooms;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredUserActionType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/** AIR: sitting down on a seat is the user's "sit" action; sitting again on the same seat is not. */
class RoomPostureSitActionTest {
    @Test
    void sittingDownOnASeatFiresSitOnce() {
        Room room = mock(Room.class);
        RoomLayout layout = mock(RoomLayout.class);
        when(room.getLayout()).thenReturn(layout);
        RoomTile tile = new RoomTile((short) 2, (short) 2, (short) 0, RoomTileState.SIT, true);
        when(layout.getTile((short) 2, (short) 2)).thenReturn(tile);
        Item chairType = mock(Item.class);
        when(chairType.allowSit()).thenReturn(true);
        when(chairType.getHeight()).thenReturn(1.0);
        HabboItem chair = mock(HabboItem.class);
        when(chair.getBaseItem()).thenReturn(chairType);
        when(room.getTallestChair(tile)).thenReturn(chair);
        RoomUnit unit = new RoomUnit();
        unit.setRoomUnitType(RoomUnitType.USER);
        unit.setLocation(tile);
        RoomPostureService posture = new RoomPostureService(room);

        try (MockedStatic<WiredManager> manager = mockStatic(WiredManager.class, CALLS_REAL_METHODS)) {
            manager.when(() -> WiredManager.triggerUserPerformsAction(eq(room), eq(unit), anyInt(), anyInt()))
                    .thenReturn(true);

            posture.settleOnChair(unit, false);
            unit.sitUpdate = true;
            posture.settleOnChair(unit, false);

            manager.verify(
                    () -> WiredManager.triggerUserPerformsAction(room, unit, WiredUserActionType.SIT, -1),
                    org.mockito.Mockito.times(1));
        }
    }
}
