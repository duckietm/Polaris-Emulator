package com.eu.habbo.habbohotel.catalog.versioning;

import java.sql.Connection;
import java.sql.SQLException;

public interface CatalogSnapshotWriter {
    void apply(Connection connection, long versionId, CatalogChangeEntry change) throws SQLException;

    void replace(Connection connection, long versionId, CatalogVersionSnapshot source) throws SQLException;
}
