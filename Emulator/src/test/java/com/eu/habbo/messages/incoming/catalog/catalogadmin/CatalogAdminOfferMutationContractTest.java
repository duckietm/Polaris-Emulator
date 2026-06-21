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
    private static final Path DELETE_SOURCE = Path.of(
            "src/main/java/com/eu/habbo/messages/incoming/catalog/catalogadmin/CatalogAdminDeleteOfferEvent.java");
    private static final Path MOVE_SOURCE = Path.of(
            "src/main/java/com/eu/habbo/messages/incoming/catalog/catalogadmin/CatalogAdminMoveOfferEvent.java");

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

    @Test
    void deleteOfferRejectsInvalidIdsAndReportsMissingRows() throws IOException {
        String delete = Files.readString(DELETE_SOURCE);

        assertTrue(delete.contains("offerId <= 0"));
        assertTrue(delete.contains("Invalid offer id"));
        assertTrue(delete.contains("statement.executeUpdate() == 0"));
        assertTrue(delete.contains("Offer not found: "));
    }

    @Test
    void moveOfferRejectsInvalidIdsClampsOrderAndReportsMissingRows() throws IOException {
        String move = Files.readString(MOVE_SOURCE);

        assertTrue(move.contains("offerId <= 0"));
        assertTrue(move.contains("Invalid offer id"));
        assertTrue(move.contains("if (orderNumber < 0) orderNumber = 0;"));
        assertTrue(move.contains("statement.executeUpdate() == 0"));
        assertTrue(move.contains("Offer not found: "));
    }
}
