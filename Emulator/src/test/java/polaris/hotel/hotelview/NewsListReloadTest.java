package polaris.hotel.hotelview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.database.Database;

/** A reload emptied the news list while the hotel view composer read it. */
class NewsListReloadTest {

    private static NewsList withNews(List<NewsWidget> news) throws Exception {
        NewsList list = mock(NewsList.class, CALLS_REAL_METHODS);
        Field field = NewsList.class.getDeclaredField("newsWidgets");
        field.setAccessible(true);
        field.set(list, new ArrayList<>(news));
        return list;
    }

    @Test
    void aReloadPublishesANewListAndLeavesTheOneBeingRead() throws Exception {
        NewsList list = withNews(List.of(mock(NewsWidget.class)));
        List<NewsWidget> beingRead = list.getNewsWidgets();
        ResultSet rows = mock(ResultSet.class);
        Statement statement = mock(Statement.class);
        when(statement.executeQuery(anyString())).thenReturn(rows);
        Connection connection = mock(Connection.class);
        when(connection.createStatement()).thenReturn(statement);
        Database database = mock(Database.class, RETURNS_DEEP_STUBS);
        when(database.getDataSource().getConnection()).thenReturn(connection);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getDatabase).thenReturn(database);

            list.reload();
        }

        assertEquals(1, beingRead.size(), "the list a composer holds is not emptied under it");
        assertNotSame(beingRead, list.getNewsWidgets());
    }

    @Test
    void aReloadThatCannotReachTheDatabaseKeepsTheNews() throws Exception {
        NewsList list = withNews(List.of(mock(NewsWidget.class)));
        Database database = mock(Database.class, RETURNS_DEEP_STUBS);
        when(database.getDataSource().getConnection()).thenThrow(new SQLException("down"));

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getDatabase).thenReturn(database);

            list.reload();
        }

        assertEquals(1, list.getNewsWidgets().size());
    }
}
