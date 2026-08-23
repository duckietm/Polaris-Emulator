package com.eu.habbo.habbohotel.catalog.versioning;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

public interface CatalogVersionRepository {
    CatalogRuntimeState lockRuntimeState(Connection connection) throws SQLException;

    CatalogVersionSnapshot loadSnapshot(Connection connection, long versionId) throws SQLException;

    default CatalogVersion loadVersion(Connection connection, long versionId) throws SQLException {
        return loadSnapshot(connection, versionId).version();
    }

    default Optional<CatalogPageSnapshot> loadPage(
            Connection connection, long versionId, CatalogPageType catalogType, int pageId) throws SQLException {
        return loadSnapshot(connection, versionId).page(catalogType, pageId);
    }

    default Optional<CatalogOfferSnapshot> loadOffer(
            Connection connection, long versionId, CatalogPageType catalogType, int offerId) throws SQLException {
        return loadSnapshot(connection, versionId).offer(catalogType, offerId);
    }

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
}
