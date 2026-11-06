package polaris.hotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import polaris.hotel.pets.RideablePet;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;

/** A wired move of a rider takes the horse along one lower, and a move of the horse takes the rider along. */
class WiredUserMovementRidingTest {

    @Test
    void riderAndHorseArePartners() {
        Room room = mock(Room.class);
        RoomUnit riderUnit = mock(RoomUnit.class);
        RoomUnit horseUnit = mock(RoomUnit.class);
        User rider = mock(User.class);
        UserInfo info = mock(UserInfo.class);
        RideablePet horse = mock(RideablePet.class);
        when(rider.getUserInfo()).thenReturn(info);
        when(rider.getRoomUnit()).thenReturn(riderUnit);
        when(info.getRiding()).thenReturn(horse);
        when(horse.getRoomUnit()).thenReturn(horseUnit);
        when(horse.getRider()).thenReturn(rider);
        when(room.getUser(riderUnit)).thenReturn(rider);
        when(room.getPet(horseUnit)).thenReturn(horse);

        WiredUserMovementHelper.RidingPartner ofRider = WiredUserMovementHelper.ridingPartner(room, riderUnit);
        assertSame(horseUnit, ofRider.unit());
        assertEquals(-1.0D, ofRider.zOffset());

        WiredUserMovementHelper.RidingPartner ofHorse = WiredUserMovementHelper.ridingPartner(room, horseUnit);
        assertSame(riderUnit, ofHorse.unit());
        assertEquals(1.0D, ofHorse.zOffset());

        assertNull(WiredUserMovementHelper.ridingPartner(room, mock(RoomUnit.class)));
    }
}
