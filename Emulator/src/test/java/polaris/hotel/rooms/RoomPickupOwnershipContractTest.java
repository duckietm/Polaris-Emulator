package polaris.hotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.FurnitureType;
import polaris.hotel.items.Item;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserInventory;
import polaris.hotel.users.UserItem;
import polaris.hotel.users.inventory.ItemsComponent;
import polaris.plugin.PluginManager;
import polaris.threading.ThreadPooling;

class RoomPickupOwnershipContractTest {

    @Test
    void pickupReturnsItemToThePickerWhenThePickerOwnsIt() throws Exception {
        assertPickupReturnsItemToOwner(false);
    }

    @Test
    void managerPickupMatchesTheCanonicalOwnerReturnBehavior() throws Exception {
        assertPickupReturnsItemToOwner(true);
    }

    private static void assertPickupReturnsItemToOwner(boolean throughManager) throws Exception {
        RecordingRoom room = new RecordingRoom();
        RoomLayout layout = mock(RoomLayout.class);
        setField(room, "layout", layout);
        UserItem item = mock(UserItem.class);
        Item baseItem = mock(Item.class);
        when(item.getId()).thenReturn(1001);
        when(item.getUserId()).thenReturn(7);
        when(item.getBaseItem()).thenReturn(baseItem);
        when(baseItem.getType()).thenReturn(FurnitureType.FLOOR);
        when(baseItem.getWidth()).thenReturn(1);
        when(baseItem.getLength()).thenReturn(1);

        User picker = mock(User.class);
        UserInfo pickerInfo = mock(UserInfo.class);
        UserInventory inventory = mock(UserInventory.class);
        ItemsComponent items = mock(ItemsComponent.class);
        GameClient client = mock(GameClient.class);
        when(picker.getUserInfo()).thenReturn(pickerInfo);
        when(pickerInfo.getId()).thenReturn(7);
        when(picker.getInventory()).thenReturn(inventory);
        when(inventory.getItemsComponent()).thenReturn(items);
        when(picker.getClient()).thenReturn(client);

        PluginManager plugins = mock(PluginManager.class);
        ThreadPooling threading = mock(ThreadPooling.class);
        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class);
                MockedStatic<BuildersClubRoomSupport> buildersClub = mockStatic(BuildersClubRoomSupport.class)) {
            emulator.when(Emulator::getPluginManager).thenReturn(plugins);
            emulator.when(Emulator::getThreading).thenReturn(threading);
            buildersClub.when(() -> BuildersClubRoomSupport.isTrackedItem(1001)).thenReturn(false);

            if (throughManager) {
                room.getItemManager().pickUpItem(item, picker);
            } else {
                room.pickUpItem(item, picker);
            }
        }

        assertEquals(1001, room.removedItemId);
        verify(item).onPickUp(room);
        verify(item).setRoomId(0);
        verify(item).needsUpdate(true);
        verify(items).addItem(item);
        verify(client).sendResponse(any(polaris.messages.outgoing.inventory.AddUserItemComposer.class));
        // Habbo sends the picker the item itself, not an inventory reload.
        verify(client).sendResponse(any(polaris.messages.outgoing.inventory.InventoryUpdateItemComposer.class));
        verify(client, org.mockito.Mockito.never())
                .sendResponse(any(polaris.messages.outgoing.inventory.InventoryRefreshComposer.class));
        verify(threading).run(item);
    }

    private static void setField(Room room, String name, Object value) throws ReflectiveOperationException {
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
        void removeUserItem(int id) {
            this.removedItemId = id;
        }

        @Override
        public double getStackHeight(short x, short y, boolean calculateHeightmap) {
            return 0;
        }

        @Override
        public void updateTiles(java.util.Collection<RoomTile> tiles) {}

        @Override
        public void sendComposer(polaris.messages.ServerMessage message) {}
    }
}
