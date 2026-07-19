package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboInventory;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.users.inventory.ItemsComponent;
import com.eu.habbo.plugin.PluginManager;
import com.eu.habbo.threading.ThreadPooling;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoomPickupOwnershipContractTest {

    @Test
    void pickupReturnsItemToThePickerWhenThePickerOwnsIt() throws Exception {
        RecordingRoom room = new RecordingRoom();
        RoomLayout layout = mock(RoomLayout.class);
        setField(room, "layout", layout);
        HabboItem item = mock(HabboItem.class);
        Item baseItem = mock(Item.class);
        when(item.getId()).thenReturn(1001);
        when(item.getUserId()).thenReturn(7);
        when(item.getBaseItem()).thenReturn(baseItem);
        when(baseItem.getType()).thenReturn(FurnitureType.FLOOR);
        when(baseItem.getWidth()).thenReturn(1);
        when(baseItem.getLength()).thenReturn(1);

        Habbo picker = mock(Habbo.class);
        HabboInfo pickerInfo = mock(HabboInfo.class);
        HabboInventory inventory = mock(HabboInventory.class);
        ItemsComponent items = mock(ItemsComponent.class);
        GameClient client = mock(GameClient.class);
        when(picker.getHabboInfo()).thenReturn(pickerInfo);
        when(pickerInfo.getId()).thenReturn(7);
        when(picker.getInventory()).thenReturn(inventory);
        when(inventory.getItemsComponent()).thenReturn(items);
        when(picker.getClient()).thenReturn(client);

        PluginManager plugins = mock(PluginManager.class);
        ThreadPooling threading = mock(ThreadPooling.class);
        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class);
             MockedStatic<BuildersClubRoomSupport> buildersClub =
                     mockStatic(BuildersClubRoomSupport.class)) {
            emulator.when(Emulator::getPluginManager).thenReturn(plugins);
            emulator.when(Emulator::getThreading).thenReturn(threading);
            buildersClub.when(() ->
                    BuildersClubRoomSupport.isTrackedItem(1001))
                    .thenReturn(false);

            room.pickUpItem(item, picker);
        }

        assertEquals(1001, room.removedItemId);
        verify(item).onPickUp(room);
        verify(item).setRoomId(0);
        verify(item).needsUpdate(true);
        verify(items).addItem(item);
        verify(client).sendResponse(any(
                com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer.class));
        verify(client).sendResponse(any(
                com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer.class));
        verify(threading).run(item);
    }

    private static void setField(Room room, String name, Object value)
            throws ReflectiveOperationException {
        Field field = Room.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(room, value);
    }

    private static final class RecordingRoom extends Room {
        private int removedItemId;

        private RecordingRoom() {
            super(41, 7);
        }

        @Override
        void removeHabboItem(int id) {
            this.removedItemId = id;
        }

        @Override
        public double getStackHeight(
                short x,
                short y,
                boolean calculateHeightmap) {
            return 0;
        }

        @Override
        public void updateTiles(java.util.Collection<RoomTile> tiles) {
        }

        @Override
        public void sendComposer(
                com.eu.habbo.messages.ServerMessage message) {
        }
    }
}
