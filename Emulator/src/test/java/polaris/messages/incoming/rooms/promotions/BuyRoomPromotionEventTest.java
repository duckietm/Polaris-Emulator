package polaris.messages.incoming.rooms.promotions;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import polaris.hotel.catalog.CatalogItem;
import polaris.hotel.permissions.Permission;
import polaris.hotel.users.User;

/**
 * A room event is shown hotel-wide, yet its text skipped the word filter, any "roomads" page would sell it - hidden,
 * staff-only or club-only - and the price was taken afterwards with giveCredits(-price), which never refuses.
 */
class BuyRoomPromotionEventTest {

    private static CatalogItem item(int credits, int points) {
        CatalogItem item = mock(CatalogItem.class);
        when(item.getCredits()).thenReturn(credits);
        when(item.getPoints()).thenReturn(points);
        when(item.getPointsType()).thenReturn(5);
        return item;
    }

    @Test
    void theEventIsRefusedWhenTheCreditsCannotBeTaken() {
        User user = mock(User.class);
        when(user.tryTakeCredits(anyInt())).thenReturn(false);

        assertFalse(BuyRoomPromotionEvent.charge(user, item(20, 0)));
        verify(user, never()).giveCredits(anyInt());
    }

    @Test
    void theCreditsComeBackWhenThePointsCannotBeTaken() {
        User user = mock(User.class);
        when(user.tryTakeCredits(20)).thenReturn(true);
        when(user.tryTakePoints(5, 10)).thenReturn(false);

        assertFalse(BuyRoomPromotionEvent.charge(user, item(20, 10)));
        verify(user).giveCredits(20);
    }

    @Test
    void infiniteCurrenciesAndFreeItemsTakeNothing() {
        User user = mock(User.class);
        when(user.hasPermission(Permission.ACC_INFINITE_CREDITS)).thenReturn(true);

        assertTrue(BuyRoomPromotionEvent.charge(user, item(20, 0)));
        verify(user, never()).tryTakeCredits(anyInt());
    }

    @Test
    void bothHandlersFilterTheTextAndThePurchaseChecksThePage() throws Exception {
        String buy = Files.readString(
                Path.of("src/main/java/polaris/messages/incoming/rooms/promotions/BuyRoomPromotionEvent.java"));
        String update = Files.readString(
                Path.of("src/main/java/polaris/messages/incoming/rooms/promotions/UpdateRoomPromotionEvent.java"));

        assertTrue(buy.contains("getWordFilter().filter(title, user)"));
        assertTrue(buy.contains("getWordFilter().filter(description, user)"));
        assertTrue(buy.contains("!mayUse(page, user)"));
        assertTrue(buy.indexOf("charge(user, item)") < buy.indexOf("room.createPromotion("));
        assertFalse(buy.contains("giveCredits(-item"));
        assertTrue(update.contains("getWordFilter().filter(promotionName"));
        assertTrue(update.contains("getWordFilter().filter(promotionDescription"));
    }
}
