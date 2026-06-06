package com.eu.habbo.messages.outgoing.catalog.catalogadmin;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class CatalogAdminOfferDetailsComposer extends MessageComposer {
    private final int offerId;
    private final int offerIdGroup;
    private final int limitedStack;
    private final int orderNumber;

    public CatalogAdminOfferDetailsComposer(int offerId, int offerIdGroup, int limitedStack, int orderNumber) {
        this.offerId = offerId;
        this.offerIdGroup = offerIdGroup;
        this.limitedStack = limitedStack;
        this.orderNumber = orderNumber;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CatalogAdminOfferDetailsComposer);
        this.response.appendInt(this.offerId);
        this.response.appendInt(this.offerIdGroup);
        this.response.appendInt(this.limitedStack);
        this.response.appendInt(this.orderNumber);
        return this.response;
    }
}
