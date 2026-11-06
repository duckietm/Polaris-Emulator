package polaris.hotel.catalog.layouts;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zaxxer.hikari.HikariDataSource;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import polaris.Emulator;
import polaris.core.ConfigurationManager;
import polaris.database.Database;
import polaris.hotel.GameEnvironment;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomManager;

class RoomBundleLayoutPurchaseTest {
    private final Map<Field, Object> originals = new LinkedHashMap<>();
    private Connection connection;
    private PreparedStatement roomInsert;
    private PreparedStatement itemsInsert;
    private RoomManager roomManager;
    private RoomBundleLayout layout;

    @BeforeEach
    void setUp() throws Exception {
        this.connection = mock(Connection.class);
        this.roomInsert = mock(PreparedStatement.class);
        this.itemsInsert = mock(PreparedStatement.class);
        when(this.connection.prepareStatement(contains("INSERT INTO rooms"), anyInt()))
                .thenReturn(this.roomInsert);
        when(this.connection.prepareStatement(contains("INSERT INTO items"))).thenReturn(this.itemsInsert);
        this.newRoomId(77);

        HikariDataSource dataSource = mock(HikariDataSource.class);
        when(dataSource.getConnection()).thenReturn(this.connection);
        Database database = mock(Database.class);
        when(database.getDataSource()).thenReturn(dataSource);
        this.install("database", database);
        this.install("config", mock(ConfigurationManager.class));
        this.roomManager = mock(RoomManager.class);
        GameEnvironment environment = mock(GameEnvironment.class);
        when(environment.getRoomManager()).thenReturn(this.roomManager);
        this.install("gameEnvironment", environment);

        Room template = mock(Room.class);
        when(template.getId()).thenReturn(10);
        when(template.getFloorItems()).thenReturn(new HashSet<>());
        when(template.getWallItems()).thenReturn(new HashSet<>());
        this.layout = mock(RoomBundleLayout.class, CALLS_REAL_METHODS);
        this.layout.room = template;
        Field loaded = RoomBundleLayout.class.getDeclaredField("loaded");
        loaded.setAccessible(true);
        loaded.setBoolean(this.layout, true);
        doReturn(new Int2ObjectOpenHashMap<>()).when(this.layout).getCatalogItems();
    }

    @AfterEach
    void restore() throws IllegalAccessException {
        for (Map.Entry<Field, Object> entry : this.originals.entrySet()) {
            entry.getKey().set(null, entry.getValue());
        }
    }

    @Test
    void aFailedFurniCopyLeavesNoRoomAndLetsTheCallerRefund() throws Exception {
        when(this.itemsInsert.execute()).thenThrow(new SQLException("items copy failed"));

        // The caller refunds on a RuntimeException: before, the error was logged, the room row
        // stayed committed without its furni and the buyer kept paying for it.
        assertThrows(IllegalStateException.class, () -> this.layout.buyRoom(null, 5, "buyer"));

        verify(this.connection).rollback();
        verify(this.connection, never()).commit();
        verify(this.roomManager, never()).loadRoom(anyInt());
    }

    @Test
    void aTemplateThatIsNotCopiedRefusesThePurchase() throws Exception {
        this.newRoomId(0);

        assertThrows(IllegalStateException.class, () -> this.layout.buyRoom(null, 5, "buyer"));

        verify(this.connection).rollback();
        verify(this.connection, never()).prepareStatement(anyString());
    }

    @Test
    void aBundleWithoutTemplateRoomRefusesThePurchase() {
        this.layout.room = null;

        assertThrows(IllegalStateException.class, () -> this.layout.buyRoom(null, 5, "buyer"));
    }

    @Test
    void aCommittedRoomThatDoesNotLoadIsStillDelivered() throws Exception {
        when(this.roomManager.loadRoom(77)).thenReturn(null);

        // Paid and committed: throwing here would refund a room the buyer keeps.
        assertDoesNotThrow(() -> this.layout.buyRoom(null, 5, "buyer"));

        verify(this.connection).commit();
        verify(this.connection, never()).rollback();
    }

    private void newRoomId(int id) throws SQLException {
        ResultSet keys = mock(ResultSet.class);
        when(keys.next()).thenReturn(id > 0);
        when(keys.getInt(1)).thenReturn(id);
        when(this.roomInsert.getGeneratedKeys()).thenReturn(keys);
    }

    private void install(String name, Object value) throws Exception {
        Field field = Emulator.class.getDeclaredField(name);
        field.setAccessible(true);
        this.originals.put(field, field.get(null));
        field.set(null, value);
    }
}
