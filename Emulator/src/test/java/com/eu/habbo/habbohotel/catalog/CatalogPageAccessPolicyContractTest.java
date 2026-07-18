package com.eu.habbo.habbohotel.catalog;

import com.eu.habbo.messages.incoming.catalog.CatalogBuyItemAsGiftEvent;
import com.eu.habbo.messages.incoming.catalog.CatalogBuyItemEvent;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogPageAccessPolicyContractTest {
    @Test
    void purchaseHandlersApplyTheSharedPageAccessPolicy() throws Exception {
        assertUsesPolicy(CatalogBuyItemEvent.class);
        assertUsesPolicy(CatalogBuyItemAsGiftEvent.class);
    }

    private static void assertUsesPolicy(Class<?> handler) throws Exception {
        Path source = Path.of("src/main/java", handler.getName().replace('.', '/') + ".java");
        String code = Files.readString(source);

        assertTrue(code.contains("CatalogPageAccessPolicy.canAccess("),
                () -> handler.getSimpleName() + " must reject disabled, rank-restricted, and club-only pages");
    }
}
