package com.eu.habbo.habbohotel.catalog.versioning;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import java.time.Instant;
import java.util.List;

final class CatalogDocumentTestSupport {
    private CatalogDocumentTestSupport() {}

    static CatalogVersionSnapshot snapshot() {
        CatalogVersion version = new CatalogVersion(
                2, CatalogVersionStatus.DRAFT, 1L, 4, "Draft", 7, Instant.parse("2026-08-02T09:00:00Z"), null, null);
        CatalogPageSnapshot page = new CatalogPageSnapshot(
                CatalogPageType.NORMAL,
                17,
                -1,
                "page",
                "Page",
                "default_3x3",
                1,
                1,
                1,
                1,
                true,
                true,
                false,
                "NORMAL",
                false,
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                0,
                "");
        CatalogOfferSnapshot offer = new CatalogOfferSnapshot(
                CatalogPageType.NORMAL, 42, "12", 17, "offer", 10, 0, 0, 1, 0, 1, -1, 0, "", true, false);
        return new CatalogVersionSnapshot(version, List.of(page), List.of(offer));
    }
}
