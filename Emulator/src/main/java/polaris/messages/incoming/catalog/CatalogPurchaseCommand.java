package polaris.messages.incoming.catalog;

record CatalogPurchaseCommand(int pageId, int itemId, String extraData, int count) {}
