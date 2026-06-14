package com.eu.habbo.messages.incoming.catalog.catalogadmin;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class CatalogAdminOfferMutationContractTest {
    private static final Path CREATE_SOURCE = Path.of(
            "src/main/java/com/eu/habbo/messages/incoming/catalog/catalogadmin/CatalogAdminCreateOfferEvent.java");
    private static final Path SAVE_SOURCE = Path.of(
            "src/main/java/com/eu/habbo/messages/incoming/catalog/catalogadmin/CatalogAdminSaveOfferEvent.java");

    @Test
    void createAndSaveValidatePayloadAndTargetPageBeforeWriting() throws IOException {
        String create = Files.readString(CREATE_SOURCE);
        String save = Files.readString(SAVE_SOURCE);

        assertTrue(create.contains("CatalogAdminOfferPayload.validate("));
        assertTrue(save.contains("CatalogAdminOfferPayload.validate("));
        assertTrue(create.contains("getCatalogPage(payload.pageId, payload.pageType) == null"));
        assertTrue(save.contains("getCatalogPage(payload.pageId, payload.pageType) == null"));

        int createValidation = create.indexOf("CatalogAdminOfferPayload.validate(");
        int createInsert = create.indexOf("INSERT INTO catalog_items");
        int saveValidation = save.indexOf("CatalogAdminOfferPayload.validate(");
        int saveUpdate = save.indexOf("UPDATE catalog_items");

        assertTrue(createValidation < createInsert, "create offer should validate before insert SQL is prepared");
        assertTrue(saveValidation < saveUpdate, "save offer should validate before update SQL is prepared");
    }

    @Test
    void saveOfferReportsMissingRowsInsteadOfAlwaysSucceeding() throws IOException {
        String save = Files.readString(SAVE_SOURCE);

        assertTrue(save.contains("statement.executeUpdate() == 0"));
        assertTrue(save.contains("Offer not found: "));
    }
}
