package polaris.hotel.guilds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zaxxer.hikari.HikariDataSource;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import polaris.Emulator;
import polaris.database.Database;
import polaris.hotel.guilds.forums.ForumView;

class GuildForumViewsTest {
    private Field databaseField;
    private Object originalDatabase;
    private GuildManager manager;

    @BeforeEach
    void setUp() throws Exception {
        ResultSet empty = mock(ResultSet.class);
        Statement statement = mock(Statement.class);
        when(statement.executeQuery(anyString())).thenReturn(empty);
        PreparedStatement prepared = mock(PreparedStatement.class);
        when(prepared.executeQuery()).thenReturn(empty);
        Connection connection = mock(Connection.class);
        when(connection.createStatement()).thenReturn(statement);
        when(connection.prepareStatement(anyString())).thenReturn(prepared);
        HikariDataSource dataSource = mock(HikariDataSource.class);
        when(dataSource.getConnection()).thenReturn(connection);
        Database database = mock(Database.class);
        when(database.getDataSource()).thenReturn(dataSource);
        this.databaseField = Emulator.class.getDeclaredField("database");
        this.databaseField.setAccessible(true);
        this.originalDatabase = this.databaseField.get(null);
        this.databaseField.set(null, database);

        this.manager = new GuildManager();
    }

    @AfterEach
    void restore() throws IllegalAccessException {
        this.databaseField.set(null, this.originalDatabase);
    }

    @Test
    void theMostViewedForumComesFirst() throws Exception {
        Guild quiet = this.readableGuild(1);
        Guild busy = this.readableGuild(2);
        this.manager.addView(10, 1);
        this.manager.addView(10, 2);
        this.manager.addView(11, 2);
        this.manager.addView(12, 2);

        // The list was sorted from the least viewed and then collected into an unordered set.
        assertEquals(List.of(busy, quiet), new ArrayList<>(this.manager.getMostViewed()));
    }

    @Test
    void viewsOlderThanAWeekLeaveMemory() throws Exception {
        this.views().add(new ForumView(10, 1, Emulator.getIntUnixTimestamp() - 8 * 24 * 60 * 60));

        this.manager.addView(11, 1);

        assertEquals(1, this.views().size());
        assertFalse(this.manager.hasViewedForum(10, 1));
        assertTrue(this.manager.hasViewedForum(11, 1));
    }

    @Test
    void viewsAndTheMostViewedListCanRunTogether() throws Exception {
        this.readableGuild(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            // A plain HashSet threw ConcurrentModificationException when one player opened a forum
            // while another listed the most viewed ones.
            Future<?> writer = pool.submit(() -> {
                start.await();
                for (int i = 0; i < 2_000; i++) this.manager.addView(i, 1);
                return null;
            });
            Future<?> reader = pool.submit(() -> {
                start.await();
                for (int i = 0; i < 2_000; i++) this.manager.getMostViewed();
                return null;
            });
            start.countDown();
            writer.get(30, TimeUnit.SECONDS);
            reader.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }

    private Guild readableGuild(int id) throws Exception {
        Guild guild = mock(Guild.class);
        when(guild.getId()).thenReturn(id);
        when(guild.canReadForum()).thenReturn(SettingsState.EVERYONE);
        this.guilds().put(id, guild);
        return guild;
    }

    @SuppressWarnings("unchecked")
    private Int2ObjectMap<Guild> guilds() throws Exception {
        Field field = GuildManager.class.getDeclaredField("guilds");
        field.setAccessible(true);
        return (Int2ObjectMap<Guild>) field.get(this.manager);
    }

    @SuppressWarnings("unchecked")
    private Set<ForumView> views() throws Exception {
        Field field = GuildManager.class.getDeclaredField("views");
        field.setAccessible(true);
        return (Set<ForumView>) field.get(this.manager);
    }
}
