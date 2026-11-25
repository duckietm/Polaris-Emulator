package polaris.hotel.items.interactions.wired.selector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.rooms.RoomUnitType;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredEvent;
import polaris.hotel.wired.core.WiredServices;
import polaris.hotel.wired.core.WiredState;

/** Invert picks everybody the selector does not match; with the filter on, it takes them out of the earlier picks. */
class WiredSelectorInvertTest {

    private static RoomUnit holding(int handItem) {
        RoomUnit unit = mock(RoomUnit.class);
        when(unit.getHandItem()).thenReturn(handItem);
        when(unit.getRoomUnitType()).thenReturn(RoomUnitType.USER);
        return unit;
    }

    private static WiredSettings settings(int[] params, boolean filter, boolean invert) {
        WiredSettings settings = new WiredSettings(params, "", new int[0], 0);
        settings.setSelectorFilter(filter);
        settings.setSelectorInvert(invert);
        return settings;
    }

    private static WiredEffectUsersHandItem handItem(int handItem, boolean filter, boolean invert) {
        WiredEffectUsersHandItem box = new WiredEffectUsersHandItem(1, 1, mock(Item.class), "", 0, 0);
        box.saveData(settings(new int[] {handItem, 0, 0}, filter, invert), null);
        return box;
    }

    private static WiredContext context(Room room) {
        return new WiredContext(
                WiredEvent.builder(WiredEvent.Type.CUSTOM, room).build(),
                null,
                mock(WiredServices.class),
                new WiredState(20));
    }

    private static Room roomOf(RoomUnit... units) {
        Room room = mock(Room.class);
        when(room.getRoomUnits()).thenReturn(new LinkedHashSet<>(List.of(units)));
        return room;
    }

    @Test
    void invertSelectsEverybodyWhoDoesNotMatch() {
        RoomUnit cola = holding(5);
        RoomUnit coffee = holding(7);
        RoomUnit empty = holding(0);
        WiredContext ctx = context(roomOf(cola, coffee, empty));

        handItem(5, false, true).execute(ctx);

        assertEquals(Set.of(coffee, empty), ctx.targets().users());
    }

    @Test
    void invertWithNobodyMatchingSelectsTheWholeRoom() {
        RoomUnit coffee = holding(7);
        RoomUnit empty = holding(0);
        WiredContext ctx = context(roomOf(coffee, empty));

        handItem(5, false, true).execute(ctx);

        assertEquals(Set.of(coffee, empty), ctx.targets().users());
    }

    @Test
    void invertWithEverybodyMatchingSelectsNobody() {
        WiredContext ctx = context(roomOf(holding(5), holding(5)));

        handItem(5, false, true).execute(ctx);

        assertEquals(Set.of(), ctx.targets().users());
    }

    @Test
    void filterWithInvertTakesTheMatchesOutOfTheEarlierPicks() {
        RoomUnit cola = holding(5);
        RoomUnit coffee = holding(7);
        RoomUnit empty = holding(0);
        WiredContext ctx = context(roomOf(cola, coffee, empty));

        // First everybody with something in their hand, then drop the cola drinkers from that.
        handItem(0, false, true).execute(ctx);
        handItem(5, true, true).execute(ctx);

        assertEquals(Set.of(coffee), ctx.targets().users());
    }

    @Test
    void anInvertedSignalSelectorOutsideASignalSelectsTheWholeRoom() {
        RoomUnit one = holding(0);
        RoomUnit two = holding(0);
        WiredContext ctx = context(roomOf(one, two));
        WiredEffectUsersSignal box = new WiredEffectUsersSignal(1, 1, mock(Item.class), "", 0, 0);
        box.saveData(settings(new int[0], false, true), null);

        box.execute(ctx);

        assertEquals(Set.of(one, two), ctx.targets().users());
    }

    @Test
    void aSignalSelectorOutsideASignalKeepsTheEarlierPicks() {
        RoomUnit cola = holding(5);
        RoomUnit empty = holding(0);
        WiredContext ctx = context(roomOf(cola, empty));
        WiredEffectUsersSignal box = new WiredEffectUsersSignal(1, 1, mock(Item.class), "", 0, 0);
        box.saveData(settings(new int[0], false, false), null);

        handItem(5, false, false).execute(ctx);
        box.execute(ctx);

        assertEquals(Set.of(cola), ctx.targets().users());
    }
}
