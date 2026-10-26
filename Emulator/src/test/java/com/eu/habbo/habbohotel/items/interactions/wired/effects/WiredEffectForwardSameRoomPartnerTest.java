package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUserRotation;
import com.eu.habbo.habbohotel.users.HabboItem;
import org.junit.jupiter.api.Test;

/** Teleport to Room onto a teleporter whose partner stands in the same room moves the user there, as AIR does. */
class WiredEffectForwardSameRoomPartnerTest {
    @Test
    void theUserLandsOnThePartnerFacingItsWay() {
        Room room = mock(Room.class);
        RoomLayout layout = mock(RoomLayout.class);
        when(room.getLayout()).thenReturn(layout);
        RoomTile start = new RoomTile((short) 1, (short) 1, (short) 0, RoomTileState.OPEN, true);
        RoomTile partnerTile = new RoomTile((short) 6, (short) 4, (short) 0, RoomTileState.OPEN, true);
        when(layout.getTile((short) 6, (short) 4)).thenReturn(partnerTile);
        HabboItem partner = mock(HabboItem.class);
        when(partner.getX()).thenReturn((short) 6);
        when(partner.getY()).thenReturn((short) 4);
        when(partner.getRotation()).thenReturn(2);
        RoomUnit unit = new RoomUnit();
        unit.setRoom(room);
        unit.setLocation(start);

        WiredEffectForwardUserToRoom.landOnPartner(room, unit, partner);

        assertEquals(partnerTile, unit.getCurrentLocation());
        assertEquals(RoomUserRotation.EAST, unit.getBodyRotation());
    }
}
