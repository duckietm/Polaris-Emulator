package com.eu.habbo.habbohotel.catalog.versioning;

import java.sql.Connection;
import java.sql.SQLException;

@FunctionalInterface
public interface CatalogLiveProjection {
    void replace(Connection connection, CatalogVersionSnapshot snapshot) throws SQLException;
}
