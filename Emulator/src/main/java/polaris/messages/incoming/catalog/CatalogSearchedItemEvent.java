package polaris.messages.incoming.catalog;

import polaris.Emulator;
import polaris.hotel.catalog.CatalogItem;
import polaris.hotel.catalog.CatalogPage;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.catalog.CatalogSearchResultComposer;

public class CatalogSearchedItemEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int offerId = this.packet.readInt();

        int catalogItemId =
                Emulator.getGameEnvironment().getCatalogManager().offerDefs.get(offerId);

        if (catalogItemId != 0) {
            CatalogItem requestedItem =
                    Emulator.getGameEnvironment().getCatalogManager().getCatalogItem(catalogItemId);
            if (requestedItem == null) {
                return;
            }

            CatalogPage page =
                    Emulator.getGameEnvironment().getCatalogManager().getCatalogPage(requestedItem.getPageId());

            if (page != null) {
                for (CatalogItem item : page.getCatalogItems().values()) {
                    if (item.getSearchOfferId() == offerId) {
                        this.client.sendResponse(new CatalogSearchResultComposer(item));
                        return;
                    }
                }
            }
        }
    }
}
