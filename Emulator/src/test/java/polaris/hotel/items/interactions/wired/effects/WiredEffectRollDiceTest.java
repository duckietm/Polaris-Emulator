package polaris.hotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import polaris.hotel.items.FurnitureType;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionDice;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredEvent;
import polaris.hotel.wired.core.WiredServices;
import polaris.hotel.wired.core.WiredSourceUtil;
import polaris.hotel.wired.core.WiredState;

/** "Roll dice": every selected dice rolls, as if a user next to it had clicked; anything else is left alone. */
class WiredEffectRollDiceTest {

    @Test
    void rollsTheSelectedDiceAndNothingElse() throws Exception {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(1);
        UserItem dice = dice(room, 301);
        UserItem otherDice = dice(room, 302);
        UserItem lamp = furni(room, 303);
        List<Integer> rolled = new ArrayList<>();

        WiredEffectRollDice box = new WiredEffectRollDice(1, 1, base(), "", 0, 0);
        box.roller((item, r) -> rolled.add(item.getId()));
        box.saveData(
                new WiredSettings(new int[] {WiredSourceUtil.SOURCE_SELECTED}, "", new int[] {301, 302, 303}, 0), null);

        box.execute(context(room));

        assertEquals(List.of(301, 302), rolled);
    }

    private static WiredContext context(Room room) {
        return new WiredContext(
                WiredEvent.builder(WiredEvent.Type.CUSTOM, room).build(),
                null,
                mock(WiredServices.class),
                new WiredState(20));
    }

    private static Item base() {
        Item base = mock(Item.class);
        when(base.getType()).thenReturn(FurnitureType.FLOOR);
        when(base.getSpriteId()).thenReturn(4321);
        return base;
    }

    private static UserItem dice(Room room, int id) {
        InteractionDice item = mock(InteractionDice.class);
        Item base = base();
        when(item.getId()).thenReturn(id);
        when(item.getBaseItem()).thenReturn(base);
        when(room.getUserItem(id)).thenReturn(item);
        return item;
    }

    private static UserItem furni(Room room, int id) {
        UserItem item = mock(UserItem.class);
        Item base = base();
        when(item.getId()).thenReturn(id);
        when(item.getBaseItem()).thenReturn(base);
        when(room.getUserItem(id)).thenReturn(item);
        return item;
    }
}
