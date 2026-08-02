package com.eu.habbo.messages.outgoing.catalog.catalogadmin;

import com.eu.habbo.habbohotel.catalog.CatalogPage;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogPageSnapshot;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class CatalogAdminPageDetailsComposer extends MessageComposer {
    private final CatalogPage page;
    private final CatalogPageSnapshot snapshot;

    public CatalogAdminPageDetailsComposer(CatalogPage page) {
        this.page = page;
        this.snapshot = null;
    }

    public CatalogAdminPageDetailsComposer(CatalogPageSnapshot snapshot) {
        this.page = null;
        this.snapshot = snapshot;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CatalogAdminPageDetailsComposer);
        if (this.snapshot != null) {
            this.response.appendInt(this.snapshot.pageId());
            this.response.appendString(this.snapshot.caption());
            this.response.appendString(this.snapshot.captionSave());
            this.response.appendInt(this.snapshot.parentId());
            this.response.appendString(this.snapshot.catalogMode());
            this.response.appendString(this.snapshot.pageLayout());
            this.response.appendInt(this.snapshot.iconColor());
            this.response.appendInt(this.snapshot.iconImage());
            this.response.appendInt(this.snapshot.minRank());
            this.response.appendInt(this.snapshot.orderNum());
            this.response.appendBoolean(this.snapshot.visible());
            this.response.appendBoolean(this.snapshot.enabled());
            this.response.appendBoolean(this.snapshot.clubOnly());
            this.response.appendBoolean(this.snapshot.vipOnly());
            this.response.appendString(this.snapshot.pageHeadline());
            this.response.appendString(this.snapshot.pageTeaser());
            this.response.appendString(this.snapshot.pageSpecial());
            this.response.appendString(this.snapshot.pageText1());
            this.response.appendString(this.snapshot.pageText2());
            this.response.appendString(this.snapshot.pageTextDetails());
            this.response.appendString(this.snapshot.pageTextTeaser());
            this.response.appendInt(this.snapshot.roomId());
            this.response.appendString(this.snapshot.includes());
            return this.response;
        }
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
