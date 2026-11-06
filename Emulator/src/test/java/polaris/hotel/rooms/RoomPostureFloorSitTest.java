package polaris.hotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.hotel.users.User;
import polaris.hotel.wired.core.WiredManager;

/** A floor sit goes out as the official {@code sit 0.55 1}: height 0.55 and the "can stand up" flag. */
class RoomPostureFloorSitTest {

    @Test
    void aFloorSitSendsTheOfficialHeightAndStandFlag() {
        Room room = mock(Room.class);
        RoomUnit unit = new RoomUnit();
        unit.setRoom(room);
        unit.setLocation(new RoomTile((short) 3, (short) 3, (short) 0, RoomTileState.OPEN, true));
        unit.setBodyRotation(RoomUserRotation.EAST);
        User user = mock(User.class);
        when(user.getRoomUnit()).thenReturn(unit);

        try (MockedStatic<WiredManager> ignored = mockStatic(WiredManager.class)) {
            new RoomPostureService(room).makeSit(user);
        }

        assertEquals("0.55 1", unit.getStatus(RoomUnitStatus.SIT));
    }
}
