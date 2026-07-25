package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.List;
import org.junit.jupiter.api.Test;

class WiredOpacityServiceTest {

    @Test
    void globalAndPerUserStatesComposeWithoutLeakingAcrossUsers() {
        Room room = room(41);
        HabboItem floor = item(4101, 41, FurnitureType.FLOOR);
        HabboItem wall = item(4102, 41, FurnitureType.WALL);
        when(room.getHabboItem(4101)).thenReturn(floor);
        when(room.getHabboItem(4102)).thenReturn(wall);
        WiredOpacityService service = new WiredOpacityService(room, 10);

        assertEquals(
                List.of(new WiredOpacityState(4101, false, 30, false)), service.applyGlobal(List.of(floor), 30, false));
        service.applyGlobal(List.of(wall), 50, true);
        service.applyUser(7, List.of(floor, wall), 100, false);

        assertEquals(
                List.of(new WiredOpacityState(4101, false, 100, false), new WiredOpacityState(4102, true, 100, false)),
                service.effective(7, List.of(4102, 4101)).stream()
                        .sorted(java.util.Comparator.comparingInt(WiredOpacityState::itemId))
                        .toList());
        assertEquals(
                List.of(new WiredOpacityState(4101, false, 30, false), new WiredOpacityState(4102, true, 50, true)),
                service.effective(8, List.of(4101, 4102)));
        assertEquals(4, service.stateCount());
    }

    @Test
    void stateCapIsRoomWideAndDeterministic() {
        Room room = room(42);
        HabboItem lower = item(4201, 42, FurnitureType.FLOOR);
        HabboItem higher = item(4202, 42, FurnitureType.FLOOR);
        when(room.getHabboItem(4201)).thenReturn(lower);
        when(room.getHabboItem(4202)).thenReturn(higher);
        WiredOpacityService service = new WiredOpacityService(room, 1);

        assertEquals(
                List.of(new WiredOpacityState(4201, false, 25, false)),
                service.applyGlobal(List.of(higher, lower), 25, false));
        assertTrue(service.applyUser(9, List.of(lower), 10, true).isEmpty());
        assertEquals(1, service.stateCount());
    }

    @Test
    void recycledItemIdsFailClosed() {
        Room room = room(43);
        HabboItem stale = item(4301, 43, FurnitureType.FLOOR);
        HabboItem replacement = item(4301, 43, FurnitureType.FLOOR);
        when(room.getHabboItem(4301)).thenReturn(replacement);

        WiredOpacityService service = new WiredOpacityService(room, 10);
        assertTrue(service.applyGlobal(List.of(stale), 10, true).isEmpty());
        assertEquals(0, service.stateCount());
    }

    @Test
    void itemUserAndRoomLifecycleCleanupReleaseEveryState() {
        Room room = room(44);
        HabboItem first = item(4401, 44, FurnitureType.FLOOR);
        HabboItem second = item(4402, 44, FurnitureType.FLOOR);
        when(room.getHabboItem(4401)).thenReturn(first);
        when(room.getHabboItem(4402)).thenReturn(second);
        WiredOpacityService service = new WiredOpacityService(room, 10);

        service.applyGlobal(List.of(first, second), 50, false);
        service.applyUser(11, List.of(first), 20, true);
        service.applyUser(12, List.of(second), 20, true);
        assertEquals(4, service.stateCount());

        service.forgetItem(first);
        assertEquals(2, service.stateCount());
        service.forgetUser(12);
        assertEquals(1, service.stateCount());
        service.dispose();

        assertEquals(0, service.stateCount());
        assertTrue(service.snapshot(11).isEmpty());
    }

    private static Room room(int roomId) {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(roomId);
        return room;
    }

    private static HabboItem item(int itemId, int roomId, FurnitureType type) {
        HabboItem item = mock(HabboItem.class);
        Item baseItem = mock(Item.class);
        when(item.getId()).thenReturn(itemId);
        when(item.getRoomId()).thenReturn(roomId);
        when(item.getBaseItem()).thenReturn(baseItem);
        when(baseItem.getType()).thenReturn(type);
        return item;
    }
}
