package com.eu.habbo.habbohotel.catalog.versioning;

import com.eu.habbo.habbohotel.catalog.CatalogPageLayouts;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class JdbcCatalogValidationDataRepository implements CatalogValidationDataRepository {

    static final String ITEM_DEFINITIONS_SQL = "SELECT id FROM items_base";
    static final String HELD_CURRENCY_TYPES_SQL = "SELECT DISTINCT type FROM users_currency";
    static final String CONFIGURED_CURRENCY_TYPES_SQL =
            "SELECT `value` FROM emulator_settings WHERE `key` LIKE '%points.type%'";
    static final String LIMITED_SELLS_SQL = "SELECT id, limited_sells FROM catalog_items WHERE limited_sells > 0";

    @Override
    public CatalogValidationReferenceData load(Connection connection) throws SQLException {
        return new CatalogValidationReferenceData(
                loadIntegerColumn(connection, ITEM_DEFINITIONS_SQL, "id"),
                loadCurrencyTypes(connection),
                loadLimitedSells(connection),
                java.util.Arrays.stream(CatalogPageLayouts.values())
                        .map(Enum::name)
                        .collect(Collectors.toUnmodifiableSet()));
    }

    /**
     * The currency types this hotel actually has.
     *
     * <p>There is no registry of currencies in the emulator - {@code getCurrencyAmount(int)} takes
     * any integer - so the accepted set has to be read from the hotel rather than assumed. It used
     * to be the fixed range 0..100, which flagged every offer priced in a higher-numbered currency
     * even when players were holding it, and passed every offer priced in a currency nobody can
     * hold as long as the number was small enough.
     *
     * <p>Type 0 is always accepted: it is the baseline currency every user has. Beyond that, a type
     * counts as real if some user holds it, or if the hotel is configured to pay it out - the second
     * source matters for a currency that has just been introduced and has no holders yet.
     */
    private static Set<Integer> loadCurrencyTypes(Connection connection) throws SQLException {
        Set<Integer> types = new HashSet<>();
        types.add(0);
        types.addAll(loadIntegerColumn(connection, HELD_CURRENCY_TYPES_SQL, "type"));
        try (PreparedStatement statement = connection.prepareStatement(CONFIGURED_CURRENCY_TYPES_SQL);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String value = resultSet.getString("value");
                if (value == null) continue;
                try {
                    types.add(Integer.parseInt(value.trim()));
                } catch (NumberFormatException ignored) {
                    // A setting whose value is not a number simply names no currency.
                }
            }
        }
        return Set.copyOf(types);
    }

    private static Set<Integer> loadIntegerColumn(Connection connection, String sql, String column)
            throws SQLException {
        Set<Integer> values = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) values.add(resultSet.getInt(column));
        }
        return Set.copyOf(values);
    }

    private static Map<Integer, Integer> loadLimitedSells(Connection connection) throws SQLException {
        Map<Integer, Integer> values = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(LIMITED_SELLS_SQL);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                values.put(resultSet.getInt("id"), resultSet.getInt("limited_sells"));
            }
        }
        return Map.copyOf(values);
    }
}
