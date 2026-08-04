package com.eu.habbo.habbohotel.catalog.versioning;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import java.sql.Connection;
import java.sql.SQLException;

public interface CatalogVersionRepository {
    CatalogRuntimeState lockRuntimeState(Connection connection) throws SQLException;

    CatalogVersionSnapshot loadSnapshot(Connection connection, long versionId) throws SQLException;

    long cloneAsDraft(Connection connection, long sourceVersionId, int actorId, String label) throws SQLException;

    long nextPageId(Connection connection) throws SQLException;

    default long nextPageId(Connection connection, CatalogPageType catalogType) throws SQLException {
        if (catalogType != CatalogPageType.NORMAL) {
            throw new UnsupportedOperationException("Catalog type is not supported by this repository");
        }
        return nextPageId(connection);
    }

    long nextOfferId(Connection connection) throws SQLException;

    default long nextOfferId(Connection connection, CatalogPageType catalogType) throws SQLException {
        if (catalogType != CatalogPageType.NORMAL) {
            throw new UnsupportedOperationException("Catalog type is not supported by this repository");
        }
        return nextOfferId(connection);
    }

    long incrementRevision(Connection connection, long versionId, long expectedRevision) throws SQLException;

    void archiveVersion(Connection connection, long versionId) throws SQLException;

    void markPublished(Connection connection, long versionId, int actorId) throws SQLException;

    void updateRuntimePointers(Connection connection, long activeVersionId, long draftVersionId) throws SQLException;
}
