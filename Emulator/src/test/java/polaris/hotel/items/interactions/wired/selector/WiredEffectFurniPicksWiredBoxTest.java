package polaris.hotel.items.interactions.wired.selector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.junit.jupiter.api.Test;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionWiredTrigger;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredEvent;
import polaris.hotel.wired.core.WiredServices;
import polaris.hotel.wired.core.WiredState;

/** The furni picks selector keeps a picked Wired box: Call Stacks fed by picks runs the picked stack. */
class WiredEffectFurniPicksWiredBoxTest {

    @Test
    void aPickedWiredBoxIsSelected() {
        Room room = mock(Room.class);
        UserItem trigger = mock(InteractionWiredTrigger.class);
        UserItem chair = mock(UserItem.class);
        when(room.getUserItem(10)).thenReturn(trigger);
        when(room.getUserItem(11)).thenReturn(chair);
        WiredEffectFurniPicks picks = new WiredEffectFurniPicks(1, 1, mock(Item.class), "", 0, 0);
        picks.saveData(new WiredSettings(new int[0], "", new int[] {10, 11}, 0), null);
        WiredContext ctx = new WiredContext(
                WiredEvent.builder(WiredEvent.Type.CUSTOM, room).build(),
                null,
                mock(WiredServices.class),
                new WiredState(20));

        picks.execute(ctx);

        assertEquals(Set.of(trigger, chair), ctx.targets().items());
    }
}
