package com.eu.habbo.networking.gameserver.wired;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraFurniVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraRoomVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraUserVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableReference;
import com.eu.habbo.networking.gameserver.wired.WiredApiRooms.Scope;
import org.junit.jupiter.api.Test;

class HotelWiredApiRoomsTest {

    @Test
    void onlyPermanentPlainCustomVariablesAreExposed() {
        WiredExtraUserVariable permanent = mock(WiredExtraUserVariable.class);
        when(permanent.isPermanentAvailability()).thenReturn(true);
        WiredExtraUserVariable roomOnly = mock(WiredExtraUserVariable.class);
        WiredExtraUserVariable array = mock(WiredExtraUserVariable.class);
        when(array.isPermanentAvailability()).thenReturn(true);
        when(array.isArray()).thenReturn(true);
        WiredExtraFurniVariable furni = mock(WiredExtraFurniVariable.class);
        when(furni.isPermanentAvailability()).thenReturn(true);
        WiredExtraRoomVariable global = mock(WiredExtraRoomVariable.class);
        when(global.isPermanentAvailability()).thenReturn(true);

        assertTrue(HotelWiredApiRooms.isApiDefinition(permanent, Scope.USER));
        assertFalse(HotelWiredApiRooms.isApiDefinition(roomOnly, Scope.USER));
        assertFalse(HotelWiredApiRooms.isApiDefinition(array, Scope.USER));
        assertFalse(HotelWiredApiRooms.isApiDefinition(permanent, Scope.FURNI));
        assertTrue(HotelWiredApiRooms.isApiDefinition(furni, Scope.FURNI));
        assertTrue(HotelWiredApiRooms.isApiDefinition(global, Scope.GLOBAL));
        assertFalse(HotelWiredApiRooms.isApiDefinition(mock(WiredExtraVariableReference.class), Scope.USER));
        assertFalse(HotelWiredApiRooms.isApiDefinition(null, Scope.GLOBAL));
    }

    @Test
    void apiNamesAreTheRoomsVariableNames() {
        assertTrue(HotelWiredApiRooms.NAME.matcher("Points_2").matches());
        assertFalse(HotelWiredApiRooms.NAME.matcher("@altitude").matches());
        assertFalse(HotelWiredApiRooms.NAME.matcher("").matches());
        assertFalse(HotelWiredApiRooms.NAME.matcher("a".repeat(41)).matches());
    }
}
