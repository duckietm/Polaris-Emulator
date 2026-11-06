package polaris.messages.outgoing.hotelview;

import polaris.hotel.catalog.CatalogPage;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class HotelViewExpiringCatalogPageCommposer extends MessageComposer {
    private final CatalogPage page;
    private final String image;

    public HotelViewExpiringCatalogPageCommposer(CatalogPage page, String image) {
        this.page = page;
        this.image = image;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.HotelViewExpiringCatalogPageCommposer);
        this.response.appendString(this.page.getCaption());
        this.response.appendInt(this.page.getId());
        this.response.appendString(this.image);
        return this.response;
    }

    public CatalogPage getPage() {
        return page;
    }

    public String getImage() {
        return image;
    }
}
