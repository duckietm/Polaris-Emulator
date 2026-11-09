package polaris.hotel.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zaxxer.hikari.HikariDataSource;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import polaris.Emulator;
import polaris.database.Database;
import polaris.hotel.GameEnvironment;
import polaris.hotel.catalog.CatalogManager;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserItem;

class RecycleTransactionTest {
    private final Map<Field, Object> originals = new LinkedHashMap<>();
    private Connection connection;
    private PreparedStatement delete;
    private PreparedStatement boxInsert;
    private PreparedStatement prizeInsert;
    private User user;

    @BeforeEach
    void setUp() throws Exception {
        this.connection = mock(Connection.class);
        this.delete = mock(PreparedStatement.class);
        this.boxInsert = mock(PreparedStatement.class);
        this.prizeInsert = mock(PreparedStatement.class);
        when(this.connection.prepareStatement(contains("DELETE FROM items"))).thenReturn(this.delete);
        when(this.connection.prepareStatement(contains("INSERT INTO items "), anyInt()))
                .thenReturn(this.boxInsert);
        when(this.connection.prepareStatement(contains("items_presents"))).thenReturn(this.prizeInsert);
        when(this.delete.executeUpdate()).thenReturn(1);
        ResultSet keys = mock(ResultSet.class);
        when(keys.next()).thenReturn(true);
        when(keys.getInt(1)).thenReturn(500);
        when(this.boxInsert.getGeneratedKeys()).thenReturn(keys);

        HikariDataSource dataSource = mock(HikariDataSource.class);
        when(dataSource.getConnection()).thenReturn(this.connection);
        Database database = mock(Database.class);
        when(database.getDataSource()).thenReturn(dataSource);
        this.install("database", database);

        Item ecotron = mock(Item.class);
        when(ecotron.getId()).thenReturn(9);
        CatalogManager catalog = mock(CatalogManager.class);
        Field ecotronField = CatalogManager.class.getDeclaredField("ecotronItem");
        ecotronField.setAccessible(true);
        ecotronField.set(catalog, ecotron);
        GameEnvironment environment = mock(GameEnvironment.class);
        when(environment.getCatalogManager()).thenReturn(catalog);
        this.install("gameEnvironment", environment);

        UserInfo info = mock(UserInfo.class);
        when(info.getId()).thenReturn(42);
        this.user = mock(User.class);
        when(this.user.getUserInfo()).thenReturn(info);
    }

    @AfterEach
    void restore() throws IllegalAccessException {
        for (Map.Entry<Field, Object> entry : this.originals.entrySet()) {
            entry.getKey().set(null, entry.getValue());
        }
    }

    @Test
    void theFurniAndTheBoxAreWrittenTogether() throws Exception {
        UserItem box = new ItemManager().handleRecycle(this.user, "77", List.of(furni(1), furni(2)));

        assertNotNull(box);
        assertEquals(500, box.getId());
        verify(this.delete).setInt(1, 1);
        verify(this.delete).setInt(1, 2);
        verify(this.prizeInsert).setInt(1, 500);
        verify(this.prizeInsert).setInt(2, 77);
        verify(this.connection).commit();
        verify(this.connection, never()).rollback();
    }

    @Test
    void aFurniAlreadyGoneWritesNothing() throws Exception {
        when(this.delete.executeUpdate()).thenReturn(1, 0);

        // Before, the prize was saved first and the furni deleted later in the background: a
        // delete that did not happen left both the prize and the furni that paid for it.
        assertNull(new ItemManager().handleRecycle(this.user, "77", List.of(furni(1), furni(2))));

        verify(this.connection).rollback();
        verify(this.connection, never()).commit();
    }

    @Test
    void aMissingPrizeRowGivesNoBox() throws Exception {
        when(this.prizeInsert.executeUpdate()).thenThrow(new SQLException("items_presents failed"));

        // Before, the box was returned without its prize row and opened empty.
        assertNull(new ItemManager().handleRecycle(this.user, "77", List.of(furni(1))));

        verify(this.connection).rollback();
        verify(this.connection, never()).commit();
    }

    @Test
    void anIngredientAlreadyGoneCraftsNothing() throws Exception {
        when(this.delete.executeUpdate()).thenReturn(1, 0);

        // Crafting saved the reward first and deleted the ingredients later in the background.
        assertNull(new ItemManager().craftInTransaction(42, mock(Item.class), List.of(furni(1), furni(2))));

        verify(this.connection).rollback();
        verify(this.connection, never()).commit();
    }

    @Test
    void aLimitedRecipeUnitIsTakenOnTheCraftConnection() throws Exception {
        PreparedStatement recipeUpdate = mock(PreparedStatement.class);
        when(this.connection.prepareStatement(contains("crafting_recipes"))).thenReturn(recipeUpdate);

        when(this.delete.executeUpdate()).thenReturn(0);

        // The remaining count was written only on reload or shutdown, so a crash sold the crafted units again.
        // Here the ingredient is gone: the unit taken on the same connection goes back with the rollback.
        assertNull(new ItemManager().craftInTransaction(42, mock(Item.class), List.of(furni(1)), 7));

        verify(recipeUpdate).setInt(1, 7);
        verify(recipeUpdate).executeUpdate();
        verify(this.connection).rollback();
        verify(this.connection, never()).commit();
    }

    @Test
    void nothingToRecycleIsRefused() {
        assertNull(new ItemManager().handleRecycle(this.user, "77", List.of()));
    }

    private static UserItem furni(int id) {
        UserItem item = mock(UserItem.class);
        when(item.getId()).thenReturn(id);
        return item;
    }

    private void install(String name, Object value) throws Exception {
        Field field = Emulator.class.getDeclaredField(name);
        field.setAccessible(true);
        this.originals.put(field, field.get(null));
        field.set(null, value);
    }
}
