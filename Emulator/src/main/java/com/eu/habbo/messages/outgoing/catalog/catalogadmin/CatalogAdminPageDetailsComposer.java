package com.eu.habbo.messages.outgoing.catalog.catalogadmin;

import com.eu.habbo.habbohotel.catalog.CatalogPage;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class CatalogAdminPageDetailsComposer extends MessageComposer {
    private final CatalogPage page;

    public CatalogAdminPageDetailsComposer(CatalogPage page) {
        this.page = page;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CatalogAdminPageDetailsComposer);
        this.response.appendInt(this.page.getId());
        this.response.appendString(this.page.getCaption());
        this.response.appendString(this.page.getPageName());
        this.response.appendInt(this.page.getRank());
        this.response.appendInt(this.page.getOrderNum());
        this.response.appendBoolean(this.page.isVisible());
        this.response.appendBoolean(this.page.isEnabled());
        return this.response;
    }
}
