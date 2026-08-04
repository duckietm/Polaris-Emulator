package com.eu.habbo.habbohotel.catalog.versioning;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.OptionalInt;
import javax.sql.DataSource;

public final class CatalogOperationalOfferRepository {
    static final String FIND_LIMITED_SELLS_SQL = "SELECT limited_sells FROM catalog_items WHERE id = ? LIMIT 1";

    private final DataSource dataSource;

    public CatalogOperationalOfferRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    public OptionalInt findLimitedSells(int offerId) {
        if (offerId <= 0) throw new IllegalArgumentException("Offer ID must be positive");
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(FIND_LIMITED_SELLS_SQL)) {
            statement.setInt(1, offerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) return OptionalInt.empty();
                return OptionalInt.of(resultSet.getInt("limited_sells"));
            }
        } catch (SQLException exception) {
            throw new CatalogVersioningException("Could not read the operational limited sales counter", exception);
        }
    }
}
