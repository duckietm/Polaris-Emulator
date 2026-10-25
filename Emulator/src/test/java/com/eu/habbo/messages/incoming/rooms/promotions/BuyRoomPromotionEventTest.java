package com.eu.habbo.messages.incoming.rooms.promotions;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.catalog.CatalogItem;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

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
        Habbo habbo = mock(Habbo.class);
        when(habbo.tryTakeCredits(anyInt())).thenReturn(false);

        assertFalse(BuyRoomPromotionEvent.charge(habbo, item(20, 0)));
        verify(habbo, never()).giveCredits(anyInt());
    }

    @Test
    void theCreditsComeBackWhenThePointsCannotBeTaken() {
        Habbo habbo = mock(Habbo.class);
        when(habbo.tryTakeCredits(20)).thenReturn(true);
        when(habbo.tryTakePoints(5, 10)).thenReturn(false);

        assertFalse(BuyRoomPromotionEvent.charge(habbo, item(20, 10)));
        verify(habbo).giveCredits(20);
    }

    @Test
    void infiniteCurrenciesAndFreeItemsTakeNothing() {
        Habbo habbo = mock(Habbo.class);
        when(habbo.hasPermission(Permission.ACC_INFINITE_CREDITS)).thenReturn(true);

        assertTrue(BuyRoomPromotionEvent.charge(habbo, item(20, 0)));
        verify(habbo, never()).tryTakeCredits(anyInt());
    }

    @Test
    void bothHandlersFilterTheTextAndThePurchaseChecksThePage() throws Exception {
        String buy = Files.readString(
                Path.of("src/main/java/com/eu/habbo/messages/incoming/rooms/promotions/BuyRoomPromotionEvent.java"));
        String update = Files.readString(
                Path.of("src/main/java/com/eu/habbo/messages/incoming/rooms/promotions/UpdateRoomPromotionEvent.java"));

        assertTrue(buy.contains("getWordFilter().filter(title, habbo)"));
        assertTrue(buy.contains("getWordFilter().filter(description, habbo)"));
        assertTrue(buy.contains("!mayUse(page, habbo)"));
        assertTrue(buy.indexOf("charge(habbo, item)") < buy.indexOf("room.createPromotion("));
        assertFalse(buy.contains("giveCredits(-item"));
        assertTrue(update.contains("getWordFilter().filter(promotionName"));
        assertTrue(update.contains("getWordFilter().filter(promotionDescription"));
    }
}
