package com.eu.habbo.messages.incoming.catalog;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogSearchOfferIdContractTest {
    private static String source(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test
    void catalogItemsExposeStableSearchOfferIdWhenDatabaseOfferIdIsMissing() throws Exception {
        String source = source("src/main/java/com/eu/habbo/habbohotel/catalog/CatalogItem.java");

        int method = source.indexOf("public int getSearchOfferId()");
        int rawGuard = source.indexOf("this.offerId > 0", method);
        int fallback = source.indexOf("return haveOffer(this) ? this.id : -1", rawGuard);

        assertTrue(method > -1, "CatalogItem should expose a search-safe offer id");
        assertTrue(rawGuard > method, "CatalogItem should preserve valid positive database offer ids");
        assertTrue(fallback > rawGuard,
                "CatalogItem should fall back to catalog item id when offer_id is missing but the item can be offered");
    }

    @Test
    void catalogManagerIndexesSearchOfferIdsInsteadOfRawOfferIds() throws Exception {
        String source = source("src/main/java/com/eu/habbo/habbohotel/catalog/CatalogManager.java");

        int searchOffer = source.indexOf("int searchOfferId = item.getSearchOfferId()");
        int addOffer = source.indexOf("page.addOfferId(searchOfferId)", searchOffer);
        int offerDefs = source.indexOf("this.offerDefs.put(searchOfferId, item.getId())", addOffer);

        assertTrue(searchOffer > -1, "CatalogManager should calculate the runtime search offer id");
        assertTrue(addOffer > searchOffer, "CatalogManager should expose runtime search offer ids in catalog pages");
        assertTrue(offerDefs > addOffer, "CatalogManager should map runtime search offer ids back to catalog items");
        assertTrue(!source.contains("this.offerDefs.put(item.getOfferId(), item.getId())"),
                "CatalogManager must not index raw -1 offer ids for catalog search");
    }

    @Test
    void catalogSearchLookupResolvesCatalogItemIdsAndComparesSearchOfferIds() throws Exception {
        String source = source("src/main/java/com/eu/habbo/messages/incoming/catalog/CatalogSearchedItemEvent.java");

        assertTrue(source.contains("int catalogItemId ="),
                "Catalog search lookup should name offerDefs values as catalog item ids");
        assertTrue(source.contains("getCatalogItem(catalogItemId)"),
                "Catalog search should resolve the mapped catalog item directly");
        assertTrue(source.contains("item.getSearchOfferId() == offerId"),
                "Catalog search should compare runtime search offer ids, not raw database offer ids");
    }
}
