package com.eu.habbo.messages.outgoing.catalog;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.CatalogFeaturedPage;
import com.eu.habbo.habbohotel.catalog.CatalogItem;
import com.eu.habbo.habbohotel.catalog.CatalogPage;
import com.eu.habbo.habbohotel.catalog.layouts.FrontPageFeaturedLayout;
import com.eu.habbo.habbohotel.catalog.layouts.FrontpageLayout;
import com.eu.habbo.habbohotel.catalog.layouts.RecentPurchasesLayout;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class CatalogPageComposer extends MessageComposer {
    private final CatalogPage page;
    private final Habbo habbo;
    private final int offerId;
    private final String mode;

    public CatalogPageComposer(CatalogPage page, Habbo habbo, int offerId, String mode) {
        this.page = page;
        this.habbo = habbo;
        this.offerId = offerId;
        this.mode = mode;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CatalogPageComposer);
        this.response.appendInt(this.page.getId());
        this.response.appendString(this.mode);
        this.page.serialize(this.response);

        if (this.page instanceof RecentPurchasesLayout) {
            this.response.appendInt(this.habbo.getHabboStats().getRecentPurchases().size());

            for (Map.Entry<Integer, CatalogItem> item : this.habbo.getHabboStats().getRecentPurchases().entrySet()) {
                item.getValue().serialize(this.response);
            }
        } else {
            this.response.appendInt(this.page.getCatalogItems().size());
            List<CatalogItem> items = new ArrayList<>(this.page.getCatalogItems().values());
            Collections.sort(items);
            for (CatalogItem item : items) {
                item.serialize(this.response);
            }
        }
        this.response.appendInt(this.offerId);
        this.response.appendBoolean(false); //acceptSeasonCurrencyAsCredits

        if (this.page instanceof FrontPageFeaturedLayout || this.page instanceof FrontpageLayout) {
            this.serializeExtra(this.response);
        }

        return this.response;
    }

    public void serializeExtra(ServerMessage message) {
        message.appendInt(Emulator.getGameEnvironment().getCatalogManager().getCatalogFeaturedPages().size());

        for (CatalogFeaturedPage page : Emulator.getGameEnvironment().getCatalogManager().getCatalogFeaturedPages().values()) {
            page.serialize(message);
        }
    }

    public CatalogPage getPage() {
        return page;
    }

    public Habbo getHabbo() {
        return habbo;
    }

    public int getOfferId() {
        return offerId;
    }

    public String getMode() {
        return mode;
    }
}
