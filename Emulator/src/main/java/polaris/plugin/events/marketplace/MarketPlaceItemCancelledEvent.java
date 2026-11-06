package polaris.plugin.events.marketplace;

import polaris.hotel.catalog.marketplace.MarketPlaceOffer;

public class MarketPlaceItemCancelledEvent extends MarketPlaceEvent {
    public final MarketPlaceOffer offer;

    public MarketPlaceItemCancelledEvent(MarketPlaceOffer offer) {
        this.offer = offer;
    }
}
