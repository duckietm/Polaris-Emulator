package polaris.messages.outgoing.catalog;

import polaris.hotel.catalog.CatalogItem;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class SeasonalCalendarDailyOfferComposer extends MessageComposer {
    private final int pageId;
    private final CatalogItem catalogItem;

    public SeasonalCalendarDailyOfferComposer(int pageId, CatalogItem catalogItem) {
        this.pageId = pageId;
        this.catalogItem = catalogItem;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.SeasonalCalendarDailyOfferComposer);
        this.response.appendInt(this.pageId);
        this.catalogItem.serialize(this.response);
        return this.response;
    }

    public int getPageId() {
        return pageId;
    }

    public CatalogItem getCatalogItem() {
        return catalogItem;
    }
}
