package com.eu.habbo.messages.outgoing.catalog;

import com.eu.habbo.habbohotel.catalog.CatalogItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class PurchaseOKComposer extends MessageComposer {
    private final CatalogItem catalogItem;

    public PurchaseOKComposer(CatalogItem catalogItem) {
        this.catalogItem = catalogItem;
    }

    public PurchaseOKComposer() {
        this.catalogItem = null;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.PurchaseOKComposer);
        if (this.catalogItem != null) {
            this.catalogItem.serialize(this.response);
        } else {
            this.response.appendInt(0);
            this.response.appendString("");
            this.response.appendBoolean(false);
            this.response.appendInt(0);
            this.response.appendInt(0);
            this.response.appendInt(0);
            this.response.appendBoolean(true);
            // One empty "s" product, then the club level and the bundle flag, in the order the
            // offer is read (the limited flag is a bool, the club level an int).
            this.response.appendInt(1);
            this.response.appendString("s");
            this.response.appendInt(0);
            this.response.appendString("");
            this.response.appendInt(1);
            this.response.appendBoolean(false);
            this.response.appendInt(0);
            this.response.appendBoolean(false);
        }
        return this.response;
    }

    public CatalogItem getCatalogItem() {
        return catalogItem;
    }
}
