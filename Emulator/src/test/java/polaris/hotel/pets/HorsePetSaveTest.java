package polaris.hotel.pets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

/** The horse columns and the base row are saved together: a failure keeps the pet dirty. */
class HorsePetSaveTest {

    private static HorsePet dirtyHorse() {
        HorsePet horse = mock(HorsePet.class, CALLS_REAL_METHODS);
        horse.id = 7;
        horse.needsUpdate = true;
        return horse;
    }

    @Test
    void aFailedHorseUpdateKeepsThePetDirtyAndSkipsTheBaseRow() throws SQLException {
        HorsePet horse = dirtyHorse();
        Connection connection = mock(Connection.class);
        PreparedStatement horseStatement = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(horseStatement);
        when(horseStatement.executeUpdate()).thenThrow(new SQLException("down"));

        assertThrows(SQLException.class, () -> horse.save(connection));

        assertTrue(horse.needsUpdate);
        verify(connection, never()).prepareStatement(startsWith("UPDATE users_pets SET room_id"));
    }

    @Test
    void aFailedBaseUpdateKeepsThePetDirty() throws SQLException {
        HorsePet horse = dirtyHorse();
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(1, 0);

        assertThrows(SQLException.class, () -> horse.save(connection));

        assertTrue(horse.needsUpdate);
    }

    @Test
    void bothWritesSucceedingClearTheFlag() throws SQLException {
        HorsePet horse = dirtyHorse();
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(1);

        horse.save(connection);

        assertFalse(horse.needsUpdate);
    }
}
