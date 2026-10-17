package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

/** A wired move of a rider takes the horse along one lower, and a move of the horse takes the rider along. */
class WiredUserMovementRidingTest {

    @Test
    void riderAndHorseArePartners() {
        Room room = mock(Room.class);
        RoomUnit riderUnit = mock(RoomUnit.class);
        RoomUnit horseUnit = mock(RoomUnit.class);
        Habbo rider = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        RideablePet horse = mock(RideablePet.class);
        when(rider.getHabboInfo()).thenReturn(info);
        when(rider.getRoomUnit()).thenReturn(riderUnit);
        when(info.getRiding()).thenReturn(horse);
        when(horse.getRoomUnit()).thenReturn(horseUnit);
        when(horse.getRider()).thenReturn(rider);
        when(room.getHabbo(riderUnit)).thenReturn(rider);
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
