package com.eu.habbo.messages.incoming.catalog.catalogadmin;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogAdminPageMutationContractTest {
    private static final Path CREATE_SOURCE = Path.of(
            "src/main/java/com/eu/habbo/messages/incoming/catalog/catalogadmin/CatalogAdminCreatePageEvent.java");
    private static final Path SAVE_SOURCE = Path.of(
            "src/main/java/com/eu/habbo/messages/incoming/catalog/catalogadmin/CatalogAdminSavePageEvent.java");
    private static final Path MOVE_SOURCE = Path.of(
            "src/main/java/com/eu/habbo/messages/incoming/catalog/catalogadmin/CatalogAdminMovePageEvent.java");
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
    void movePageValidatesTargetBeforeTogglingVisibilityOrEnabledState() throws IOException {
        String move = Files.readString(MOVE_SOURCE);

        int pageLookup = move.indexOf("getCatalogPage(pageId, pageType)");
        int enabledToggle = move.indexOf("SET enabled = IF");
        int visibleToggle = move.indexOf("SET visible = IF");

        assertTrue(pageLookup >= 0, "move page should load the page before mutating it");
        assertTrue(pageLookup < enabledToggle, "enabled toggle must not run before page existence is checked");
        assertTrue(pageLookup < visibleToggle, "visible toggle must not run before page existence is checked");
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
