package com.eu.habbo.habbohotel.catalog.versioning;

import java.sql.Connection;
import java.sql.SQLException;

@FunctionalInterface
public interface CatalogLiveSnapshotRepository {
    /** Reads the live catalog and locks it for the caller's transaction. */
    CatalogVersionSnapshot load(Connection connection, CatalogVersion version) throws SQLException;

    /** Reads the live catalog without locking it. Defaults to the locking read for implementations that cannot. */
    default CatalogVersionSnapshot loadForRead(Connection connection, CatalogVersion version) throws SQLException {
        return load(connection, version);
    }
}
