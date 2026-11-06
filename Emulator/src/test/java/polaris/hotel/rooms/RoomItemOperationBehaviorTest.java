package polaris.hotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import polaris.hotel.items.FurnitureType;
import polaris.hotel.items.Item;
import polaris.hotel.items.ItemInteraction;
import polaris.hotel.users.UserItem;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.Outgoing;

class RoomItemOperationBehaviorTest {

    @Test
    void roomItemUpdatePublishesFloorAndAreaHideState() throws Exception {
        RecordingRoom room = roomWithLayout();
        UserItem controller = item("wf_conf_area_hide", null);

        room.updateItem(controller);

        assertEquals(List.of(Outgoing.FloorItemUpdateComposer, Outgoing.AreaHideComposer), room.messageHeaders());
    }

    @Test
    void managerItemUpdateMatchesTheCanonicalAreaHideBehavior() throws Exception {
        RecordingRoom room = roomWithLayout();
        UserItem controller = item("wf_conf_area_hide", null);

        room.getItemManager().updateItem(controller);

        assertEquals(List.of(Outgoing.FloorItemUpdateComposer, Outgoing.AreaHideComposer), room.messageHeaders());
    }

    @Test
    void roomItemStateUpdatePublishesInvisibilityState() throws Exception {
        RecordingRoom room = roomWithLayout();
        UserItem controller = item("wf_conf_invis_control", null);
        when(controller.getExtradata()).thenReturn("1");
        UserItem target = item("default", "is_invisible");
        room.floorItems = Set.of(controller, target);

        room.updateItemState(target);

        assertEquals(List.of(Outgoing.ItemStateComposer, Outgoing.ConfInvisStateComposer), room.messageHeaders());
    }

    @Test
    void managerItemStateUpdateMatchesCanonicalInvisibilityBehavior() throws Exception {
        RecordingRoom room = roomWithLayout();
        UserItem controller = item("wf_conf_invis_control", null);
        when(controller.getExtradata()).thenReturn("1");
        UserItem target = item("default", "is_invisible");
        room.floorItems = Set.of(controller, target);

        room.getItemManager().updateItemState(target);

        assertEquals(List.of(Outgoing.ItemStateComposer, Outgoing.ConfInvisStateComposer), room.messageHeaders());
    }

    @Test
    void roomItemStateUpdatePublishesHanditemBlockState() throws Exception {
        RecordingRoom room = roomWithLayout();
        UserItem controller = item("wf_conf_handitem_block", null);
        when(controller.getExtradata()).thenReturn("1");
        room.floorItems = Set.of(controller);

        room.updateItemState(controller);

        assertEquals(List.of(Outgoing.ItemStateComposer, Outgoing.HanditemBlockStateComposer), room.messageHeaders());
    }

    @Test
    void managerItemStateUpdateMatchesCanonicalHanditemBehavior() throws Exception {
        RecordingRoom room = roomWithLayout();
        UserItem controller = item("wf_conf_handitem_block", null);
        when(controller.getExtradata()).thenReturn("1");
        room.floorItems = Set.of(controller);

        room.getItemManager().updateItemState(controller);

        assertEquals(List.of(Outgoing.ItemStateComposer, Outgoing.HanditemBlockStateComposer), room.messageHeaders());
    }

    @Test
    void wiredFiringSendsItsFurniStatesTogetherOnceItCloses() throws Exception {
        RecordingRoom room = roomWithLayout();
        UserItem furni = item("default", null);
        room.floorItems = Set.of(furni);

        WiredFurniStateBatch.open();
        try {
            WiredFurniStateBatch.open();
            room.updateItemState(furni);
            WiredFurniStateBatch.close();
            room.updateItemState(furni);
            assertEquals(List.of(), room.messageHeaders());
        } finally {
            WiredFurniStateBatch.close();
        }

        assertEquals(List.of(Outgoing.ItemsDataUpdateComposer), room.messageHeaders());
        assertFalse(WiredFurniStateBatch.isOpen());

        room.updateItemState(furni);
        assertEquals(List.of(Outgoing.ItemsDataUpdateComposer, Outgoing.ItemStateComposer), room.messageHeaders());
    }

    private static RecordingRoom roomWithLayout() throws Exception {
        RecordingRoom room = new RecordingRoom();
        RoomLayout layout = mock(RoomLayout.class);
        when(layout.getTilesAt(nullable(RoomTile.class), anyInt(), anyInt(), anyInt()))
                .thenReturn(Set.of());
        setField(room, "layout", layout);
        return room;
    }

    private static UserItem item(String interactionName, String customParams) {
        UserItem item = mock(UserItem.class);
        Item baseItem = mock(Item.class);
        when(item.getId()).thenReturn(1001);
        when(item.getRoomId()).thenReturn(41);
        when(item.getBaseItem()).thenReturn(baseItem);
        when(item.getExtradata()).thenReturn("0");
        when(baseItem.getType()).thenReturn(FurnitureType.FLOOR);
        when(baseItem.getWidth()).thenReturn(1);
        when(baseItem.getLength()).thenReturn(1);
        when(baseItem.getInteractionType()).thenReturn(new ItemInteraction(interactionName, UserItem.class));
        when(baseItem.getCustomParams()).thenReturn(customParams);
        return item;
    }

    private static void setField(Room room, String name, Object value) throws ReflectiveOperationException {
        Field field = Room.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(room, value);
    }

    private static final class RecordingRoom extends Room {
        private final List<ServerMessage> messages = new ArrayList<>();
        private Set<UserItem> floorItems = Set.of();

        private RecordingRoom() {
            super(41, 7);
        }

        @Override
        public boolean isLoaded() {
            return true;
        }

        @Override
        public Set<UserItem> getFloorItems() {
            return this.floorItems;
        }

        @Override
        public void updateTiles(Collection<RoomTile> tiles) {}

        @Override
        public void sendComposer(ServerMessage message) {
            this.messages.add(message);
        }

        private List<Integer> messageHeaders() {
            return this.messages.stream().map(ServerMessage::getHeader).toList();
        }
    }
}
