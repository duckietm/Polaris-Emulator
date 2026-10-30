package com.eu.habbo.habbohotel.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class NavigatorLiftedRoomsLoadTest {
    @Test
    void keepsRowOrderAndFallsBackToTheRoomNameForAnEmptyCaption() throws Exception {
        ResultSet set = mock(ResultSet.class);
        when(set.next()).thenReturn(true, true, false);
        when(set.getInt("room_id")).thenReturn(7, 9);
        when(set.getInt("area_id")).thenReturn(2, 0);
        when(set.getString("image")).thenReturn("officialrooms_hq/a.png", (String) null);
        when(set.getString("caption")).thenReturn("Lobby", " ");
        when(set.getString("room_name")).thenReturn("Pool room");

        List<NavigatorLiftedRoom> rooms = NavigatorManager.readLiftedRooms(set);

        assertEquals(2, rooms.size());
        assertEquals(7, rooms.get(0).getRoomId());
        assertEquals(2, rooms.get(0).getAreaId());
        assertEquals("officialrooms_hq/a.png", rooms.get(0).getImage());
        assertEquals("Lobby", rooms.get(0).getCaption());
        assertEquals(9, rooms.get(1).getRoomId());
        assertEquals("", rooms.get(1).getImage());
        assertEquals("Pool room", rooms.get(1).getCaption());
    }
}
