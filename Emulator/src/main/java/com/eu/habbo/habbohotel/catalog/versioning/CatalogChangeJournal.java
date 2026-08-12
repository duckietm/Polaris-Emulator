package com.eu.habbo.habbohotel.catalog.versioning;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public interface CatalogChangeJournal {
    CatalogChangeGroup load(Connection connection, long groupId) throws SQLException;

    boolean hasLaterChangesToSameEntities(Connection connection, CatalogChangeGroup group) throws SQLException;

    default void delete(Connection connection, long groupId) throws SQLException {
        throw new UnsupportedOperationException("Catalog change deletion is not supported");
    }

    long append(
            Connection connection,
            long versionId,
            long revision,
            int actorId,
            String summary,
            CatalogChangeSource source,
            List<CatalogChangeEntry> entries)
            throws SQLException;
}
