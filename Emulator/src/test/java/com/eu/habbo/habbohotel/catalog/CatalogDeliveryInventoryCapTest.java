package com.eu.habbo.habbohotel.catalog;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInventory;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Vouchers, targeted offers and the limited raffle delivered past the inventory size the catalog enforces. */
class CatalogDeliveryInventoryCapTest {

    @Test
    void anInventoryOverItsSizeIsRefused() {
        Habbo habbo = mock(Habbo.class, RETURNS_DEEP_STUBS);
        when(habbo.getInventory().getItemsComponent().itemCount()).thenReturn(HabboInventory.MAXIMUM_ITEMS + 1);

        assertTrue(CatalogManager.inventoryOverCap(habbo));
    }

    @Test
    void anInventoryWithinItsSizeIsAccepted() {
        Habbo habbo = mock(Habbo.class, RETURNS_DEEP_STUBS);
        when(habbo.getInventory().getItemsComponent().itemCount()).thenReturn(HabboInventory.MAXIMUM_ITEMS);

        assertFalse(CatalogManager.inventoryOverCap(habbo));
    }

    @Test
    void everyDeliveryChecksTheSize() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/catalog/CatalogManager.java"));
        int refusal = source.indexOf("private boolean refusedBeforePricing(");

        assertTrue(source.indexOf("if (inventoryOverCap(habbo)) {", refusal) > refusal);
    }
}
