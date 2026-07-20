package com.eu.habbo.habbohotel.catalog;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class CatalogPurchasePageServiceTest {

    @Test
    void unknownSearchItemDoesNotEvaluatePageAccess() {
        AtomicBoolean accessEvaluated = new AtomicBoolean();
        CatalogPurchasePageService service = service(null, null);

        CatalogPage resolved = service.resolve(new CatalogPurchaseCommand(-1, 404, "", 1), page -> {
            accessEvaluated.set(true);
            return true;
        });

        assertNull(resolved);
        assertFalse(accessEvaluated.get());
    }

    @Test
    void searchResultResolvesItsOwningPageAndAppliesAccessPolicy() {
        CatalogItem item = mock(CatalogItem.class);
        CatalogPage page = mock(CatalogPage.class);
        when(item.getOfferId()).thenReturn(72);
        when(item.getPageId()).thenReturn(19);
        CatalogPurchasePageService.CatalogLookup lookup = mock(CatalogPurchasePageService.CatalogLookup.class);
        when(lookup.findItem(72)).thenReturn(item);
        when(lookup.findPage(19)).thenReturn(page);
        CatalogPurchasePageService service = new CatalogPurchasePageService(lookup);

        CatalogPage resolved =
                service.resolve(new CatalogPurchaseCommand(-1, 72, "", 1), candidate -> candidate == page);

        assertSame(page, resolved);
        verify(lookup).findPage(19);
    }

    @Test
    void directPageUsesThePageIdWithoutAnItemLookup() {
        CatalogPage page = mock(CatalogPage.class);
        CatalogPurchasePageService.CatalogLookup lookup = mock(CatalogPurchasePageService.CatalogLookup.class);
        when(lookup.findPage(11)).thenReturn(page);
        CatalogPurchasePageService service = new CatalogPurchasePageService(lookup);

        assertSame(page, service.resolve(new CatalogPurchaseCommand(11, 72, "", 1), candidate -> true));
    }

    @Test
    void clubGiftPageIsNotPurchasableThroughThisCommand() {
        CatalogPage page = mock(CatalogPage.class);
        when(page.getLayout()).thenReturn(CatalogPageLayouts.club_gift.name());
        AtomicBoolean accessEvaluated = new AtomicBoolean();
        CatalogPurchasePageService service = service(null, page);

        assertNull(service.resolve(new CatalogPurchaseCommand(11, 72, "", 1), candidate -> {
            accessEvaluated.set(true);
            return true;
        }));
        assertFalse(accessEvaluated.get());
    }

    private static CatalogPurchasePageService service(CatalogItem item, CatalogPage page) {
        return new CatalogPurchasePageService(new CatalogPurchasePageService.CatalogLookup() {
            @Override
            public CatalogItem findItem(int itemId) {
                return item;
            }

            @Override
            public CatalogPage findPage(int pageId) {
                return page;
            }
        });
    }
}
