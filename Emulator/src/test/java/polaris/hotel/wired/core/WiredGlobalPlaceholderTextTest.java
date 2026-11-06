package polaris.hotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionWiredExtra;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.items.interactions.wired.extra.WiredExtraTextOutputGlobal;
import polaris.hotel.items.interactions.wired.extra.WiredExtraTextOutputUsername;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomSpecialTypes;
import polaris.hotel.users.UserItem;

class WiredGlobalPlaceholderTextTest {

    private static WiredExtraTextOutputGlobal global(int id, String name, String value) throws Exception {
        WiredExtraTextOutputGlobal box = new WiredExtraTextOutputGlobal(id, 1, mock(Item.class), "", 0, 0);
        box.saveData(new WiredSettings(new int[] {0}, name + "\t" + value, new int[0], 0), null);
        return box;
    }

    private static Room room(Set<InteractionWiredExtra> all, Set<InteractionWiredExtra> stack) {
        Room room = mock(Room.class);
        RoomSpecialTypes special = mock(RoomSpecialTypes.class);
        when(room.getRoomSpecialTypes()).thenReturn(special);
        when(special.getExtras()).thenReturn(all);
        when(special.getExtras(0, 0)).thenReturn(stack);
        return room;
    }

    private static WiredContext context(Room room, UserItem triggerItem) {
        return new WiredContext(
                WiredEvent.builder(WiredEvent.Type.CUSTOM, room).build(),
                triggerItem,
                mock(WiredServices.class),
                new WiredState(100));
    }

    @Test
    void aGlobalPlaceholderReachesEveryStackInTheRoom() throws Exception {
        WiredExtraTextOutputGlobal greeting = global(10, "greeting", "Welcome");
        Room room = room(new LinkedHashSet<>(List.of(greeting)), Set.of());

        assertEquals(
                "Welcome! Welcome!",
                WiredTextPlaceholderUtil.applyUsernamePlaceholders(context(room, null), "$(greeting)! $(greeting)!"));
        assertEquals(
                "Welcome",
                WiredTextPlaceholderUtil.applyUsernamePlaceholders(context(room, mock(UserItem.class)), "$(greeting)"));
    }

    @Test
    void textWithoutAPlaceholderIsLeftAlone() throws Exception {
        Room room = room(new LinkedHashSet<>(List.of(global(10, "greeting", "Welcome"))), Set.of());
        String text = "no  tokens here";

        assertSame(text, WiredTextPlaceholderUtil.applyUsernamePlaceholders(context(room, null), text));
    }

    @Test
    void theStacksOwnPlaceholderWinsOverAGlobalOfTheSameName() throws Exception {
        WiredExtraTextOutputUsername local = new WiredExtraTextOutputUsername(20, 1, mock(Item.class), "", 0, 0);
        local.saveData(
                new WiredSettings(new int[] {1, WiredSourceUtil.SOURCE_TRIGGER}, "who\t, ", new int[0], 0), null);
        WiredExtraTextOutputGlobal global = global(10, "who", "Everyone");
        Room room = room(new LinkedHashSet<>(List.of(global, local)), new LinkedHashSet<>(List.of(local)));

        assertEquals(
                "Hi !",
                WiredTextPlaceholderUtil.applyUsernamePlaceholders(context(room, mock(UserItem.class)), "Hi $(who)!"));
    }

    @Test
    void expansionStaysWithinTheOutputBudget() throws Exception {
        WiredExtraTextOutputGlobal big = global(10, "x", "y".repeat(WiredExtraTextOutputGlobal.MAX_VALUE_LENGTH));
        Room room = room(new LinkedHashSet<>(List.of(big)), Set.of());

        String result = WiredTextPlaceholderUtil.applyUsernamePlaceholders(context(room, null), "$(x)".repeat(1000));

        assertTrue(result.length() <= 16384);
    }

    @Test
    void onlyTheFirstGlobalsByIdAreConsidered() throws Exception {
        Set<InteractionWiredExtra> all = new LinkedHashSet<>();
        for (int id = 200; id > 0; id--) all.add(global(id, "p" + id, "v"));
        Room room = room(all, Set.of());

        List<WiredExtraTextOutputGlobal> globals = WiredTextPlaceholderUtil.collectGlobalPlaceholders(room);

        assertEquals(64, globals.size());
        assertEquals(1, globals.getFirst().getId());
        assertEquals("v", WiredTextPlaceholderUtil.applyUsernamePlaceholders(context(room, null), "$(p64)"));
        assertEquals("$(p65)", WiredTextPlaceholderUtil.applyUsernamePlaceholders(context(room, null), "$(p65)"));
    }
}
