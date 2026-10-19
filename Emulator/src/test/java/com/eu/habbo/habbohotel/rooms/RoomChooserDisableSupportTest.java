package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.ItemInteraction;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.rooms.ConfigurationItemStatesComposer;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RoomChooserDisableSupportTest {

    @Test
    void theChooserStaysOpenWithoutASwitchedOnDisabler() {
        Room room = room(Set.of(item("conf_chooser_disable", "0"), item("default", "1")));

        assertFalse(RoomChooserDisableSupport.isChooserDisabled(room));
        assertFalse(new ConfigurationItemStatesComposer(room, mock(Habbo.class)).isChooserDisabled());
    }

    @Test
    void aSwitchedOnDisablerClosesTheChooserForEveryViewer() {
        Room room = room(Set.of(item("conf_chooser_disable", "1")));

        assertTrue(RoomChooserDisableSupport.isChooserDisabled(room));
        // The flag is the room's; the client lets rights holders through (AIR ChatInputWidgetHandler :chooser).
        assertTrue(new ConfigurationItemStatesComposer(room, mock(Habbo.class)).isChooserDisabled());
    }

    @Test
    void theDisablerIsRecognisedByItsInteraction() {
        assertTrue(RoomChooserDisableSupport.isControllerItem(item("CONF_CHOOSER_DISABLE", "0")));
        assertFalse(RoomChooserDisableSupport.isControllerItem(item("wf_conf_handitem_block", "1")));
        assertFalse(RoomChooserDisableSupport.isControllerItem(null));
    }

    private static Room room(Set<HabboItem> floorItems) {
        Room room = mock(Room.class);
        when(room.getFloorItems()).thenReturn(floorItems);
        return room;
    }

    private static HabboItem item(String interactionName, String extradata) {
        HabboItem item = mock(HabboItem.class);
        Item baseItem = mock(Item.class);
        when(item.getBaseItem()).thenReturn(baseItem);
        when(item.getExtradata()).thenReturn(extradata);
        when(baseItem.getType()).thenReturn(FurnitureType.FLOOR);
        when(baseItem.getInteractionType()).thenReturn(new ItemInteraction(interactionName, HabboItem.class));
        return item;
    }
}
