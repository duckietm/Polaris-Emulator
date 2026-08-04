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
import java.util.stream.IntStream;

public final class JdbcCatalogValidationDataRepository implements CatalogValidationDataRepository {
    @Override
    public CatalogValidationReferenceData load(Connection connection) throws SQLException {
        return new CatalogValidationReferenceData(
                loadIntegerColumn(connection, "SELECT id FROM items_base", "id"),
                IntStream.rangeClosed(0, 100).boxed().collect(Collectors.toUnmodifiableSet()),
                loadLimitedSells(connection),
                java.util.Arrays.stream(CatalogPageLayouts.values())
                        .map(Enum::name)
                        .collect(Collectors.toUnmodifiableSet()));
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
        try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT id, limited_sells FROM catalog_items WHERE limited_sells > 0");
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                values.put(resultSet.getInt("id"), resultSet.getInt("limited_sells"));
            }
        }
        return Map.copyOf(values);
    }
}
