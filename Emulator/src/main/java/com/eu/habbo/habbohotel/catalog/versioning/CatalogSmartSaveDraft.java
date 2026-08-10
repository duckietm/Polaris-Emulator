package com.eu.habbo.habbohotel.catalog.versioning;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;

public final class CatalogSmartSaveDraft {
    private final Connection connection;
    private final CatalogVersionRepository versions;
    private final long versionId;

    CatalogSmartSaveDraft(Connection connection, CatalogVersionRepository versions, long versionId) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.versions = Objects.requireNonNull(versions, "versions");
        this.versionId = versionId;
    }

    public Optional<CatalogPageSnapshot> page(CatalogPageType catalogType, int pageId) {
        try {
            return versions.loadPage(connection, versionId, catalogType, pageId);
        } catch (SQLException exception) {
            throw new CatalogVersioningException("Targeted catalog page lookup failed", exception);
        }
    }

    public Optional<CatalogOfferSnapshot> offer(CatalogPageType catalogType, int offerId) {
        try {
            return versions.loadOffer(connection, versionId, catalogType, offerId);
        } catch (SQLException exception) {
            throw new CatalogVersioningException("Targeted catalog offer lookup failed", exception);
        }
    }
}
