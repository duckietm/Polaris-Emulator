package polaris.hotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import polaris.hotel.items.FurnitureType;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionDefault;
import polaris.hotel.users.UserItem;

/** The tile index answers like the old whole-room scan, and follows furni as they move. */
class RoomItemTileIndexTest {

    private Room room;
    private RoomItemIndex index;

    @BeforeEach
    void setUp() {
        this.room = mock(Room.class);
        when(this.room.isLoaded()).thenReturn(true);
        RoomLayout layout = mock(RoomLayout.class);
        when(layout.getTile(anyShort(), anyShort())).thenAnswer(call -> tile(call.getArgument(0), call.getArgument(1)));
        when(this.room.getLayout()).thenReturn(layout);
        this.index = new RoomItemIndex(this.room);
    }

    @Test
    void aMovedFurniLeavesItsOldTilesAndCoversItsNewOnes() {
        UserItem table = this.add(item(1, 2, 1), 3, 3, 0);

        assertEquals(Set.of(table), this.at(3, 3));
        assertEquals(Set.of(table), this.at(4, 3));

        table.setX((short) 7);
        table.setY((short) 8);

        assertTrue(this.at(3, 3).isEmpty());
        assertTrue(this.at(4, 3).isEmpty());
        assertEquals(Set.of(table), this.at(7, 8));
        assertEquals(Set.of(table), this.at(8, 8));
    }

    @Test
    void rotatingALongFurniTurnsItsFootprint() {
        UserItem sofa = this.add(item(2, 2, 1), 1, 1, 0);

        sofa.setRotation(2);

        assertTrue(this.at(2, 1).isEmpty());
        assertEquals(Set.of(sofa), this.at(1, 2));
    }

    @Test
    void stackedFurniShareATileAndLeavingDropsOnlyOne() {
        UserItem floor = this.add(item(3, 1, 1), 5, 5, 0);
        UserItem lamp = this.add(item(4, 1, 1), 5, 5, 0);
        assertEquals(Set.of(floor, lamp), this.at(5, 5));

        synchronized (this.index.items()) {
            this.index.items().remove(lamp.getId());
        }
        this.index.untrackItem(lamp);

        assertEquals(Set.of(floor), this.at(5, 5));
    }

    @Test
    void furniPutStraightIntoTheMapAreFoundAfterARebuild() {
        UserItem tracked = this.add(item(5, 1, 1), 0, 0, 0);
        assertEquals(Set.of(tracked), this.at(0, 0));

        UserItem untracked = item(6, 1, 1);
        untracked.setX((short) 9);
        untracked.setY((short) 9);
        synchronized (this.index.items()) {
            this.index.items().put(untracked.getId(), untracked);
        }

        assertEquals(Set.of(untracked), this.at(9, 9));
        untracked.setX((short) 10);
        assertEquals(Set.of(untracked), this.at(10, 9));
    }

    @Test
    void wallFurniAreNeverOnATile() {
        UserItem poster = item(7, 1, 1);
        when(poster.getBaseItem().getType()).thenReturn(FurnitureType.WALL);
        this.add(poster, 2, 2, 0);

        assertTrue(this.at(2, 2).isEmpty());
    }

    private UserItem add(UserItem item, int x, int y, int rotation) {
        item.setX((short) x);
        item.setY((short) y);
        item.setRotation(rotation);
        synchronized (this.index.items()) {
            this.index.items().put(item.getId(), item);
            this.index.trackItem(item);
        }
        return item;
    }

    private Set<UserItem> at(int x, int y) {
        return this.index.itemsAt(tile((short) x, (short) y), false);
    }

    private static RoomTile tile(short x, short y) {
        return new RoomTile(x, y, (short) 0, RoomTileState.OPEN, true);
    }

    private static UserItem item(int id, int width, int length) {
        Item baseItem = mock(Item.class);
        when(baseItem.getType()).thenReturn(FurnitureType.FLOOR);
        when(baseItem.getWidth()).thenReturn(width);
        when(baseItem.getLength()).thenReturn(length);
        return new InteractionDefault(id, 1, baseItem, "0", 0, 0);
    }
}
