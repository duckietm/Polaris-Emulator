package com.eu.habbo.database.migration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CatalogSoundOfferReferencesMigrationContractTest {
    private static final Path MIGRATION =
            Path.of("src/main/resources/db/migration/V20260817180000__repair_catalog_sound_offer_references.sql");

    @Test
    void repairsLiveAndVersionedOffersWithoutReplacingValidReferences() throws IOException {
        String sql = Files.readString(MIGRATION);

        assertTrue(sql.contains("UPDATE `catalog_items`"));
        assertTrue(sql.contains("UPDATE `catalog_version_offers`"));
        assertTrue(sql.contains("offer.`song_id` = IF(offer.`song_id` = 0"));
        assertTrue(sql.contains("offer.`extradata` = IF(TRIM(offer.`extradata`) = ''"));
        assertTrue(sql.contains("'SONG LostMyTapesAtGoa', 23, 'lost_my_tapes_at_goa'"));
        assertTrue(sql.contains("'SONG Trax_1', 15, 'party_trax'"));
        assertTrue(sql.contains("'SONG Xmas11', 26, 'xmas_2011'"));
    }
}
