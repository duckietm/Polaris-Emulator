package com.eu.habbo.habbohotel.catalog;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.messages.incoming.catalog.CatalogBuyItemAsGiftEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CatalogPageAccessPolicyContractTest {
    @Test
    void purchaseHandlersApplyTheSharedPageAccessPolicy() throws Exception {
        Path purchaseService = Path.of(
                "src/main/java", "com/eu/habbo/messages/incoming/catalog/CatalogPurchaseApplicationService.java");
        assertTrue(
                Files.readString(purchaseService).contains("CatalogPageAccessPolicy.canAccess("),
                "catalog purchases must reject disabled, rank-restricted, and club-only pages");
        assertUsesPolicy(CatalogBuyItemAsGiftEvent.class);
    }

    private static void assertUsesPolicy(Class<?> handler) throws Exception {
        Path source = Path.of("src/main/java", handler.getName().replace('.', '/') + ".java");
        String code = Files.readString(source);

        assertTrue(
                code.contains("CatalogPageAccessPolicy.canAccess("),
                () -> handler.getSimpleName() + " must reject disabled, rank-restricted, and club-only pages");
    }
}
