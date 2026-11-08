package polaris.hotel.items.interactions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;

/** A vending machine serves one use per cycle, and only to a user still in the room. */
class InteractionVendingMachineUseTest {

    @Test
    void aSecondClickWhileServingIsRefused() {
        InteractionVendingMachine machine = mock(InteractionVendingMachine.class, CALLS_REAL_METHODS);
        machine.setExtradata("0");

        assertTrue(machine.claimUse());
        assertEquals("1", machine.getExtradata());
        assertFalse(machine.claimUse());

        machine.setExtradata("0");
        assertTrue(machine.claimUse());
    }

    @Test
    void aUserWhoLeftIsNotServed() {
        Room room = mock(Room.class);
        RoomUnit unit = mock(RoomUnit.class);
        User user = mock(User.class);
        when(user.getUserInfo()).thenReturn(mock(UserInfo.class));
        when(user.getRoomUnit()).thenReturn(unit);
        when(unit.getRoom()).thenReturn(room);

        assertTrue(InteractionVendingMachine.isStillServed(user, unit, room));
        assertFalse(InteractionVendingMachine.isStillServed(null, unit, room));
        assertFalse(InteractionVendingMachine.isStillServed(user, unit, mock(Room.class)));
        assertFalse(InteractionVendingMachine.isStillServed(user, mock(RoomUnit.class), room));
    }
}
