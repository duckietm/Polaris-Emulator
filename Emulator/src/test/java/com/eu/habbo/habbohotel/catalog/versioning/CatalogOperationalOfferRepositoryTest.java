package com.eu.habbo.habbohotel.catalog.versioning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class CatalogOperationalOfferRepositoryTest {
    @Test
    void readsTheLiveLimitedSalesCounterWithoutMutatingCatalogData() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(CatalogOperationalOfferRepository.FIND_LIMITED_SELLS_SQL))
                .thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getInt("limited_sells")).thenReturn(4);

        assertEquals(
                4,
                new CatalogOperationalOfferRepository(dataSource)
                        .findLimitedSells(42)
                        .orElseThrow());
        verify(statement).setInt(1, 42);
    }
}
