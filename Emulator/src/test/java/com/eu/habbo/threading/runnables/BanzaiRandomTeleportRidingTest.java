package com.eu.habbo.threading.runnables;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.pets.RideablePet;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import org.junit.jupiter.api.Test;

/** A Banzai teleporter takes a ridden horse along with its rider. */
class BanzaiRandomTeleportRidingTest {

    @Test
    void theRiddenHorseIsFound() {
        Room room = mock(Room.class);
        RoomUnit riderUnit = mock(RoomUnit.class);
        RoomUnit horseUnit = mock(RoomUnit.class);
        Habbo rider = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        RideablePet horse = mock(RideablePet.class);
        when(room.getHabbo(riderUnit)).thenReturn(rider);
        when(rider.getHabboInfo()).thenReturn(info);
        when(info.getRiding()).thenReturn(horse);
        when(horse.getRoomUnit()).thenReturn(horseUnit);

        assertSame(horseUnit, BanzaiRandomTeleport.ridingHorse(room, riderUnit));
        assertNull(BanzaiRandomTeleport.ridingHorse(room, mock(RoomUnit.class)));
    }
}
