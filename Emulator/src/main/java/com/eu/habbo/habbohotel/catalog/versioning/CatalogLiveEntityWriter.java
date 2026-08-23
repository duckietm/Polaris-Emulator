package com.eu.habbo.habbohotel.catalog.versioning;

import java.sql.Connection;
import java.sql.SQLException;

@FunctionalInterface
public interface CatalogLiveEntityWriter {
    void apply(Connection connection, CatalogChangeEntry change) throws SQLException;
}
