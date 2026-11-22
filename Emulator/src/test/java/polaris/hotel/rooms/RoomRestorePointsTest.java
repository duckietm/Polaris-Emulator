package polaris.hotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import polaris.hotel.items.interactions.InteractionWiredHighscore;
import polaris.hotel.users.UserItem;

/** The wired menu's Rollback: furni go back to where they stood when the room loaded. */
class RoomRestorePointsTest {
    private static <T extends UserItem> T item(
            Class<T> type, int id, int x, int y, double z, int rotation, String data) {
        T item = mock(type);
        when(item.getId()).thenReturn(id);
        when(item.getX()).thenReturn((short) x);
        when(item.getY()).thenReturn((short) y);
        when(item.getZ()).thenReturn(z);
        when(item.getRotation()).thenReturn(rotation);
        when(item.getWallPosition()).thenReturn("");
        when(item.getExtradata()).thenReturn(data);
        return item;
    }

    @Test
    void aMovedAndSwitchedFurniGoesBackAndIsSaved() {
        RoomRestorePoints points = new RoomRestorePoints();
        UserItem item = item(UserItem.class, 1, 3, 4, 0.5, 2, "0");
        points.captureLoaded(List.of(item));

        when(item.getX()).thenReturn((short) 9);
        when(item.getExtradata()).thenReturn("1");

        assertEquals(1, points.restore(List.of(item)));
        verify(item).setX((short) 3);
        verify(item).setY((short) 4);
        verify(item).setZ(0.5);
        verify(item).setRotation(2);
        verify(item).setExtradata("0");
        verify(item).needsUpdate(true);
    }

    @Test
    void anUntouchedFurniIsNotSaved() {
        RoomRestorePoints points = new RoomRestorePoints();
        UserItem item = item(UserItem.class, 1, 3, 4, 0.5, 2, "0");
        points.captureLoaded(List.of(item));

        assertEquals(0, points.restore(List.of(item)));
        verify(item, never()).needsUpdate(true);
    }

    @Test
    void aHighScoreBoardKeepsItsScores() {
        RoomRestorePoints points = new RoomRestorePoints();
        InteractionWiredHighscore board = item(InteractionWiredHighscore.class, 2, 1, 1, 0, 0, "0");
        points.captureLoaded(List.of(board));

        when(board.getY()).thenReturn((short) 7);
        when(board.getExtradata()).thenReturn("1");

        assertEquals(1, points.restore(List.of(board)));
        verify(board).setY((short) 1);
        verify(board, never()).setExtradata("0");
    }

    @Test
    void aFurniPlacedSinceGoesBackToWhereItWasPlacedAndAStrangerIsLeftAlone() {
        RoomRestorePoints points = new RoomRestorePoints();
        points.captureLoaded(List.of());
        UserItem placed = item(UserItem.class, 3, 5, 5, 0, 0, "0");
        points.capturePlaced(placed);
        when(placed.getX()).thenReturn((short) 6);
        // A second placement of a furni the room already knows keeps the older point.
        points.capturePlaced(placed);
        UserItem stranger = item(UserItem.class, 4, 2, 2, 0, 0, "0");

        assertEquals(1, points.restore(List.of(placed, stranger)));
        verify(placed).setX((short) 5);
        verify(stranger, never()).needsUpdate(true);
    }
}
