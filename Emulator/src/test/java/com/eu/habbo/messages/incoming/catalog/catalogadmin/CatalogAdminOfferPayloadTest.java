package com.eu.habbo.messages.incoming.catalog.catalogadmin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import org.junit.jupiter.api.Test;

class CatalogAdminOfferPayloadTest {
    @Test
    void acceptsAndNormalizesValidOfferPayload() {
        CatalogAdminOfferPayload payload = CatalogAdminOfferPayload.validate(
                42, "1, 2,3", "Rare Chair", 100, 5, 0, 1, 0,
                "extra", true, 0, 0, 10, CatalogPageType.NORMAL);

        assertNotNull(payload);
        assertEquals("1,2,3", payload.itemIds);
        assertEquals("Rare Chair", payload.catalogName);
    }

    @Test
    void rejectsInvalidItemIdsAndNegativeEconomyValues() {
        assertNull(CatalogAdminOfferPayload.validate(42, "1,abc", "Name", 0, 0, 0, 1, 0,
                "", false, 0, 0, 0, CatalogPageType.NORMAL));
        assertNull(CatalogAdminOfferPayload.validate(42, "1", "Name", -1, 0, 0, 1, 0,
                "", false, 0, 0, 0, CatalogPageType.NORMAL));
        assertNull(CatalogAdminOfferPayload.validate(42, "1", "Name", 0, 0, 0, 0, 0,
                "", false, 0, 0, 0, CatalogPageType.NORMAL));
    }

    @Test
    void builderOffersStillRequireSafeCommonFields() {
        assertNotNull(CatalogAdminOfferPayload.validate(42, "", "BC Offer", -1, -1, -1, -1, -1,
                "", false, -1, -1, 0, CatalogPageType.BUILDER));
        assertNull(CatalogAdminOfferPayload.validate(0, "1", "BC Offer", 0, 0, 0, 1, 0,
                "", false, 0, 0, 0, CatalogPageType.BUILDER));
        assertNull(CatalogAdminOfferPayload.validate(42, "1", "", 0, 0, 0, 1, 0,
                "", false, 0, 0, 0, CatalogPageType.BUILDER));
    }
}
