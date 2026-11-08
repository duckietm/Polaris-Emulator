package polaris.messages.incoming.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import polaris.hotel.items.Item;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;

class RequestInventoryItemsDeleteContractTest {

    private static String source() throws Exception {
        return Files.readString(
                Path.of("src/main/java/polaris/messages/incoming/inventory/RequestInventoryItemsDelete.java"));
    }

    @Test
    void rejectsInvalidDeleteAmountsBeforeMutatingInventory() throws Exception {
        String source = source();
        int amountRead = source.indexOf("int amount = this.packet.readInt()");
        int amountGuard = source.indexOf("amount <= 0 || amount > MAX_DELETE_AMOUNT", amountRead);
        int firstInventoryLookup = source.indexOf("getItemsComponent().getUserItem", amountRead);

        assertTrue(amountRead > -1, "Delete handler must read the client-provided amount");
        assertTrue(amountGuard > amountRead, "Delete handler must reject non-positive and oversized amounts");
        assertTrue(
                amountGuard < firstInventoryLookup,
                "Amount validation must happen before inventory lookup or mutation work starts");
    }

    @Test
    void doesNotUseAbsoluteValueForClientProvidedAmount() throws Exception {
        String source = source();

        assertFalse(
                source.contains("Math.abs(amount)"),
                "Delete amount must not use Math.abs because Integer.MIN_VALUE remains negative");
    }

    @Test
    void deletesTheChosenInstanceFirst() {
        Item base = mock(Item.class);
        UserItem limited = item(base, true, false);
        UserItem plain = item(base, false, false);
        User user = user(limited, plain);

        assertEquals(List.of(plain), RequestInventoryItemsDelete.selectItemsToDelete(user, plain, 1));
        assertEquals(List.of(limited), RequestInventoryItemsDelete.selectItemsToDelete(user, limited, 1));
    }

    @Test
    void neverAddsAUniqueCopyToAStackDelete() {
        Item base = mock(Item.class);
        UserItem chosen = item(base, false, false);
        UserItem rented = item(base, false, true);
        UserItem limited = item(base, true, false);
        UserItem plain = item(base, false, false);
        User user = user(chosen, rented, limited, plain);

        assertEquals(List.of(chosen, plain), RequestInventoryItemsDelete.selectItemsToDelete(user, chosen, 2));
        assertNull(RequestInventoryItemsDelete.selectItemsToDelete(user, chosen, 3));
        assertNull(RequestInventoryItemsDelete.selectItemsToDelete(user, limited, 2));
    }

    private static UserItem item(Item base, boolean limited, boolean rented) {
        UserItem item = mock(UserItem.class);
        when(item.getBaseItem()).thenReturn(base);
        when(item.isLimited()).thenReturn(limited);
        when(item.hasRentPeriod()).thenReturn(rented);
        return item;
    }

    private static User user(UserItem... items) {
        User user = mock(User.class, RETURNS_DEEP_STUBS);
        when(user.getInventory().getItemsComponent().getItemsAsValueCollection())
                .thenReturn(new LinkedHashSet<>(List.of(items)));
        return user;
    }
}
