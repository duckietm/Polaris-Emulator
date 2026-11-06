package polaris.hotel.rooms;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.junit.jupiter.api.Test;
import polaris.hotel.items.FurnitureType;
import polaris.hotel.items.Item;
import polaris.hotel.items.ItemInteraction;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.outgoing.rooms.ConfigurationItemStatesComposer;

class RoomChooserDisableSupportTest {

    @Test
    void theChooserStaysOpenWithoutASwitchedOnDisabler() {
        Room room = room(Set.of(item("conf_chooser_disable", "0"), item("default", "1")));

        assertFalse(RoomChooserDisableSupport.isChooserDisabled(room));
        assertFalse(new ConfigurationItemStatesComposer(room, mock(User.class)).isChooserDisabled());
    }

    @Test
    void aSwitchedOnDisablerClosesTheChooserForEveryViewer() {
        Room room = room(Set.of(item("conf_chooser_disable", "1")));

        assertTrue(RoomChooserDisableSupport.isChooserDisabled(room));
        // The flag is the room's; the client lets rights holders through (AIR ChatInputWidgetHandler :chooser).
        assertTrue(new ConfigurationItemStatesComposer(room, mock(User.class)).isChooserDisabled());
    }

    @Test
    void theDisablerIsRecognisedByItsInteraction() {
        assertTrue(RoomChooserDisableSupport.isControllerItem(item("CONF_CHOOSER_DISABLE", "0")));
        assertFalse(RoomChooserDisableSupport.isControllerItem(item("wf_conf_handitem_block", "1")));
        assertFalse(RoomChooserDisableSupport.isControllerItem(null));
    }

    private static Room room(Set<UserItem> floorItems) {
        Room room = mock(Room.class);
        when(room.getFloorItems()).thenReturn(floorItems);
        return room;
    }

    private static UserItem item(String interactionName, String extradata) {
        UserItem item = mock(UserItem.class);
        Item baseItem = mock(Item.class);
        when(item.getBaseItem()).thenReturn(baseItem);
        when(item.getExtradata()).thenReturn(extradata);
        when(baseItem.getType()).thenReturn(FurnitureType.FLOOR);
        when(baseItem.getInteractionType()).thenReturn(new ItemInteraction(interactionName, UserItem.class));
        return item;
    }
}
