package com.eu.habbo.habbohotel.catalog.versioning;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.google.gson.Gson;
import java.sql.Connection;
import java.sql.PreparedStatement;
import org.junit.jupiter.api.Test;

class JdbcCatalogLiveEntityWriterTest {
    private final Gson gson = new Gson();

    @Test
    void writesEveryEditableNormalOfferFieldToTheLiveTable() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(JdbcCatalogLiveEntityWriter.UPSERT_NORMAL_OFFER_SQL))
                .thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(1);
        CatalogOfferSnapshot offer = new CatalogOfferSnapshot(
                CatalogPageType.NORMAL, 42, "12;13", 17, "bundle", 5, 7, 5, 2, 9, 4, 88, 3, "x", true, true);

        new JdbcCatalogLiveEntityWriter(gson)
                .apply(
                        connection,
                        new CatalogChangeEntry(
                                0,
                                CatalogEntityType.OFFER,
                                CatalogPageType.NORMAL,
                                42,
                                CatalogChangeOperation.UPDATE,
                                gson.toJson(offer),
                                gson.toJson(offer)));

        verify(statement).setInt(1, 42);
        verify(statement).setString(2, "12;13");
        verify(statement).setInt(3, 17);
        verify(statement).setString(4, "bundle");
        verify(statement).setInt(5, 5);
        verify(statement).setInt(6, 7);
        verify(statement).setInt(7, 5);
        verify(statement).setInt(8, 2);
        verify(statement).setInt(9, 9);
        verify(statement).setInt(10, 4);
        verify(statement).setInt(11, 88);
        verify(statement).setInt(12, 3);
        verify(statement).setString(13, "x");
        verify(statement).setString(14, "1");
        verify(statement).setString(15, "1");
        verify(statement).executeUpdate();
    }
}
