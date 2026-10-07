package com.eu.habbo.habbohotel.items.interactions.wired.selector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredServices;
import com.eu.habbo.habbohotel.wired.core.WiredState;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** A furni-on-furni selector without source furni selects nothing, and keeps what earlier selectors picked. */
class WiredEffectFurniOnFurniEmptySourceTest {

    @Test
    void earlierPicksStay() {
        HabboItem picked = mock(HabboItem.class);
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
