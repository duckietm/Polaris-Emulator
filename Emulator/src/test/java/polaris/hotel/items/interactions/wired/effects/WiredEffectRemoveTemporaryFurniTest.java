package polaris.hotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomItemManager;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.WiredEffectType;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredSourceUtil;
import polaris.hotel.wired.protocol.WiredActionCodes;

/** The official "remove furni" takes away temporary furni only; ours picks up into inventories. */
class WiredEffectRemoveTemporaryFurniTest {

    @Test
    void itRemovesTemporaryFurniAndLeavesOwnedFurni() throws Exception {
        Room room = mock(Room.class);
        UserItem copy = item(2_147_401_728, true);
        UserItem secondCopy = item(2_147_401_729, true);
        UserItem owned = item(15, false);
        when(room.getUserItem(anyInt())).thenReturn(null);
        when(room.getUserItem(copy.getId())).thenReturn(copy);
        when(room.getUserItem(owned.getId())).thenReturn(owned);
        when(room.getUserItem(secondCopy.getId())).thenReturn(secondCopy);
        RoomItemManager items = mock(RoomItemManager.class);
        when(room.getItemManager()).thenReturn(items);
        WiredContext ctx = mock(WiredContext.class);
        when(ctx.room()).thenReturn(room);

        WiredEffectRemoveTemporaryFurni box = new WiredEffectRemoveTemporaryFurni(1, 1, mock(Item.class), "", 0, 0);
        WiredSettings settings = new WiredSettings(new int[0], "", new int[0], -1);
        settings.setFurniSourceTypes(new int[] {WiredSourceUtil.SOURCE_SELECTOR});
        box.saveData(settings, null);
        assertArrayEquals(new int[] {WiredSourceUtil.SOURCE_SELECTOR}, box.officialFurniSources());

        try (MockedStatic<WiredSourceUtil> sources = mockStatic(WiredSourceUtil.class)) {
            sources.when(() -> WiredSourceUtil.resolveItems(eq(ctx), eq(WiredSourceUtil.SOURCE_SELECTOR), any()))
                    .thenReturn(List.of(copy, owned, secondCopy));
            box.execute(ctx);
        }

        // One batched removal of the copies (ObjectRemoveMultiple); owned furni stays.
        verify(items).removeTemporaryItems(List.of(copy, secondCopy));
        verify(room, never()).pickUpItem(any(), any());
    }

    @Test
    void theOfficialBoxIs56AndOurPickupBoxIsCustom() {
        assertEquals(56, WiredActionCodes.official(WiredEffectType.REMOVE_FURNI.code));
        assertEquals(1156, WiredActionCodes.official(WiredEffectType.PICKUP_FURNI.code));
        assertEquals(WiredEffectType.PICKUP_FURNI, WiredEffectRemoveFurni.type);
    }

    private static UserItem item(int id, boolean temporary) {
        UserItem item = mock(UserItem.class);
        when(item.getId()).thenReturn(id);
        when(item.isTemporary()).thenReturn(temporary);
        return item;
    }
}
