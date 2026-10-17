package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.eu.habbo.habbohotel.items.ItemManager;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.wired.protocol.WiredSourceSaveRules;
import org.junit.jupiter.api.Test;

/** Place Furni copies take ids in the official temporary band, per room, from the bottom after an unload. */
class RoomTemporaryItemIdsTest {

    @Test
    void copiesTakeTheLowestUnusedIdAndStartAgainAfterAnUnload() {
        RoomWiredRuntime runtime = new RoomWiredRuntime(mock(Room.class));

        assertEquals(ItemManager.FIRST_TEMPORARY_ITEM_ID, runtime.nextTemporaryItemId());
        assertEquals(ItemManager.FIRST_TEMPORARY_ITEM_ID + 1, runtime.nextTemporaryItemId());

        runtime.dispose();

        assertEquals(ItemManager.FIRST_TEMPORARY_ITEM_ID, runtime.nextTemporaryItemId());
    }

    @Test
    void aFullBandPlacesNothing() {
        RoomWiredRuntime runtime = new RoomWiredRuntime(mock(Room.class));
        int band = ItemManager.LAST_TEMPORARY_ITEM_ID - ItemManager.FIRST_TEMPORARY_ITEM_ID + 1;
        for (int i = 0; i < band; i++) runtime.nextTemporaryItemId();

        assertEquals(-1, runtime.nextTemporaryItemId());
        assertEquals(-1, runtime.nextTemporaryItemId());
    }

    @Test
    void aBoxSaveThatPicksACopyIsRefused() {
        WiredSettings settings =
                new WiredSettings(new int[0], "", new int[] {15, ItemManager.FIRST_TEMPORARY_ITEM_ID + 3}, 0);

        assertEquals(
                "wiredfurni.error.temp_furni_not_allowed",
                WiredSourceSaveRules.apply(null, settings, mock(Room.class)));
        assertEquals(
                null, WiredSourceSaveRules.apply(null, new WiredSettings(new int[0], "", new int[] {15}, 0), null));
    }
}
