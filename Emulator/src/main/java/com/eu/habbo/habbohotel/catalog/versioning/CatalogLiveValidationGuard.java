package com.eu.habbo.habbohotel.catalog.versioning;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.google.gson.Gson;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Allows repairs to an inconsistent live catalog while rejecting newly introduced problems. */
public final class CatalogLiveValidationGuard {
    private final CatalogValidationDataRepository validationData;
    private final Gson gson;

    public CatalogLiveValidationGuard(CatalogValidationDataRepository validationData, Gson gson) {
        this.validationData = Objects.requireNonNull(validationData, "validationData");
        this.gson = Objects.requireNonNull(gson, "gson");
    }

    public CatalogValidationReport report(Connection connection, CatalogVersionSnapshot live) throws SQLException {
        return validationData.load(connection).validator().validate(live);
    }

    public void rejectIntroducedProblems(
            Connection connection, CatalogVersionSnapshot live, List<CatalogChangeEntry> changes) throws SQLException {
        CatalogVersionSnapshot candidate = apply(live, changes);
        CatalogValidationReport introduced =
                validationData.load(connection).validator().validateChanges(live, candidate);
        if (!introduced.valid()) throw new CatalogLiveValidationException(introduced);
    }

    private CatalogVersionSnapshot apply(CatalogVersionSnapshot live, List<CatalogChangeEntry> changes) {
        Map<EntityKey, CatalogPageSnapshot> pages = new LinkedHashMap<>();
        Map<EntityKey, CatalogOfferSnapshot> offers = new LinkedHashMap<>();
        live.pages().forEach(page -> pages.put(new EntityKey(page.catalogType(), page.pageId()), page));
        live.offers().forEach(offer -> offers.put(new EntityKey(offer.catalogType(), offer.offerId()), offer));

        for (CatalogChangeEntry change : changes) {
            EntityKey key = new EntityKey(change.catalogType(), change.entityId());
            if (change.entityType() == CatalogEntityType.PAGE) {
                applyPage(pages, key, change);
            } else {
                applyOffer(offers, key, change);
            }
        }
        return new CatalogVersionSnapshot(
                live.version(), new ArrayList<>(pages.values()), new ArrayList<>(offers.values()));
    }

    private void applyPage(Map<EntityKey, CatalogPageSnapshot> pages, EntityKey key, CatalogChangeEntry change) {
        if (change.operation() == CatalogChangeOperation.DELETE) {
            pages.remove(key);
            return;
        }
        CatalogPageSnapshot page = gson.fromJson(change.afterJson(), CatalogPageSnapshot.class);
        requireIdentity(key, page.catalogType(), page.pageId());
        pages.put(key, page);
    }

    private void applyOffer(Map<EntityKey, CatalogOfferSnapshot> offers, EntityKey key, CatalogChangeEntry change) {
        if (change.operation() == CatalogChangeOperation.DELETE) {
            offers.remove(key);
            return;
        }
        CatalogOfferSnapshot offer = gson.fromJson(change.afterJson(), CatalogOfferSnapshot.class);
        requireIdentity(key, offer.catalogType(), offer.offerId());
        offers.put(key, offer);
    }

    private static void requireIdentity(EntityKey key, CatalogPageType catalogType, int entityId) {
        if (key.catalogType() != catalogType || key.entityId() != entityId) {
            throw new IllegalArgumentException("Catalog change payload identity does not match its target");
        }
    }

    private record EntityKey(CatalogPageType catalogType, int entityId) {}
}
