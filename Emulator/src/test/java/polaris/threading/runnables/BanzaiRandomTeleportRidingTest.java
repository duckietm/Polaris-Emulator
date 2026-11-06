package polaris.threading.runnables;

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

/** A Banzai teleporter takes a ridden horse along with its rider. */
class BanzaiRandomTeleportRidingTest {

    @Test
    void theRiddenHorseIsFound() {
        Room room = mock(Room.class);
        RoomUnit riderUnit = mock(RoomUnit.class);
        RoomUnit horseUnit = mock(RoomUnit.class);
        User rider = mock(User.class);
        UserInfo info = mock(UserInfo.class);
        RideablePet horse = mock(RideablePet.class);
        when(room.getUser(riderUnit)).thenReturn(rider);
        when(rider.getUserInfo()).thenReturn(info);
        when(info.getRiding()).thenReturn(horse);
        when(horse.getRoomUnit()).thenReturn(horseUnit);

        assertSame(horseUnit, BanzaiRandomTeleport.ridingHorse(room, riderUnit));
        assertNull(BanzaiRandomTeleport.ridingHorse(room, mock(RoomUnit.class)));
    }
}
