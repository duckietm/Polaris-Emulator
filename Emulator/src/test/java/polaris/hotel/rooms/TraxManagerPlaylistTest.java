package polaris.hotel.rooms;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import polaris.hotel.items.interactions.InteractionJukeBox;
import polaris.hotel.items.interactions.InteractionMusicDisc;

class TraxManagerPlaylistTest {

    @Test
    void aPlaylistEmptiedWhilePlayingStopsInsteadOfDividingByZero() throws Exception {
        Room room = mock(Room.class);
        RoomSpecialTypes specialTypes = mock(RoomSpecialTypes.class);
        when(specialTypes.getItemsOfType(InteractionJukeBox.class)).thenReturn(new HashSet<>());
        when(room.getRoomSpecialTypes()).thenReturn(specialTypes);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(statement.executeQuery()).thenReturn(mock(ResultSet.class));
        when(connection.prepareStatement(anyString())).thenReturn(statement);

        TraxManager trax = new TraxManager(room, () -> connection);
        InteractionJukeBox jukeBox = mock(InteractionJukeBox.class);
        set(trax, "jukeBox", jukeBox);
        // The last disc was taken out by someone with rights while the room cycle was advancing:
        // the cycle computed (index + 1) % songs.size() on an empty list.
        set(trax, "currentlyPlaying", mock(InteractionMusicDisc.class));

        assertDoesNotThrow(trax::cycle);

        assertFalse(trax.isPlaying());
        verify(jukeBox).setExtradata("0");
    }

    private static void set(TraxManager trax, String name, Object value) throws Exception {
        Field field = TraxManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(trax, value);
    }
}
