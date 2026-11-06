package polaris.messages.incoming.catalog;

import polaris.hotel.catalog.CatalogNewAdditions;
import polaris.messages.incoming.MessageHandler;

/**
 * Official {@code MarkCatalogNewAdditionsPageOpenedMessageComposer} (no payload): AIR {@code HabboCatalog} sends it
 * when the catalogue opens while the index flagged new additions. The player has now seen what the new_additions
 * page offers, so the next index no longer flags it.
 */
public class MarkCatalogNewAdditionsPageOpenedEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getUser() == null) return;

        CatalogNewAdditions.get().markOpened(this.client.getUser());
    }
}
