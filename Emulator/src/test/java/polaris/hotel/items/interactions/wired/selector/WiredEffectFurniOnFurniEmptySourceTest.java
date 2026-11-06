package polaris.hotel.items.interactions.wired.selector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.junit.jupiter.api.Test;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomLayout;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredEvent;
import polaris.hotel.wired.core.WiredServices;
import polaris.hotel.wired.core.WiredState;

/** A furni-on-furni selector without source furni selects nothing, and keeps what earlier selectors picked. */
class WiredEffectFurniOnFurniEmptySourceTest {

    @Test
    void earlierPicksStay() {
        UserItem picked = mock(UserItem.class);
        Room room = mock(Room.class);
        when(room.getLayout()).thenReturn(mock(RoomLayout.class));
        when(room.getFloorItems()).thenReturn(Set.of(picked));
        WiredContext ctx = new WiredContext(
                WiredEvent.builder(WiredEvent.Type.CUSTOM, room).build(),
                null,
                mock(WiredServices.class),
                new WiredState(20));
        ctx.targets().setItems(Set.of(picked));

        new WiredEffectFurniOnFurni(1, 1, mock(Item.class), "", 0, 0).execute(ctx);

        assertEquals(Set.of(picked), ctx.targets().items());
    }
}
