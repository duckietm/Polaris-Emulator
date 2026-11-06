package polaris.hotel.catalog;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInventory;

/** Vouchers, targeted offers and the limited raffle delivered past the inventory size the catalog enforces. */
class CatalogDeliveryInventoryCapTest {

    @Test
    void anInventoryOverItsSizeIsRefused() {
        User user = mock(User.class, RETURNS_DEEP_STUBS);
        when(user.getInventory().getItemsComponent().itemCount()).thenReturn(UserInventory.MAXIMUM_ITEMS + 1);

        assertTrue(CatalogManager.inventoryOverCap(user));
    }

    @Test
    void anInventoryWithinItsSizeIsAccepted() {
        User user = mock(User.class, RETURNS_DEEP_STUBS);
        when(user.getInventory().getItemsComponent().itemCount()).thenReturn(UserInventory.MAXIMUM_ITEMS);

        assertFalse(CatalogManager.inventoryOverCap(user));
    }

    @Test
    void everyDeliveryChecksTheSize() throws Exception {
        String source = Files.readString(Path.of("src/main/java/polaris/hotel/catalog/CatalogManager.java"));
        int refusal = source.indexOf("private boolean refusedBeforePricing(");

        assertTrue(source.indexOf("if (inventoryOverCap(user)) {", refusal) > refusal);
    }
}
