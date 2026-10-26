package com.eu.habbo.habbohotel.quests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.messages.outgoing.quests.ActiveDailyTasksComposer.Reward;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * A daily task reward reaches the client as AIR's DailyTaskRewardView reads it: the product type (8 currency,
 * 4 badge, 1 floor, 0 wall, -1 unknown) and the item type (currency type, badge code or furni class id).
 */
class DailyTaskRewardWireTest {

    private static Item item(int spriteId, FurnitureType type) {
        Item item = mock(Item.class);
        when(item.getSpriteId()).thenReturn(spriteId);
        when(item.getType()).thenReturn(type);
        return item;
    }

    private static final Map<String, Item> ITEMS =
            Map.of("throne", item(230, FurnitureType.FLOOR), "poster", item(4001, FurnitureType.WALL));

    private static Reward wire(String type, String extra, int amount) {
        return QuestRewards.dailyTaskRewardWire(type, extra, amount, ITEMS::get);
    }

    @Test
    void currenciesAreSentAsTheirActivityPointType() {
        assertEquals(new Reward(8, "-1", "", 10), wire("credits", "", 10));
        assertEquals(new Reward(8, "0", "", 15), wire("Duckets", "", 15));
        assertEquals(new Reward(8, "5", "", 2), wire("diamonds", "", 2));
    }

    @Test
    void aBadgeSendsItsCodeAsTheItemType() {
        assertEquals(new Reward(4, "ACH_Daily1", "ACH_Daily1", 1), wire("badge", "ACH_Daily1", 1));
    }

    @Test
    void furniIsSentAsItsClassIdOnTheFloorOrWall() {
        assertEquals(new Reward(1, "230", "throne", 1), wire("furni", "throne", 1));
        assertEquals(new Reward(0, "4001", "poster", 3), wire("furni", "poster", 3));
    }

    @Test
    void aMissingFurniOrAnUnknownTypeIsTheUnknownProduct() {
        assertEquals(new Reward(-1, "furni", "", 1), wire("furni", "nothing", 1));
        assertEquals(new Reward(-1, "pixels", "", 4), wire("pixels", "", 4));
        assertEquals(new Reward(-1, "furni", "", 1), QuestRewards.dailyTaskRewardWire("furni", "throne", 1, null));
    }
}
