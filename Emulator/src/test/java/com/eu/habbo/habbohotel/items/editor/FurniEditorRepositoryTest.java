package com.eu.habbo.habbohotel.items.editor;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class FurniEditorRepositoryTest {

    @Test
    void spriteLookupUsesTheProvidedDataSource() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getInt("id")).thenReturn(42);

        int itemId =
                new FurniEditorRepository(dataSource).findItemIdBySprite(91).orElseThrow();

        assertEquals(42, itemId);
        verify(statement).setInt(1, 91);
        verify(connection).close();
    }

    @Test
    void deletionStopsBeforeMutationWhenPlacedItemsExist() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement exists = mock(PreparedStatement.class);
        PreparedStatement count = mock(PreparedStatement.class);
        ResultSet existsResult = mock(ResultSet.class);
        ResultSet countResult = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement("SELECT id FROM items_base WHERE id = ?"))
                .thenReturn(exists);
        when(connection.prepareStatement("SELECT COUNT(*) FROM items WHERE item_id = ?"))
                .thenReturn(count);
        when(exists.executeQuery()).thenReturn(existsResult);
        when(count.executeQuery()).thenReturn(countResult);
        when(existsResult.next()).thenReturn(true);
        when(countResult.next()).thenReturn(true);
        when(countResult.getInt(1)).thenReturn(3);

        var result = new FurniEditorRepository(dataSource).deleteItem(12);

        assertAll(
                () -> assertEquals(FurniEditorRepository.DeleteStatus.IN_USE, result.status()),
                () -> assertEquals(3, result.referenceCount()));
        verify(connection, never()).prepareStatement("DELETE FROM items_base WHERE id = ?");
    }

    @Test
    void deletionStopsWhenTheFurniOnlyLivesInAWiredChest() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement exists = mock(PreparedStatement.class);
        PreparedStatement items = mock(PreparedStatement.class);
        PreparedStatement chests = mock(PreparedStatement.class);
        ResultSet existsResult = mock(ResultSet.class);
        ResultSet itemsResult = mock(ResultSet.class);
        ResultSet chestsResult = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement("SELECT id FROM items_base WHERE id = ?"))
                .thenReturn(exists);
        when(connection.prepareStatement("SELECT COUNT(*) FROM items WHERE item_id = ?"))
                .thenReturn(items);
        when(connection.prepareStatement("SELECT COUNT(*) FROM wired_chest_items WHERE item_id = ?"))
                .thenReturn(chests);
        when(exists.executeQuery()).thenReturn(existsResult);
        when(items.executeQuery()).thenReturn(itemsResult);
        when(chests.executeQuery()).thenReturn(chestsResult);
        when(existsResult.next()).thenReturn(true);
        when(itemsResult.next()).thenReturn(true);
        when(itemsResult.getInt(1)).thenReturn(0);
        when(chestsResult.next()).thenReturn(true);
        when(chestsResult.getInt(1)).thenReturn(2);

        var result = new FurniEditorRepository(dataSource).deleteItem(12);

        assertAll(
                () -> assertEquals(FurniEditorRepository.DeleteStatus.IN_USE, result.status()),
                () -> assertEquals(2, result.referenceCount()));
        verify(connection, never()).prepareStatement("DELETE FROM items_base WHERE id = ?");
    }
}
