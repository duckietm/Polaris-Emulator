package com.eu.habbo.habbohotel.catalog.versioning;

import java.sql.Connection;
import java.sql.SQLException;

@FunctionalInterface
public interface CatalogLiveSnapshotRepository {
    CatalogVersionSnapshot load(Connection connection, CatalogVersion version) throws SQLException;
}
