package com.eu.habbo.habbohotel.wired.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.ItemInteraction;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredInternalVariableSupport;
import java.util.LinkedHashSet;
import org.junit.jupiter.api.Test;

/** Wall furni picks travel as negative ids, as AIR picks them; boxes keep storing the wall items' ids. */
class WiredWallPicksTest {
    private static final int WALL = 42;
    private static final int FLOOR = 7;

    @Test
    void aNegativePickOnABoxThatTakesWallFurniBecomesTheWallItem() {
        assertArrayEquals(new int[] {FLOOR, WALL}, WiredWallPicks.fromWire(new int[] {FLOOR, -WALL}, room(), true));
    }

    @Test
    void aNegativePickNamingNoWallItemIsDropped() {
        assertArrayEquals(
                new int[0], WiredWallPicks.fromWire(new int[] {-FLOOR, -99, Integer.MIN_VALUE}, room(), true));
    }

    @Test
    void aBoxThatTakesNoWallFurniDropsNegativePicksButKeepsItsStoredWallPick() {
        assertArrayEquals(new int[] {WALL}, WiredWallPicks.fromWire(new int[] {WALL, -WALL}, room(), false));
        assertArrayEquals(new int[] {FLOOR}, WiredWallPicks.fromWire(new int[] {FLOOR, -WALL}, room(), false));
    }

    @Test
    void theSameWallItemInBothFormsIsPickedOnce() {
        assertArrayEquals(new int[] {WALL}, WiredWallPicks.fromWire(new int[] {WALL, -WALL}, room(), true));
    }

    @Test
    void wallFurniFollowsTheBoxConf() {
        assertTrue(WiredWallPicks.allowsWallFurni(box("wf_trg_state_changed")));
        assertTrue(WiredWallPicks.allowsWallFurni(box("wf_slc_furni_bytype")));
        assertFalse(WiredWallPicks.allowsWallFurni(box("wf_act_adjust_clock")));
        // No conf: the editor keeps accepting wall picks.
        assertTrue(WiredWallPicks.allowsWallFurni(box("wf_act_toggle_state")));
    }

    @Test
    void theSaveRulesResolveBothPickLists() {
        WiredSettings settings = new WiredSettings(new int[0], "", new int[] {-WALL, FLOOR}, -1);
        settings.setFurniIds2(new int[] {-WALL});
        Room room = room();

        assertNull(WiredSourceSaveRules.apply(box("wf_trg_state_changed"), settings, room));
        assertArrayEquals(new int[] {WALL, FLOOR}, settings.getFurniIds());
        assertArrayEquals(new int[] {WALL}, settings.getFurniIds2());
    }

    @Test
    void storedWallPicksGoOutNegativeOnlyForBoxesThatTakeWallFurni() {
        int[] stored = {FLOOR, WALL};

        assertArrayEquals(new int[] {FLOOR, -WALL}, WiredWallPicks.toWire(stored, box("wf_trg_state_changed"), room()));
        assertArrayEquals(new int[] {FLOOR, WALL}, WiredWallPicks.toWire(stored, box("wf_act_adjust_clock"), room()));
    }

    @Test
    void theIdVariableOfAWallItemIsNegative() {
        Room room = room();

        assertEquals(-WALL, WiredInternalVariableSupport.readFurniValue(room, room.getHabboItem(WALL), "@id"));
        assertEquals(FLOOR, WiredInternalVariableSupport.readFurniValue(room, room.getHabboItem(FLOOR), "@id"));
    }

    private static Room room() {
        Room room = mock(Room.class);
        RoomSpecialTypes types = mock(RoomSpecialTypes.class);
        when(room.getRoomSpecialTypes()).thenReturn(types);
        when(types.getTriggers(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(new LinkedHashSet<>());
        HabboItem wall = item(WALL, FurnitureType.WALL);
        HabboItem floor = item(FLOOR, FurnitureType.FLOOR);
        when(room.getHabboItem(WALL)).thenReturn(wall);
        when(room.getHabboItem(FLOOR)).thenReturn(floor);
        return room;
    }

    private static HabboItem item(int id, FurnitureType type) {
        HabboItem item = mock(HabboItem.class);
        Item base = mock(Item.class);
        when(item.getId()).thenReturn(id);
        when(item.getBaseItem()).thenReturn(base);
        when(base.getType()).thenReturn(type);
        return item;
    }

    private static HabboItem box(String interaction) {
        HabboItem box = mock(HabboItem.class);
        Item base = mock(Item.class);
        when(box.getBaseItem()).thenReturn(base);
        when(base.getInteractionType()).thenReturn(new ItemInteraction(interaction, HabboItem.class));
        return box;
    }
}
