package com.eu.habbo.messages.outgoing.catalog.marketplace;

import com.eu.habbo.habbohotel.catalog.marketplace.MarketPlace;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/** MarketplaceItemStats (725): average price, offer count and the 30-day history of one furni. */
public class MarketplaceItemInfoComposer extends MessageComposer {
    /** The floor-item category, what the composer echoed before the request's category was read. */
    public static final int CATEGORY_FLOOR = 1;

    private final int category;
    private final int itemId;

    public MarketplaceItemInfoComposer(int itemId) {
        this(CATEGORY_FLOOR, itemId);
    }

    public MarketplaceItemInfoComposer(int category, int itemId) {
        this.category = category;
        this.itemId = itemId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.MarketplaceItemInfoComposer);
        MarketPlace.serializeItemInfo(this.category, this.itemId, this.response);
        return this.response;
    }

    public int getCategory() {
        return category;
    }

    public int getItemId() {
        return itemId;
    }
}
