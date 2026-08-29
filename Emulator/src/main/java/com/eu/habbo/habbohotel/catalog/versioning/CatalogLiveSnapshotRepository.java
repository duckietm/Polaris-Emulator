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

    /**
     * Reads every page, and only the offers the given mutations can reach, locking what it reads.
     *
     * <p>Defaults to the full locking read, so an implementation that cannot narrow stays correct -
     * a snapshot with more offers than needed is never wrong, only slower.
     */
    default CatalogVersionSnapshot loadForMutation(
            Connection connection, CatalogVersion version, CatalogMutationScope scope) throws SQLException {
        return load(connection, version);
    }
}
