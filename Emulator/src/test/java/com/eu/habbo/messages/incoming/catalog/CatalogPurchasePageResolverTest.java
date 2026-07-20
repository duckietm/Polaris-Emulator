package com.eu.habbo.messages.incoming.catalog;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.catalog.CatalogItem;
import com.eu.habbo.habbohotel.catalog.CatalogPage;
import com.eu.habbo.habbohotel.catalog.CatalogPageLayouts;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class CatalogPurchasePageResolverTest {

    @Test
    void unknownSearchItemDoesNotEvaluatePageAccess() {
        AtomicBoolean accessEvaluated = new AtomicBoolean();
        CatalogPurchasePageResolver resolver = resolver(null, null);

        CatalogPage resolved = resolver.resolve(new CatalogPurchaseCommand(-1, 404, "", 1), page -> {
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
        CatalogPurchasePageResolver.CatalogLookup lookup = mock(CatalogPurchasePageResolver.CatalogLookup.class);
        when(lookup.findItem(72)).thenReturn(item);
        when(lookup.findPage(19)).thenReturn(page);
        CatalogPurchasePageResolver resolver = new CatalogPurchasePageResolver(lookup);

        CatalogPage resolved =
                resolver.resolve(new CatalogPurchaseCommand(-1, 72, "", 1), candidate -> candidate == page);

        assertSame(page, resolved);
        verify(lookup).findPage(19);
    }

    @Test
    void directPageUsesThePageIdWithoutAnItemLookup() {
        CatalogPage page = mock(CatalogPage.class);
        CatalogPurchasePageResolver.CatalogLookup lookup = mock(CatalogPurchasePageResolver.CatalogLookup.class);
        when(lookup.findPage(11)).thenReturn(page);
        CatalogPurchasePageResolver resolver = new CatalogPurchasePageResolver(lookup);

        assertSame(page, resolver.resolve(new CatalogPurchaseCommand(11, 72, "", 1), candidate -> true));
    }

    @Test
    void clubGiftPageIsNotPurchasableThroughThisCommand() {
        CatalogPage page = mock(CatalogPage.class);
        when(page.getLayout()).thenReturn(CatalogPageLayouts.club_gift.name());
        AtomicBoolean accessEvaluated = new AtomicBoolean();
        CatalogPurchasePageResolver resolver = resolver(null, page);

        assertNull(resolver.resolve(new CatalogPurchaseCommand(11, 72, "", 1), candidate -> {
            accessEvaluated.set(true);
            return true;
        }));
        assertFalse(accessEvaluated.get());
    }

    private static CatalogPurchasePageResolver resolver(CatalogItem item, CatalogPage page) {
        return new CatalogPurchasePageResolver(new CatalogPurchasePageResolver.CatalogLookup() {
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
