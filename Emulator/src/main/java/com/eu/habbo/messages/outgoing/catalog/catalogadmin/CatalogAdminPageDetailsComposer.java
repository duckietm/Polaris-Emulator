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
        this.response.appendInt(this.page.getParentId());
        this.response.appendString(this.page.getCatalogPageType().name());
        this.response.appendString(this.page.getLayout());
        this.response.appendInt(this.page.getIconColor());
        this.response.appendInt(this.page.getIconImage());
        this.response.appendInt(this.page.getRank());
        this.response.appendInt(this.page.getOrderNum());
        this.response.appendBoolean(this.page.isVisible());
        this.response.appendBoolean(this.page.isEnabled());
        this.response.appendBoolean(this.page.isClubOnly());
        this.response.appendBoolean(this.page.isVipOnly());
        this.response.appendString(this.page.getHeaderImage());
        this.response.appendString(this.page.getTeaserImage());
        this.response.appendString(this.page.getSpecialImage());
        this.response.appendString(this.page.getTextOne());
        this.response.appendString(this.page.getTextTwo());
        this.response.appendString(this.page.getTextDetails());
        this.response.appendString(this.page.getTextTeaser());
        this.response.appendInt(this.page.getRoomId());
        this.response.appendString(this.page.getIncluded().stream()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(";")));
        return this.response;
    }
}
