package polaris.hotel.items;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zaxxer.hikari.HikariDataSource;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import polaris.Emulator;
import polaris.database.Database;
import polaris.hotel.users.UserItem;

class CrackableRewardTransactionTest {
    private Field databaseField;
    private Object originalDatabase;
    private Connection connection;
    private PreparedStatement delete;
    private UserItem crackable;

    @BeforeEach
    void setUp() throws Exception {
        this.connection = mock(Connection.class);
        this.delete = mock(PreparedStatement.class);
        when(this.connection.prepareStatement(contains("DELETE FROM items"))).thenReturn(this.delete);
        when(this.delete.executeUpdate()).thenReturn(1);

        HikariDataSource dataSource = mock(HikariDataSource.class);
        when(dataSource.getConnection()).thenReturn(this.connection);
        Database database = mock(Database.class);
        when(database.getDataSource()).thenReturn(dataSource);
        this.databaseField = Emulator.class.getDeclaredField("database");
        this.databaseField.setAccessible(true);
        this.originalDatabase = this.databaseField.get(null);
        this.databaseField.set(null, database);

        this.crackable = mock(UserItem.class);
        when(this.crackable.getId()).thenReturn(31);
    }

    @AfterEach
    void restore() throws IllegalAccessException {
        this.databaseField.set(null, this.originalDatabase);
    }

    @Test
    void aRewardThatCannotBeCreatedKeepsTheCrackable() throws Exception {
        when(this.connection.prepareStatement(contains("INSERT INTO items"), anyInt()))
                .thenThrow(new SQLException("insert failed"));
        Item reward = mock(Item.class);
        when(reward.getId()).thenReturn(9);

        // Before, the crackable was deleted on its own connection first and the reward then
        // failed: the player lost the box and got nothing.
        assertThrows(SQLException.class, () -> new ItemManager().crackInTransaction(this.crackable, true, 42, reward));

        verify(this.delete).setInt(1, 31);
        verify(this.connection).rollback();
        verify(this.connection, never()).commit();
    }

    @Test
    void aCrackableAlreadyGoneGivesNoReward() throws Exception {
        when(this.delete.executeUpdate()).thenReturn(0);

        // A failed or repeated delete used to leave the box to be cracked again with a new reward.
        assertThrows(
                SQLException.class,
                () -> new ItemManager().crackInTransaction(this.crackable, true, 42, mock(Item.class)));

        verify(this.connection).rollback();
        verify(this.connection, never()).prepareStatement(anyString(), anyInt());
    }

    @Test
    void aResettableCrackableWithoutRewardOnlyCommits() throws Exception {
        assertNull(new ItemManager().crackInTransaction(this.crackable, false, 42, null));

        verify(this.connection, never()).prepareStatement(anyString());
        verify(this.connection).commit();
    }
}
