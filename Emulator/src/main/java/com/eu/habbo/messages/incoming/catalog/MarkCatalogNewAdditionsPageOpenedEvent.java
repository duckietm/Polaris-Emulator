package com.eu.habbo.messages.incoming.catalog;

import com.eu.habbo.habbohotel.catalog.CatalogNewAdditions;
import com.eu.habbo.messages.incoming.MessageHandler;

/**
 * Official {@code MarkCatalogNewAdditionsPageOpenedMessageComposer} (no payload): AIR {@code HabboCatalog} sends it
 * when the catalogue opens while the index flagged new additions. The player has now seen what the new_additions
 * page offers, so the next index no longer flags it.
 */
public class MarkCatalogNewAdditionsPageOpenedEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo() == null) return;

        CatalogNewAdditions.get().markOpened(this.client.getHabbo());
    }
}
