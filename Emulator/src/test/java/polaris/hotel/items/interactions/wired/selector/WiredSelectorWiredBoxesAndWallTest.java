package polaris.hotel.items.interactions.wired.selector;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredEvent;
import polaris.hotel.wired.core.WiredServices;
import polaris.hotel.wired.core.WiredState;

/** AIR's furni selectors keep wired boxes, and By Type and inversion reach wall furni. */
class WiredSelectorWiredBoxesAndWallTest {
    private final Room room = mock(Room.class);

    private WiredContext context() {
        return new WiredContext(
                WiredEvent.builder(WiredEvent.Type.CUSTOM, this.room).build(),
                null,
                mock(WiredServices.class),
                new WiredState(20));
    }

    @Test
    void furniInAreaSelectsTheWiredBoxesInIt() throws Exception {
        UserItem trigger = mock(InteractionWiredTrigger.class);
        UserItem block = mock(UserItem.class);
        when(this.room.getItemsAt(1, 1)).thenReturn(new LinkedHashSet<>(List.of(trigger, block)));
        WiredEffectFurniArea area = new WiredEffectFurniArea(1, 1, mock(Item.class), "", 0, 0);
        area.saveData(new WiredSettings(new int[] {1, 1, 1, 1}, "", new int[0], 0), null);
        WiredContext ctx = this.context();

        area.execute(ctx);

        assertEquals(Set.of(trigger, block), ctx.targets().items());
    }

    @Test
    void byTypeSelectsEveryWallFurniOfThePickedType() throws Exception {
        Item posterType = mock(Item.class);
        when(posterType.getId()).thenReturn(77);
        UserItem poster = wallItem(5, posterType);
        UserItem otherPoster = wallItem(6, posterType);
        when(this.room.getUserItem(5)).thenReturn(poster);
        when(this.room.getFloorItems()).thenReturn(new LinkedHashSet<>());
        when(this.room.getWallItems()).thenReturn(new LinkedHashSet<>(List.of(poster, otherPoster)));
        WiredEffectFurniByType byType = new WiredEffectFurniByType(1, 1, mock(Item.class), "", 0, 0);
        byType.saveData(new WiredSettings(new int[] {0, 0}, "", new int[] {5}, 0), null);
        WiredContext ctx = this.context();

        byType.execute(ctx);

        assertEquals(Set.of(poster, otherPoster), ctx.targets().items());
    }

    private static UserItem wallItem(int id, Item base) {
        UserItem item = mock(UserItem.class);
        when(item.getId()).thenReturn(id);
        when(item.getBaseItem()).thenReturn(base);
        return item;
    }
}
