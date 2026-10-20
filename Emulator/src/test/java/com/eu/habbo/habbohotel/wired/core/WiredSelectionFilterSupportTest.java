package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraFilterFurni;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraFilterUser;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The furni and user filter add-ons default to an amount of 0. A filter that has been placed but
 * never configured must leave the selection alone: 0 means "no limit", not "nothing".
 */
class WiredSelectionFilterSupportTest {

    @Test
    void anUnconfiguredFurniFilterLeavesTheSelectionAlone() throws Exception {
        WiredExtraFilterFurni filter = new WiredExtraFilterFurni(9, 1, base(), "", 0, 0);
        Room room = roomWithExtras(filter);
        List<HabboItem> items = List.of(mock(HabboItem.class), mock(HabboItem.class));

        List<HabboItem> filtered = WiredSelectionFilterSupport.filterItems(room, trigger(), context(room), items);

        assertEquals(items, filtered);
    }

    @Test
    void anUnconfiguredUserFilterLeavesTheSelectionAlone() throws Exception {
        WiredExtraFilterUser filter = new WiredExtraFilterUser(9, 1, base(), "", 0, 0);
        Room room = roomWithExtras(filter);
        List<RoomUnit> users = List.of(mock(RoomUnit.class), mock(RoomUnit.class));

        List<RoomUnit> filtered = WiredSelectionFilterSupport.filterUsers(room, trigger(), context(room), users);

        assertEquals(users, filtered);
    }

    @Test
    void aConfiguredFurniFilterStillLimitsTheSelection() throws Exception {
        WiredExtraFilterFurni filter = new WiredExtraFilterFurni(9, 1, base(), "", 0, 0);
        filter.saveData(new WiredSettings(new int[] {1}, "", new int[0], 0), null);
        Room room = roomWithExtras(filter);
        List<HabboItem> items = List.of(mock(HabboItem.class), mock(HabboItem.class));

        List<HabboItem> filtered = WiredSelectionFilterSupport.filterItems(room, trigger(), context(room), items);

        assertEquals(1, filtered.size());
    }

    @Test
    void withoutASelectorOfItsKindAFilterStartsFromNothing() throws Exception {
        WiredExtraFilterUser filter = new WiredExtraFilterUser(9, 1, base(), "", 0, 0);
        filter.saveData(new WiredSettings(new int[] {2}, "", new int[0], 0), null);
        Room room = roomWithExtras(filter);
        HabboItem trigger = trigger();
        WiredContext ctx = new WiredContext(
                WiredEvent.builder(WiredEvent.Type.CUSTOM, room)
                        .actor(mock(RoomUnit.class))
                        .build(),
                trigger,
                mock(WiredServices.class),
                new WiredState(20));
        ctx.targets().addItem(mock(HabboItem.class));

        WiredSelectionFilterSupport.applySelectorFilters(room, trigger, ctx);

        // The users kind had a filter and no selector: it is emptied, not left on the triggering user.
        assertTrue(ctx.targets().isUsersModifiedBySelector());
        assertTrue(ctx.targets().users().isEmpty());
        // No furni filter: the furni defaults stay as they were.
        assertFalse(ctx.targets().isItemsModifiedBySelector());
        assertEquals(2, ctx.targets().items().size());
    }

    @Test
    void anUnconfiguredFilterWithoutASelectorChangesNothing() throws Exception {
        WiredExtraFilterUser filter = new WiredExtraFilterUser(9, 1, base(), "", 0, 0);
        Room room = roomWithExtras(filter);
        HabboItem trigger = trigger();
        WiredContext ctx = new WiredContext(
                WiredEvent.builder(WiredEvent.Type.CUSTOM, room)
                        .actor(mock(RoomUnit.class))
                        .build(),
                trigger,
                mock(WiredServices.class),
                new WiredState(20));

        WiredSelectionFilterSupport.applySelectorFilters(room, trigger, ctx);

        assertFalse(ctx.targets().isUsersModifiedBySelector());
        assertEquals(1, ctx.targets().users().size());
    }

    @Test
    void aFilterNeverNarrowsAnotherSourceThanTheSelectors() throws Exception {
        WiredExtraFilterUser filter = new WiredExtraFilterUser(9, 1, base(), "", 0, 0);
        filter.saveData(new WiredSettings(new int[] {1}, "", new int[0], 0), null);
        Room room = roomWithExtras(filter);
        WiredContext ctx = new WiredContext(
                WiredEvent.builder(WiredEvent.Type.CUSTOM, room).build(),
                trigger(),
                mock(WiredServices.class),
                new WiredState(20));
        List<RoomUnit> picked = List.of(mock(RoomUnit.class), mock(RoomUnit.class));

        assertEquals(picked, WiredSourceUtil.resolveUsers(ctx, WiredSourceUtil.SOURCE_SELECTED, picked));
    }

    private static WiredContext context(Room room) {
        return new WiredContext(
                WiredEvent.builder(WiredEvent.Type.CUSTOM, room).build(),
                null,
                mock(WiredServices.class),
                new WiredState(20));
    }

    private static Room roomWithExtras(InteractionWiredExtra extra) {
        Room room = mock(Room.class);
        RoomSpecialTypes specialTypes = mock(RoomSpecialTypes.class);
        when(room.getRoomSpecialTypes()).thenReturn(specialTypes);
        when(specialTypes.getExtras(3, 4)).thenReturn(Set.of(extra));
        return room;
    }

    private static HabboItem trigger() {
        HabboItem trigger = mock(HabboItem.class);
        when(trigger.getX()).thenReturn((short) 3);
        when(trigger.getY()).thenReturn((short) 4);
        return trigger;
    }

    private static Item base() {
        Item base = mock(Item.class);
        when(base.getSpriteId()).thenReturn(4321);
        return base;
    }
}
