package polaris.hotel.items.interactions.wired.selector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionWiredTrigger;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredEvent;
import polaris.hotel.wired.core.WiredServices;
import polaris.hotel.wired.core.WiredState;

/** The remote selector runs whole picked stacks: any box of a stack stands for it, a stack without selector is empty. */
class WiredEffectRemoteSelectorStacksTest {
    private final Room room = mock(Room.class);
    private final UserItem chair = mock(UserItem.class);
    private final UserItem triggerA = trigger(10, 1, 1);
    private final UserItem triggerB = trigger(20, 2, 2);

    WiredEffectRemoteSelectorStacksTest() {
        WiredEffectFurniPicks picksChair = new WiredEffectFurniPicks(11, 1, mock(Item.class), "", 0, 0);
        picksChair.setX((short) 1);
        picksChair.setY((short) 1);
        picksChair.saveData(new WiredSettings(new int[0], "", new int[] {30}, 0), null);
        when(this.chair.getId()).thenReturn(30);
        when(this.room.getUserItem(10)).thenReturn(this.triggerA);
        when(this.room.getUserItem(11)).thenReturn(picksChair);
        when(this.room.getUserItem(20)).thenReturn(this.triggerB);
        when(this.room.getUserItem(30)).thenReturn(this.chair);
        when(this.room.getItemsAt(1, 1)).thenReturn(new LinkedHashSet<>(List.of(this.triggerA, picksChair)));
        when(this.room.getItemsAt(2, 2)).thenReturn(new LinkedHashSet<>(List.of(this.triggerB)));
        Set<UserItem> floor = new LinkedHashSet<>(List.of(this.triggerA, picksChair, this.triggerB, this.chair));
        when(this.room.getFloorItems()).thenReturn(floor);
        when(this.room.getRoomUnits()).thenReturn(new LinkedHashSet<RoomUnit>());
    }

    private static UserItem trigger(int id, int x, int y) {
        UserItem trigger = mock(InteractionWiredTrigger.class);
        when(trigger.getId()).thenReturn(id);
        when(trigger.getX()).thenReturn((short) x);
        when(trigger.getY()).thenReturn((short) y);
        return trigger;
    }

    private Set<UserItem> run(int combine, int... picks) {
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

    private WiredEffectRemoteSelector remoteAt(int id, int x, int y, int... picks) {
        WiredEffectRemoteSelector remote = new WiredEffectRemoteSelector(id, 1, mock(Item.class), "", 0, 0);
        remote.setX((short) x);
        remote.setY((short) y);
        remote.saveData(new WiredSettings(new int[] {0, 0}, "", picks, 0), null);
        when(this.room.getUserItem(id)).thenReturn(remote);
        return remote;
    }

    @Test
    void aRemoteSelectionInAPickedStackRunsToo() {
        UserItem triggerC = trigger(40, 3, 3);
        when(this.room.getUserItem(40)).thenReturn(triggerC);
        WiredEffectRemoteSelector nested = this.remoteAt(41, 3, 3, 10);
        when(this.room.getItemsAt(3, 3)).thenReturn(new LinkedHashSet<>(List.of(triggerC, nested)));

        assertEquals(Set.of(this.chair), this.run(0, 40));
    }

    @Test
    void twoRemotesPickingEachOtherStop() {
        UserItem triggerD = trigger(50, 4, 4);
        UserItem triggerE = trigger(60, 5, 5);
        when(this.room.getUserItem(50)).thenReturn(triggerD);
        when(this.room.getUserItem(60)).thenReturn(triggerE);
        WiredEffectRemoteSelector d = this.remoteAt(51, 4, 4, 60);
        WiredEffectRemoteSelector e = this.remoteAt(61, 5, 5, 50, 10);
        when(this.room.getItemsAt(4, 4)).thenReturn(new LinkedHashSet<>(List.of(triggerD, d)));
        when(this.room.getItemsAt(5, 5)).thenReturn(new LinkedHashSet<>(List.of(triggerE, e)));

        assertEquals(Set.of(this.chair), this.run(0, 50));
    }
}
