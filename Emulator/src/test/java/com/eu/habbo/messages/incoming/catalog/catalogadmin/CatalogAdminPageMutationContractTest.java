package com.eu.habbo.messages.incoming.catalog.catalogadmin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogAdminPageMutationContractTest {
    private static final Path HANDLER_ROOT =
            Path.of("src/main/java/com/eu/habbo/messages/incoming/catalog/catalogadmin");
    private static final Path CREATE_SOURCE = HANDLER_ROOT.resolve("CatalogAdminCreatePageEvent.java");
    private static final Path SAVE_SOURCE = HANDLER_ROOT.resolve("CatalogAdminSavePageEvent.java");
    private static final Path MOVE_SOURCE = HANDLER_ROOT.resolve("CatalogAdminMovePageEvent.java");

    @Test
    void pageCreateAndSaveUseTargetedSmartSaveChecks() throws IOException {
        String create = Files.readString(CREATE_SOURCE);
        String save = Files.readString(SAVE_SOURCE);

        assertTrue(create.contains("services().smartSaves()"));
        assertTrue(save.contains("services().smartSaves()"));
        assertTrue(create.contains("CatalogAdminPageDraftChecks.validateParentAndIncludes"));
        assertTrue(save.contains("CatalogAdminPageDraftChecks.validateParentAndIncludes"));
        assertFalse(create.contains("loadDraft("));
        assertFalse(save.contains("loadDraft("));
    }

    @Test
    void rootParentIdsRemainValidForMovesAndRootCreation() throws IOException {
        String create = Files.readString(CREATE_SOURCE);
        String move = Files.readString(MOVE_SOURCE);

        assertTrue(create.contains("CATALOG_ROOT_LOCK_ID"));
        assertTrue(move.contains("newParentId != ROOT_PARENT_ID"));
        assertTrue(move.contains("CatalogPageMovePlanner.plan"));
        assertTrue(move.contains("mutations.applyBatch(requests)"));
        assertFalse(move.contains("SET enabled = IF"));
        assertFalse(move.contains("SET visible = IF"));
    }

    @Test
    void registeredCatalogAdminMutationsNeverWriteDirectlyToLiveTables() throws IOException {
        List<String> mutationHandlers = List.of(
                "CatalogAdminCreateOfferEvent.java",
                "CatalogAdminCreatePageEvent.java",
                "CatalogAdminDeleteOfferEvent.java",
                "CatalogAdminDeletePageEvent.java",
                "CatalogAdminMoveOfferEvent.java",
                "CatalogAdminMovePageEvent.java",
                "CatalogAdminReorderOffersEvent.java",
                "CatalogAdminSaveOfferEvent.java",
                "CatalogAdminSavePageEvent.java",
                "CatalogAdminSavePageIconEvent.java",
                "CatalogAdminSavePageImagesEvent.java",
                "CatalogAdminSetPageEnabledEvent.java",
                "CatalogAdminSetPageVisibleEvent.java");

        for (String handler : mutationHandlers) {
            String source = Files.readString(HANDLER_ROOT.resolve(handler));
            assertTrue(source.contains("CatalogStudioRuntime.services()"), handler);
            assertFalse(source.contains("PreparedStatement"), handler);
            assertFalse(source.contains("CatalogAdminMutationService"), handler);
            assertFalse(source.contains("UPDATE catalog"), handler);
            assertFalse(source.contains("INSERT INTO catalog"), handler);
            assertFalse(source.contains("DELETE FROM catalog"), handler);
        }
    }
}
