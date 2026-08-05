package com.eu.habbo.messages.incoming.catalog.catalogadmin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import org.junit.jupiter.api.Test;

class CatalogAdminOfferDraftDataTest {
    @Test
    void normalOfferCreationPreservesFlagsAndSongIdFromTheEditor() {
        CatalogAdminOfferPayload payload = CatalogAdminOfferPayload.validate(
                42, "100", "Sound offer", 5, 0, 0, 1, 1, "extra", false, 77, 0, -1, 321, CatalogPageType.NORMAL);

        var data = CatalogAdminOfferDraftData.from(payload);

        assertEquals(321, data.songId());
        assertFalse(data.haveOffer());
        assertTrue(data.clubOnly());
    }

    @Test
    void builderOfferCreationUsesBuilderSafeValues() {
        CatalogAdminOfferPayload payload = CatalogAdminOfferPayload.validate(
                42, "", "Builder offer", -1, -1, -1, -1, -1, "extra", false, -1, -1, -1, 321, CatalogPageType.BUILDER);

        var data = CatalogAdminOfferDraftData.from(payload);

        assertEquals(0, data.songId());
        assertTrue(data.haveOffer());
        assertFalse(data.clubOnly());
    }
}
