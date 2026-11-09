package polaris.hotel.catalog.versioning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;
import polaris.hotel.catalog.CatalogPageType;

/** A new id is never one a row already has, also when rows were added outside Catalog Studio. */
class JdbcCatalogVersionRepositoryNextIdTest {

    private static PreparedStatement returning(long value) throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getLong(1)).thenReturn(value);
        return statement;
    }

    private static long nextPageId(long sequence, long tableNext) throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement sequenceRead = returning(sequence);
        PreparedStatement tableRead = returning(tableNext);
        PreparedStatement update = mock(PreparedStatement.class);
        when(update.executeUpdate()).thenReturn(1);
        when(connection.prepareStatement(anyString())).thenAnswer(call -> {
            String sql = call.getArgument(0);
            if (sql.startsWith("SELECT next_id")) return sequenceRead;
            if (sql.startsWith("SELECT COALESCE(MAX(id)")) return tableRead;
            return update;
        });
        long id = new JdbcCatalogVersionRepository().nextPageId(connection, CatalogPageType.NORMAL);
        verify(update).setLong(1, id + 1);
        return id;
    }

    @Test
    void theSequenceIsUsedWhileItIsAhead() throws Exception {
        assertEquals(1111, nextPageId(1111, 900));
    }

    @Test
    void rowsAddedOutsideTheSequenceAreSkipped() throws Exception {
        assertEquals(1205, nextPageId(1111, 1205));
    }

    @Test
    void theTableQueryNamesTheRightTable() {
        assertEquals(
                "SELECT COALESCE(MAX(id), 0) + 1 FROM catalog_items_bc",
                JdbcCatalogVersionRepository.tableNextIdSql(CatalogEntityType.OFFER, CatalogPageType.BUILDER));
    }
}
