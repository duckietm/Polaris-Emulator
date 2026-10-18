package com.eu.habbo.habbohotel.items.interactions.wired.selector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredServices;
import com.eu.habbo.habbohotel.wired.core.WiredState;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The furni picks selector keeps a picked Wired box: Call Stacks fed by picks runs the picked stack. */
class WiredEffectFurniPicksWiredBoxTest {

    @Test
    void aPickedWiredBoxIsSelected() {
        Room room = mock(Room.class);
        HabboItem trigger = mock(InteractionWiredTrigger.class);
        HabboItem chair = mock(HabboItem.class);
        when(room.getHabboItem(10)).thenReturn(trigger);
        when(room.getHabboItem(11)).thenReturn(chair);
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
