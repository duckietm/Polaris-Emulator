package com.eu.habbo.habbohotel.catalog;

public record CatalogPurchaseCommand(int pageId, int itemId, String extraData, int count) {}
