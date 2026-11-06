package polaris.messages.outgoing.catalog;

import polaris.hotel.catalog.CatalogItem;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class CatalogSearchResultComposer extends MessageComposer {
    private final CatalogItem item;

    public CatalogSearchResultComposer(CatalogItem item) {
        this.item = item;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CatalogSearchResultComposer);
        this.item.serialize(this.response);
        return this.response;
    }

    public CatalogItem getItem() {
        return item;
    }
}
