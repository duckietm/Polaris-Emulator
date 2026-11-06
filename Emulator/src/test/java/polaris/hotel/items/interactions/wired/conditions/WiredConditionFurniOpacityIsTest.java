package polaris.hotel.items.interactions.wired.conditions;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static polaris.hotel.items.interactions.wired.conditions.WiredConditionTestSupport.boxBase;
import static polaris.hotel.items.interactions.wired.conditions.WiredConditionTestSupport.context;
import static polaris.hotel.items.interactions.wired.conditions.WiredConditionTestSupport.room;
import static polaris.hotel.items.interactions.wired.conditions.WiredConditionTestSupport.settings;
import static polaris.hotel.items.interactions.wired.conditions.WiredConditionTestSupport.user;

import java.util.List;
import org.junit.jupiter.api.Test;
import polaris.hotel.items.FurnitureType;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.wired.WiredComparison;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomWiredRuntime;
import polaris.hotel.rooms.WiredOpacityState;
import polaris.hotel.users.UserItem;

/** "Furni opacity is": the selected furni's opacity, as the room currently shows it, compared to a value. */
class WiredConditionFurniOpacityIsTest {

    @Test
    void passesWhenEverySelectedFurniComparesAsAsked() {
        Room room = room(1);
        UserItem lamp = item(room, 501);
        UserItem rug = item(room, 502);
        opacity(
                room,
                List.of(new WiredOpacityState(501, false, 40, false), new WiredOpacityState(502, false, 60, false)));
        WiredConditionFurniOpacityIs box = new WiredConditionFurniOpacityIs(1, 1, boxBase(), "", 0, 0);

        box.saveData(settings(new int[] {30, WiredComparison.GREATER}, 501, 502));
        assertTrue(box.evaluate(context(room, user(room, 10, 1))));

        box.saveData(settings(new int[] {50, WiredComparison.GREATER}, 501, 502));
        assertFalse(box.evaluate(context(room, user(room, 10, 1))));

        box.saveData(settings(new int[] {40, WiredComparison.EQUAL}, 501));
        assertTrue(box.evaluate(context(room, user(room, 10, 1))));
    }

    @Test
    void theNegativeBoxPassesWhenNoneCompareAsAsked() {
        Room room = room(1);
        item(room, 501);
        opacity(room, List.of(new WiredOpacityState(501, false, 100, false)));
        WiredConditionNotFurniOpacityIs box = new WiredConditionNotFurniOpacityIs(1, 1, boxBase(), "", 0, 0);

        box.saveData(settings(new int[] {100, WiredComparison.EQUAL}, 501));
        assertFalse(box.evaluate(context(room, null)));

        box.saveData(settings(new int[] {50, WiredComparison.LESS}, 501));
        assertTrue(box.evaluate(context(room, null)));
    }

    @Test
    void withNoFurniToLookAtItFails() {
        Room room = room(1);
        opacity(room, List.of());
        WiredConditionFurniOpacityIs box = new WiredConditionFurniOpacityIs(1, 1, boxBase(), "", 0, 0);
        box.saveData(settings(new int[] {50, WiredComparison.LESS}));

        assertFalse(box.evaluate(context(room, null)));
    }

    private static UserItem item(Room room, int id) {
        UserItem item = mock(UserItem.class);
        Item base = mock(Item.class);
        when(item.getId()).thenReturn(id);
        when(item.getBaseItem()).thenReturn(base);
        when(base.getType()).thenReturn(FurnitureType.FLOOR);
        when(room.getUserItem(id)).thenReturn(item);
        return item;
    }

    private static void opacity(Room room, List<WiredOpacityState> states) {
        RoomWiredRuntime runtime = mock(RoomWiredRuntime.class);
        when(room.getWiredRuntime()).thenReturn(runtime);
        when(runtime.effectiveOpacity(anyInt(), any())).thenReturn(states);
    }
}
