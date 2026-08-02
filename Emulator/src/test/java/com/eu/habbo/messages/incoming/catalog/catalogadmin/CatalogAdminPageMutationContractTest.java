package com.eu.habbo.messages.incoming.catalog.catalogadmin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CatalogAdminPageMutationContractTest {
    private static final Path CREATE_SOURCE = Path.of(
            "src/main/java/com/eu/habbo/messages/incoming/catalog/catalogadmin/CatalogAdminCreatePageEvent.java");
    private static final Path SAVE_SOURCE =
            Path.of("src/main/java/com/eu/habbo/messages/incoming/catalog/catalogadmin/CatalogAdminSavePageEvent.java");
    private static final Path MOVE_SOURCE =
            Path.of("src/main/java/com/eu/habbo/messages/incoming/catalog/catalogadmin/CatalogAdminMovePageEvent.java");
    private static final Path DELETE_SOURCE = Path.of(
            "src/main/java/com/eu/habbo/messages/incoming/catalog/catalogadmin/CatalogAdminDeletePageEvent.java");

    @Test
    void pageParentChecksStayWithinTheSameCatalogPageType() throws IOException {
        String create = Files.readString(CREATE_SOURCE);
        String save = Files.readString(SAVE_SOURCE);
        String move = Files.readString(MOVE_SOURCE);

        assertTrue(create.contains("getCatalogPage(parentId, pageType)"));
        assertTrue(save.contains("getCatalogPage(parentId, pageType)"));
        assertTrue(save.contains("getCatalogPage(current, pageType)"));
        assertTrue(move.contains("getCatalogPage(newParentId, pageType)"));
        assertTrue(move.contains("getCatalogPage(current, pageType)"));
    }

    @Test
    void movePageDoesNotOverloadRootParentIdsAsPageStateToggles() throws IOException {
        String move = Files.readString(MOVE_SOURCE);

        int pageLookup = move.indexOf("getCatalogPage(pageId, pageType)");

        assertTrue(pageLookup >= 0, "move page should load the page before mutating it");
        assertFalse(move.contains("SET enabled = IF"), "enabled changes must use their dedicated packet");
        assertFalse(move.contains("SET visible = IF"), "visibility changes must use their dedicated packet");
        assertTrue(move.contains("newParentId != ROOT_PARENT_ID"), "root must remain a valid page parent");
    }

    @Test
    void pageMutationsReportMissingRowsInsteadOfAlwaysSucceeding() throws IOException {
        String save = Files.readString(SAVE_SOURCE);
        String move = Files.readString(MOVE_SOURCE);
        String delete = Files.readString(DELETE_SOURCE);

        assertTrue(save.contains("statement.executeUpdate() == 0"));
        assertTrue(move.contains("statement.executeUpdate() == 0"));
        assertTrue(delete.contains("statement.executeUpdate() == 0"));
    }
}
