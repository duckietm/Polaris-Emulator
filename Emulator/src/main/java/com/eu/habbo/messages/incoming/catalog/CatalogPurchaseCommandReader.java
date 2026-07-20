package com.eu.habbo.messages.incoming.catalog;

import com.eu.habbo.habbohotel.catalog.CatalogPurchaseCommand;
import com.eu.habbo.messages.ClientMessage;

final class CatalogPurchaseCommandReader {

    private CatalogPurchaseCommandReader() {}

    static CatalogPurchaseCommand readFrom(ClientMessage packet) {
        int pageId = packet.readInt();
        int itemId = packet.readInt();
        String extraData = packet.readString();
        int requestedCount = packet.readInt();
        int count = Math.clamp(requestedCount, 1, 100);
        return new CatalogPurchaseCommand(pageId, itemId, extraData, count);
    }
}
