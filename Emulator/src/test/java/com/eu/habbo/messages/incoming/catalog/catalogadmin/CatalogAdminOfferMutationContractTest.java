package com.eu.habbo.messages.incoming.catalog.catalogadmin;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertTrue(create.contains("services().liveMutations()"));
        assertTrue(save.contains("services().liveMutations()"));
        assertTrue(create.contains("live.page(pageType, payload.pageId).isEmpty()"));
        assertTrue(save.contains("live.page(pageType, payload.pageId).isEmpty()"));
        assertFalse(create.contains("loadDraft("));
        assertFalse(save.contains("loadDraft("));

        int createValidation = create.indexOf("CatalogAdminOfferPayload.validate(");
        int createInsert = create.indexOf("liveMutations.applyBatch(");
        int saveValidation = save.indexOf("CatalogAdminOfferPayload.validate(");
        int saveUpdate = save.indexOf("liveMutations.applyBatch(");

        assertTrue(createValidation < createInsert, "create offer should validate before insert SQL is prepared");
        assertTrue(saveValidation < saveUpdate, "save offer should validate before update SQL is prepared");
    }

    @Test
    void saveOfferReportsMissingRowsInsteadOfAlwaysSucceeding() throws IOException {
        String save = Files.readString(SAVE_SOURCE);

        assertTrue(save.contains("live.offer(pageType, offerId).orElse(null)"));
        assertTrue(save.contains("Offer not found: "));
    }

    @Test
    void deleteOfferRejectsInvalidIdsAndReportsMissingRows() throws IOException {
        String delete = Files.readString(DELETE_SOURCE);

        assertTrue(delete.contains("offerId <= 0"));
        assertTrue(delete.contains("Invalid offer id"));
        assertTrue(delete.contains("live.offer(pageType, offerId).isEmpty()"));
        assertTrue(delete.contains("Live catalog offer not found: "));
    }

    @Test
    void moveOfferRejectsInvalidIdsClampsOrderAndUsesLiveMutationService() throws IOException {
        String move = Files.readString(MOVE_SOURCE);

        assertTrue(move.contains("offerId <= 0"));
        assertTrue(move.contains("Invalid offer id"));
        assertTrue(move.contains("if (orderNumber < 0) orderNumber = 0;"));
        assertTrue(move.contains(".liveMutations()"));
        assertTrue(move.contains(".updateOffer("));
    }
}
