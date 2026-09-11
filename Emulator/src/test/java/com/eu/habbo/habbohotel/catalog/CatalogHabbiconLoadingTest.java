package com.eu.habbo.habbohotel.catalog;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;

class CatalogHabbiconLoadingTest {
    @Test
    void reloadAcceptsHabbiconOnlyOffersButStillRejectsEmptyFurnitureOffers() throws Exception {
        CatalogManager manager = mock(CatalogManager.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        when(connection.prepareStatement("SELECT * FROM catalog_items WHERE id = ? LIMIT 1"))
                .thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(true);
        when(rows.getString("item_ids")).thenReturn("0");

        try (var cache = mockStatic(CatalogAdminCacheSync.class, CALLS_REAL_METHODS);
                var catalogItems = mockConstruction(CatalogItem.class)) {
            cache.when(CatalogAdminCacheSync::currentCatalogManager).thenReturn(manager);
            cache.when(CatalogAdminCacheSync::openCatalogConnection).thenReturn(connection);
            assertFalse(CatalogAdminCacheSync.reloadCatalogItem(10, CatalogPageType.NORMAL));
            when(rows.getInt("habbicon_id")).thenReturn(61);
            assertTrue(CatalogAdminCacheSync.reloadCatalogItem(10, CatalogPageType.NORMAL));
            org.junit.jupiter.api.Assertions.assertEquals(
                    1, catalogItems.constructed().size());
        }
    }
}
