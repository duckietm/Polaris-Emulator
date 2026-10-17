package com.eu.habbo.habbohotel.items.interactions.wired.selector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredServices;
import com.eu.habbo.habbohotel.wired.core.WiredState;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The remote selector runs whole picked stacks: any box of a stack stands for it, a stack without selector is empty. */
class WiredEffectRemoteSelectorStacksTest {
    private final Room room = mock(Room.class);
    private final HabboItem chair = mock(HabboItem.class);
    private final HabboItem triggerA = trigger(10, 1, 1);
    private final HabboItem triggerB = trigger(20, 2, 2);

    WiredEffectRemoteSelectorStacksTest() {
        WiredEffectFurniPicks picksChair = new WiredEffectFurniPicks(11, 1, mock(Item.class), "", 0, 0);
        picksChair.setX((short) 1);
        picksChair.setY((short) 1);
        picksChair.saveData(new WiredSettings(new int[0], "", new int[] {30}, 0), null);
        when(this.chair.getId()).thenReturn(30);
        when(this.room.getHabboItem(10)).thenReturn(this.triggerA);
        when(this.room.getHabboItem(11)).thenReturn(picksChair);
        when(this.room.getHabboItem(20)).thenReturn(this.triggerB);
        when(this.room.getHabboItem(30)).thenReturn(this.chair);
        when(this.room.getItemsAt(1, 1)).thenReturn(new LinkedHashSet<>(List.of(this.triggerA, picksChair)));
        when(this.room.getItemsAt(2, 2)).thenReturn(new LinkedHashSet<>(List.of(this.triggerB)));
        Set<HabboItem> floor = new LinkedHashSet<>(List.of(this.triggerA, picksChair, this.triggerB, this.chair));
        when(this.room.getFloorItems()).thenReturn(floor);
        when(this.room.getRoomUnits()).thenReturn(new LinkedHashSet<RoomUnit>());
    }

    private static HabboItem trigger(int id, int x, int y) {
        HabboItem trigger = mock(InteractionWiredTrigger.class);
        when(trigger.getId()).thenReturn(id);
        when(trigger.getX()).thenReturn((short) x);
        when(trigger.getY()).thenReturn((short) y);
        return trigger;
    }

    private Set<HabboItem> run(int combine, int... picks) {
        WiredEffectRemoteSelector remote = new WiredEffectRemoteSelector(1, 1, mock(Item.class), "", 0, 0);
        remote.saveData(new WiredSettings(new int[] {combine, 0}, "", picks, 0), null);
        WiredContext ctx = new WiredContext(
                WiredEvent.builder(WiredEvent.Type.CUSTOM, this.room).build(),
                null,
                mock(WiredServices.class),
                new WiredState(20));
        remote.execute(ctx);
        assertTrue(ctx.targets().isItemsModifiedBySelector());
        assertTrue(ctx.targets().isUsersModifiedBySelector());
        return ctx.targets().items();
    }

    @Test
    void pickingTheTriggerRunsThatStacksSelectors() {
        assertEquals(Set.of(this.chair), this.run(0, 10));
    }

    @Test
    void aStackWithoutSelectorIsEmptyAndIntersectsToNothing() {
        assertEquals(Set.of(), this.run(0, 20));
        assertEquals(Set.of(this.chair), this.run(0, 10, 20));
        assertEquals(Set.of(), this.run(1, 10, 20));
    }
}
