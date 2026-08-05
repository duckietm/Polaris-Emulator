package com.eu.habbo.habbohotel.catalog.versioning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.ItemManager;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class RuntimeCatalogPreviewPresentationResolverTest {
    @Test
    void resolvesRealTileMetadataBundleCountsLimitedStockAndGiftability() {
        ItemManager items = mock(ItemManager.class);
        CatalogOperationalOfferRepository operationalOffers = mock(CatalogOperationalOfferRepository.class);
        Item item = mock(Item.class);
        when(items.getItem(12)).thenReturn(item);
        when(item.getType()).thenReturn(FurnitureType.FLOOR);
        when(item.getSpriteId()).thenReturn(456);
        when(item.getName()).thenReturn("chair");
        when(item.allowGift()).thenReturn(true);
        when(operationalOffers.findLimitedSells(42)).thenReturn(OptionalInt.of(3));

        CatalogPreviewPresentation presentation = new RuntimeCatalogPreviewPresentationResolver(
                        items, operationalOffers)
                .resolve(new CatalogOfferSnapshot(
                        CatalogPageType.NORMAL, 42, "12:2", 17, "chair", 5, 0, 0, 1, 10, 1, -1, 0, "", true, false));

        CatalogPreviewProduct product = presentation.products().getFirst();
        assertEquals(FurnitureType.FLOOR.code, product.productType());
        assertEquals(456, product.productClassId());
        assertEquals(2, product.productCount());
        assertTrue(product.uniqueLimitedItem());
        assertEquals(10, product.uniqueLimitedSeriesSize());
        assertEquals(7, product.uniqueLimitedItemsLeft());
        assertTrue(presentation.giftable());
        verify(operationalOffers).findLimitedSells(42);
    }
}
