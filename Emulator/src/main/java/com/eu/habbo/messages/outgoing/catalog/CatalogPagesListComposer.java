package com.eu.habbo.messages.outgoing.catalog;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.CatalogPage;
import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class CatalogPagesListComposer extends MessageComposer {
    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogPagesListComposer.class);

    private static final int MAX_OFFERS = 1000;
    private static final int MAX_CHILDREN = 500;
    private static final int MAX_DEPTH = 20;

    private final Habbo habbo;
    private final String mode;
    private final boolean hasPermission;

    public CatalogPagesListComposer(Habbo habbo, String mode) {
        this.habbo = habbo;
        this.mode = mode;
        this.hasPermission = this.habbo.hasPermission(Permission.ACC_CATALOG_IDS);
    }

    @Override
    protected ServerMessage composeInternal() {
        try {
            CatalogPageType requestedType = CatalogPageType.fromString(this.mode);
            List<CatalogPage> pages = Emulator.getGameEnvironment().getCatalogManager().getCatalogPages(-1, this.habbo, requestedType);

            this.response.init(Outgoing.CatalogPagesListComposer);

            this.response.appendBoolean(true);
            this.response.appendInt(0);
            this.response.appendInt(-1);
            this.response.appendInt(-1);
            this.response.appendString("root");
            this.response.appendString("");
            this.response.appendInt(0);

            int childCount = Math.min(pages.size(), MAX_CHILDREN);
            this.response.appendInt(childCount);

            for (int idx = 0; idx < childCount; idx++) {
                this.append(pages.get(idx), 1, requestedType);
            }

            this.response.appendBoolean(false);
            this.response.appendString(this.mode);

            return this.response;
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }

        return null;
    }

    private void append(CatalogPage category, int depth, CatalogPageType requestedType) {
        List<CatalogPage> pagesList = Emulator.getGameEnvironment().getCatalogManager().getCatalogPages(category.getId(), this.habbo, requestedType);

        this.response.appendBoolean(category.isVisible());
        this.response.appendInt(category.getIconImage());
        this.response.appendInt(category.isEnabled() || this.hasPermission ? category.getId() : -1);
        this.response.appendInt(category.getParentId());
        this.response.appendString(category.getPageName());
        this.response.appendString(category.getCaption() + (this.hasPermission ? " (" + category.getId() + ")" : ""));

        int[] offers = category.getOfferIds().toArray();
        int offerCount = Math.min(offers.length, MAX_OFFERS);
        this.response.appendInt(offerCount);

        for (int idx = 0; idx < offerCount; idx++) {
            this.response.appendInt(offers[idx]);
        }

        if (depth >= MAX_DEPTH) {
            this.response.appendInt(0);
            return;
        }

        int childCount = Math.min(pagesList.size(), MAX_CHILDREN);
        this.response.appendInt(childCount);

        for (int idx = 0; idx < childCount; idx++) {
            this.append(pagesList.get(idx), depth + 1, requestedType);
        }
    }


    public Habbo getHabbo() {
        return habbo;
    }

    public String getMode() {
        return mode;
    }

    public boolean isHasPermission() {
        return hasPermission;
    }
}
