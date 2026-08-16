package com.eu.habbo.messages.outgoing.catalog;

public record CatalogProductMetadataEntry(
        int offerId, int itemBaseId, int productClassId, boolean tradeable, boolean recyclable) {

    public int flags() {
        int flags = 0;

        if (this.tradeable) flags |= 1;
        if (this.recyclable) flags |= 2;

        return flags;
    }
}
