package com.eu.habbo.messages.incoming.catalog.catalogadmin;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.CatalogAdminCacheSync;
import com.eu.habbo.habbohotel.catalog.CatalogPage;
import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.CatalogAdminResultComposer;

import java.sql.Connection;
import java.sql.PreparedStatement;

public class CatalogAdminDeletePageEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {
        if (!this.client.getHabbo().hasPermission(Permission.ACC_CATALOGFURNI)) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "No permission"));
            return;
        }

        int pageId = this.packet.readInt();
        CatalogPageType pageType = CatalogPageType.fromString(this.packet.readString());

        CatalogPage page = Emulator.getGameEnvironment().getCatalogManager().getCatalogPage(pageId, pageType);

        if (page == null) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "Page not found: " + pageId));
            return;
        }

        if (!page.getChildPages().isEmpty()) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "Move or delete child pages first"));
            return;
        }

        if (!page.getCatalogItems().isEmpty()) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "Move or delete offers on this page first"));
            return;
        }

        String pageTable = pageType == CatalogPageType.BUILDER ? "catalog_pages_bc" : "catalog_pages";
        String itemTable = pageType == CatalogPageType.BUILDER ? "catalog_items_bc" : "catalog_items";

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            try (PreparedStatement children = connection.prepareStatement(
                    "SELECT 1 FROM " + pageTable + " WHERE parent_id = ? LIMIT 1")) {
                children.setInt(1, pageId);
                try (var set = children.executeQuery()) {
                    if (set.next()) {
                        this.client.sendResponse(new CatalogAdminResultComposer(false, "Move or delete child pages first"));
                        return;
                    }
                }
            }

            try (PreparedStatement offers = connection.prepareStatement(
                    "SELECT 1 FROM " + itemTable + " WHERE page_id = ? LIMIT 1")) {
                offers.setInt(1, pageId);
                try (var set = offers.executeQuery()) {
                    if (set.next()) {
                        this.client.sendResponse(new CatalogAdminResultComposer(false, "Move or delete offers on this page first"));
                        return;
                    }
                }
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM " + pageTable + " WHERE id = ?")) {
                statement.setInt(1, pageId);
                if (statement.executeUpdate() == 0) {
                    this.client.sendResponse(new CatalogAdminResultComposer(false, "Page not found: " + pageId));
                    return;
                }
            }
        }

        CatalogAdminCacheSync.detachDeletedPage(page, pageType);
        this.client.sendResponse(new CatalogAdminResultComposer(true, "Page deleted"));
    }
}
