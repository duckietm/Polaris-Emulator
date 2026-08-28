package com.eu.habbo.habbohotel.catalog.versioning;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JdbcCatalogValidationDataRepositoryTest {

    @Test
    void acceptsTheCurrenciesTheHotelActuallyHas() throws Exception {
        Connection connection = mock(Connection.class);
        stubIntegerColumn(connection, JdbcCatalogValidationDataRepository.ITEM_DEFINITIONS_SQL, "id");
        stubIntegerColumn(connection, JdbcCatalogValidationDataRepository.HELD_CURRENCY_TYPES_SQL, "type", 5, 101, 102);
        stubStringColumn(connection, JdbcCatalogValidationDataRepository.CONFIGURED_CURRENCY_TYPES_SQL, "value");
        stubEmpty(connection, JdbcCatalogValidationDataRepository.LIMITED_SELLS_SQL);

        Set<Integer> currencies =
                new JdbcCatalogValidationDataRepository().load(connection).currencyTypes();

        // 101 and 102 are held by players; the old fixed range 0..100 rejected them.
        assertTrue(currencies.containsAll(Set.of(0, 5, 101, 102)), () -> "got " + currencies);
        // 104 is priced in the catalog but nobody holds it and nothing pays it out.
        assertFalse(currencies.contains(104), () -> "got " + currencies);
    }

    @Test
    void acceptsACurrencyTheHotelPaysOutBeforeAnyoneHoldsIt() throws Exception {
        Connection connection = mock(Connection.class);
        stubIntegerColumn(connection, JdbcCatalogValidationDataRepository.ITEM_DEFINITIONS_SQL, "id");
        stubIntegerColumn(connection, JdbcCatalogValidationDataRepository.HELD_CURRENCY_TYPES_SQL, "type");
        stubStringColumn(
                connection,
                JdbcCatalogValidationDataRepository.CONFIGURED_CURRENCY_TYPES_SQL,
                "value",
                "7",
                "not a number",
                null);
        stubEmpty(connection, JdbcCatalogValidationDataRepository.LIMITED_SELLS_SQL);

        Set<Integer> currencies =
                new JdbcCatalogValidationDataRepository().load(connection).currencyTypes();

        assertTrue(currencies.containsAll(Set.of(0, 7)), () -> "got " + currencies);
        assertFalse(currencies.contains(104), () -> "got " + currencies);
    }

    private static void stubIntegerColumn(Connection connection, String sql, String column, int... values)
            throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        Boolean[] more = new Boolean[values.length];
        for (int i = 0; i < values.length; i++) more[i] = true;
        when(resultSet.next()).thenReturn(values.length > 0, appendFalse(more));
        if (values.length > 0) {
            Integer[] rest = new Integer[values.length - 1];
            for (int i = 1; i < values.length; i++) rest[i - 1] = values[i];
            when(resultSet.getInt(column)).thenReturn(values[0], rest);
        }
        bind(connection, sql, resultSet);
    }

    private static void stubStringColumn(Connection connection, String sql, String column, String... values)
            throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        Boolean[] more = new Boolean[values.length];
        for (int i = 0; i < values.length; i++) more[i] = true;
        when(resultSet.next()).thenReturn(values.length > 0, appendFalse(more));
        if (values.length > 0) {
            String[] rest = new String[values.length - 1];
            System.arraycopy(values, 1, rest, 0, values.length - 1);
            when(resultSet.getString(column)).thenReturn(values[0], rest);
        }
        bind(connection, sql, resultSet);
    }

    private static void stubEmpty(Connection connection, String sql) throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(false);
        bind(connection, sql, resultSet);
    }

    private static void bind(Connection connection, String sql, ResultSet resultSet) throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(sql)).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
    }

    private static Boolean[] appendFalse(Boolean[] more) {
        Boolean[] result = new Boolean[more.length];
        for (int i = 0; i < more.length - 1; i++) result[i] = true;
        if (more.length > 0) result[more.length - 1] = false;
        return result;
    }
}
