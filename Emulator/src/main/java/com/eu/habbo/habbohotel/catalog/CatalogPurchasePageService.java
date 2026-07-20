package com.eu.habbo.habbohotel.catalog;

import java.util.Objects;
import java.util.function.Predicate;

public final class CatalogPurchasePageService {

    interface CatalogLookup {
        CatalogItem findItem(int itemId);

        CatalogPage findPage(int pageId);
    }

    private final CatalogLookup catalog;

    public CatalogPurchasePageService(CatalogManager catalogManager) {
        Objects.requireNonNull(catalogManager);
        this.catalog = new CatalogLookup() {
            @Override
            public CatalogItem findItem(int itemId) {
                return catalogManager.getCatalogItem(itemId);
            }

            @Override
            public CatalogPage findPage(int pageId) {
                return catalogManager.getCatalogPage(pageId);
            }
        };
    }

    CatalogPurchasePageService(CatalogLookup catalog) {
        this.catalog = Objects.requireNonNull(catalog);
    }

    public CatalogPage resolve(CatalogPurchaseCommand command, Predicate<CatalogPage> canAccess) {
        CatalogPage page;
        if (command.pageId() == -12345678 || command.pageId() == -1) {
            CatalogItem searchedItem = this.catalog.findItem(command.itemId());
            if (searchedItem == null || searchedItem.getOfferId() <= 0) {
                return null;
            }
            page = this.catalog.findPage(searchedItem.getPageId());
        } else {
            page = this.catalog.findPage(command.pageId());
        }

        if (page == null
                || page.getLayout() != null && page.getLayout().equalsIgnoreCase(CatalogPageLayouts.club_gift.name())) {
            return null;
        }
        return canAccess.test(page) ? page : null;
    }
}
