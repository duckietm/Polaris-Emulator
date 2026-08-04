package com.eu.habbo.habbohotel.catalog.versioning;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import java.sql.Connection;
import java.sql.PreparedStatement;
import org.junit.jupiter.api.Test;

class JdbcCatalogSnapshotWriterTest {
    private final Gson gson = new Gson();

    @Test
    void appliesAPageUpdateToTheRequestedDraftVersion() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(JdbcCatalogSnapshotWriter.UPSERT_PAGE_SQL))
                .thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(1);
        CatalogPageSnapshot page = page("Edited");
        CatalogChangeEntry change = new CatalogChangeEntry(
                0,
                CatalogEntityType.PAGE,
                17,
                CatalogChangeOperation.UPDATE,
                gson.toJson(page("Old")),
                gson.toJson(page));

        new JdbcCatalogSnapshotWriter(gson).apply(connection, 2, change);

        verify(statement).setLong(1, 2);
        verify(statement).setString(2, "NORMAL");
        verify(statement).setInt(3, 17);
        verify(statement).setString(6, "Edited");
        verify(statement).executeUpdate();
    }

    @Test
    void appliesAnOfferDeletionOnlyInsideTheRequestedVersion() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(JdbcCatalogSnapshotWriter.DELETE_OFFER_SQL))
                .thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(1);
        CatalogChangeEntry change = new CatalogChangeEntry(
                0, CatalogEntityType.OFFER, 42, CatalogChangeOperation.DELETE, gson.toJson(offer()), null);

        new JdbcCatalogSnapshotWriter(gson).apply(connection, 2, change);

        verify(statement).setLong(1, 2);
        verify(statement).setString(2, "NORMAL");
        verify(statement).setInt(3, 42);
        verify(statement).executeUpdate();
    }

    private static CatalogPageSnapshot page(String caption) {
        return new CatalogPageSnapshot(
                17,
                -1,
                "rare_page",
                caption,
                "default_3x3",
                1,
                145,
                1,
                1,
                true,
                true,
                false,
                "NORMAL",
                false,
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                0,
                "");
    }

    private static CatalogOfferSnapshot offer() {
        return new CatalogOfferSnapshot(42, "12", 17, "rare_dragon", 25, 0, 0, 1, 0, 1, -1, 0, "", true, false);
    }
}
