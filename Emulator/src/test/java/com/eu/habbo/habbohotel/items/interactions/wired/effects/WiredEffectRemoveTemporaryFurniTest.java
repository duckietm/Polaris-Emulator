package com.eu.habbo.habbohotel.items.interactions.wired.effects;

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

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomItemManager;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.protocol.WiredActionCodes;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/** The official "remove furni" takes away temporary furni only; ours picks up into inventories. */
class WiredEffectRemoveTemporaryFurniTest {

    @Test
    void itRemovesTemporaryFurniAndLeavesOwnedFurni() throws Exception {
        Room room = mock(Room.class);
        HabboItem copy = item(2_147_401_728, true);
        HabboItem secondCopy = item(2_147_401_729, true);
        HabboItem owned = item(15, false);
        when(room.getHabboItem(anyInt())).thenReturn(null);
        when(room.getHabboItem(copy.getId())).thenReturn(copy);
        when(room.getHabboItem(owned.getId())).thenReturn(owned);
        when(room.getHabboItem(secondCopy.getId())).thenReturn(secondCopy);
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

    private static HabboItem item(int id, boolean temporary) {
        HabboItem item = mock(HabboItem.class);
        when(item.getId()).thenReturn(id);
        when(item.isTemporary()).thenReturn(temporary);
        return item;
    }
}
