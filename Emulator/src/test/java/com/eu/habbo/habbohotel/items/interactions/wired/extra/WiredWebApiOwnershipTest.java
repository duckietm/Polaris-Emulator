package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.catalog.CatalogItem;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.ItemInteraction;
import com.eu.habbo.habbohotel.items.ItemManager;
import com.eu.habbo.habbohotel.items.interactions.InteractionDefault;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import java.sql.ResultSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class WiredWebApiOwnershipTest {

    @Test
    void theBoxCanNeverBeTradedSoldGiftedOrRecycledWhateverItemsBaseSays() throws Exception {
        Item box = baseItem("wf_xtra_var_web_api", WiredExtraVariableWebApi.class);
        Item chair = baseItem("default", InteractionDefault.class);

        assertFalse(box.allowTrade());
        assertFalse(box.allowMarketplace());
        assertFalse(box.allowGift());
        assertFalse(box.allowRecyle());
        assertFalse(box.allowInventoryStack());
        assertTrue(WiredWebApiOwnership.isWebApiItem(box));

        assertTrue(chair.allowTrade());
        assertTrue(chair.allowMarketplace());
        assertTrue(chair.allowGift());
        assertTrue(chair.allowRecyle());
        assertFalse(WiredWebApiOwnership.isWebApiItem(chair));
    }

    @Test
    void theBoxStaysUntradeableWhenItsInteractionIsChangedInTheDatabase() throws Exception {
        Item renamed = baseItem("wf_xtra_var_web_api", "default", InteractionDefault.class);

        assertTrue(WiredWebApiOwnership.isWebApiItem(renamed));
        assertFalse(renamed.allowTrade());
        assertFalse(renamed.allowMarketplace());
        assertFalse(renamed.allowGift());
        assertFalse(renamed.allowRecyle());
    }

    @Test
    void aUserBuysAtMostOneBoxByDefault() throws Exception {
        Item box = baseItem("wf_xtra_var_web_api", WiredExtraVariableWebApi.class);
        Item chair = baseItem("default", InteractionDefault.class);

        assertNull(WiredWebApiOwnership.purchaseRefusal(offer(box, 1), 1, 3, user -> 0, 1));
        assertEquals(
                WiredWebApiOwnership.ONE_PER_USER_KEY,
                WiredWebApiOwnership.purchaseRefusal(offer(box, 1), 1, 3, user -> 1, 1));
        assertEquals(
                WiredWebApiOwnership.ONE_PER_USER_KEY,
                WiredWebApiOwnership.purchaseRefusal(offer(box, 1), 2, 3, user -> 0, 1));
        assertEquals(
                WiredWebApiOwnership.ONE_PER_USER_KEY,
                WiredWebApiOwnership.purchaseRefusal(offer(box, 2), 1, 3, user -> 0, 1));
        assertNull(WiredWebApiOwnership.purchaseRefusal(offer(chair, 5), 5, 3, user -> 1, 1));
    }

    @Test
    void theHotelCanAllowMoreBoxesPerUser() throws Exception {
        Item box = baseItem("wf_xtra_var_web_api", WiredExtraVariableWebApi.class);

        assertNull(WiredWebApiOwnership.purchaseRefusal(offer(box, 1), 1, 3, user -> 4, 5));
        assertNull(WiredWebApiOwnership.purchaseRefusal(offer(box, 1), 3, 3, user -> 2, 5));
        assertEquals(
                WiredWebApiOwnership.MAX_PER_USER_KEY,
                WiredWebApiOwnership.purchaseRefusal(offer(box, 1), 1, 3, user -> 5, 5));
        assertEquals(
                WiredWebApiOwnership.MAX_PER_USER_KEY,
                WiredWebApiOwnership.purchaseRefusal(offer(box, 1), 4, 3, user -> 2, 5));
        assertEquals(
                WiredWebApiOwnership.MAX_PER_USER_KEY,
                WiredWebApiOwnership.purchaseRefusal(offer(box, 1), 1, 3, user -> Integer.MAX_VALUE, 5));
    }

    @Test
    void theBoxOnlyGoesIntoItsOwnersRoomAndOnlyOnce() {
        WiredExtraVariableWebApi placed = new WiredExtraVariableWebApi(1, 3, mock(Item.class), "", 0, 0);
        WiredExtraVariableWebApi mine = new WiredExtraVariableWebApi(2, 3, mock(Item.class), "", 0, 0);
        WiredExtraVariableWebApi someoneElses = new WiredExtraVariableWebApi(3, 4, mock(Item.class), "", 0, 0);
        Room empty = roomWith();
        Room full = roomWith(placed);
        when(empty.getOwnerId()).thenReturn(3);
        when(full.getOwnerId()).thenReturn(3);

        assertNull(WiredWebApiOwnership.placementRefusal(empty, mine));
        assertEquals(WiredWebApiOwnership.ONE_PER_ROOM_KEY, WiredWebApiOwnership.placementRefusal(full, mine));
        assertEquals(
                WiredWebApiOwnership.OWN_ROOM_ONLY_KEY, WiredWebApiOwnership.placementRefusal(empty, someoneElses));
        assertNull(
                WiredWebApiOwnership.placementRefusal(empty, new InteractionDefault(9, 4, mock(Item.class), "", 0, 0)));
    }

    @Test
    void aRoomWithTwoBoxesHasNoUsableBox() {
        WiredExtraVariableWebApi first = new WiredExtraVariableWebApi(1, 3, mock(Item.class), "", 0, 0);
        WiredExtraVariableWebApi second = new WiredExtraVariableWebApi(2, 3, mock(Item.class), "", 0, 0);
        WiredExtraVariableWebApi third = new WiredExtraVariableWebApi(4, 3, mock(Item.class), "", 0, 0);
        Room conflicted = roomWith(first, second);
        when(conflicted.getOwnerId()).thenReturn(3);

        assertSame(first, WiredWebApiOwnership.boxIn(roomWith(first)));
        assertNull(WiredWebApiOwnership.boxIn(conflicted));
        assertEquals(WiredWebApiOwnership.ONE_PER_ROOM_KEY, WiredWebApiOwnership.placementRefusal(conflicted, third));
    }

    @Test
    void aRoomHoldsAtMostOneBox() {
        WiredExtraVariableWebApi placed = new WiredExtraVariableWebApi(1, 3, mock(Item.class), "", 0, 0);
        WiredExtraVariableWebApi second = new WiredExtraVariableWebApi(2, 3, mock(Item.class), "", 0, 0);
        Room empty = roomWith();
        Room full = roomWith(placed);

        assertFalse(WiredWebApiOwnership.wouldExceedRoomLimit(empty, second));
        assertTrue(WiredWebApiOwnership.wouldExceedRoomLimit(full, second));
        assertFalse(WiredWebApiOwnership.wouldExceedRoomLimit(full, placed));
        assertTrue(placed == WiredWebApiOwnership.boxIn(full));
        assertNull(WiredWebApiOwnership.boxIn(empty));
    }

    private static Room roomWith(InteractionWiredExtra... extras) {
        RoomSpecialTypes types = mock(RoomSpecialTypes.class);
        when(types.getExtras()).thenReturn(new LinkedHashSet<>(Set.of(extras)));
        Room room = mock(Room.class);
        when(room.getRoomSpecialTypes()).thenReturn(types);
        return room;
    }

    private static CatalogItem offer(Item item, int amount) {
        CatalogItem offer = mock(CatalogItem.class);
        when(offer.getBaseItems()).thenReturn(Set.of(item));
        when(offer.getItemAmount(item.getId())).thenReturn(amount);
        return offer;
    }

    private static Item baseItem(String interaction, Class<? extends com.eu.habbo.habbohotel.users.HabboItem> type)
            throws Exception {
        return baseItem(interaction, interaction, type);
    }

    private static Item baseItem(
            String itemName, String interaction, Class<? extends com.eu.habbo.habbohotel.users.HabboItem> type)
            throws Exception {
        ResultSet set = mock(ResultSet.class);
        when(set.getInt("id")).thenReturn(itemName.hashCode() & 0xFFFF);
        when(set.getString("type")).thenReturn("s");
        when(set.getString("item_name")).thenReturn(itemName);
        when(set.getString("public_name")).thenReturn(itemName);
        when(set.getString("interaction_type")).thenReturn(interaction);
        for (String flag : Map.of(
                        "allow_trade",
                        1,
                        "allow_marketplace_sell",
                        1,
                        "allow_gift",
                        1,
                        "allow_recycle",
                        1,
                        "allow_inventory_stack",
                        1)
                .keySet()) {
            when(set.getBoolean(flag)).thenReturn(true);
        }
        when(set.getString("vending_ids")).thenReturn("");
        when(set.getString("multiheight")).thenReturn("");
        when(set.getString("customparams")).thenReturn("");

        ItemManager itemManager = mock(ItemManager.class);
        when(itemManager.getItemInteraction(anyString())).thenReturn(new ItemInteraction(interaction, type));
        GameEnvironment environment = mock(GameEnvironment.class);
        when(environment.getItemManager()).thenReturn(itemManager);
        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            return new Item(set);
        }
    }
}
