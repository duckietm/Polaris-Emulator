package com.eu.habbo.habbohotel.items.interactions;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.List;
import java.util.Set;
import org.apache.commons.math3.util.Pair;
import org.junit.jupiter.api.Test;

class InteractionWaterJoiningTest {
    private static final Item SHALLOW = base(3530, "bw_water_1");
    private static final Item DEEP = base(3541, "bw_water_2");
    private static final Item VENETIAN = base(4863, "val13_water");
    private static final Item STACKABLE = base(5436, "stackable_water");

    private static Item base(int id, String name) {
        Item item = mock(Item.class);
        when(item.getId()).thenReturn(id);
        when(item.getName()).thenReturn(name);
        return item;
    }

    private static InteractionWater water(int id, Item base) {
        return new InteractionWater(id, 1, base, "0", 0, 0);
    }

    @Test
    void theSameKindOfWaterJoins() {
        assertTrue(water(1, VENETIAN).joins(water(2, VENETIAN), false));
        assertTrue(water(1, SHALLOW).joins(water(2, SHALLOW), false));
        assertTrue(water(1, DEEP).joins(water(2, DEEP), true));
    }

    @Test
    void differentKindsKeepTheirShore() {
        assertFalse(water(1, SHALLOW).joins(water(2, VENETIAN), false));
        assertFalse(water(1, SHALLOW).joins(water(2, VENETIAN), true));
        assertFalse(water(1, VENETIAN).joins(water(2, STACKABLE), true));
    }

    @Test
    void shallowBwWaterTakesDeepWaterOnlyAtItsCorners() {
        assertTrue(water(1, SHALLOW).joins(water(2, DEEP), true));
        assertFalse(water(1, SHALLOW).joins(water(2, DEEP), false));
        assertFalse(water(1, DEEP).joins(water(2, SHALLOW), true));
    }

    @Test
    void onlyStackableWaterGoesOnOtherFurni() {
        HabboItem table = mock(HabboItem.class);
        List<Pair<RoomTile, Set<HabboItem>>> onTable = List.of(new Pair<>(mock(RoomTile.class), Set.of(table)));
        List<Pair<RoomTile, Set<HabboItem>>> onWater =
                List.of(new Pair<>(mock(RoomTile.class), Set.of(water(9, SHALLOW))));
        Room room = mock(Room.class);

        assertTrue(water(1, STACKABLE).canStackAt(room, onTable));
        assertFalse(water(1, SHALLOW).canStackAt(room, onTable));
        assertTrue(water(1, SHALLOW).canStackAt(room, onWater));
    }
}
